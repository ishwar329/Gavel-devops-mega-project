import asyncio
import json
import logging
from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI, Query, Request, HTTPException
from fastapi.responses import JSONResponse

from auth import get_bearer_token, get_current_user
from chatbot import Chatbot
from config import AUCTION_SERVICE_URL, KAFKA_BROKERS, PORT, SHOP_SERVICE_URL
from llm import LLMClient
from recommender import Recommender
from vector_store import VectorStore

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

llm = LLMClient()
vector_store = VectorStore()
recommender = Recommender()
chatbot = Chatbot(llm, vector_store)


def _build_auction_text(a: dict) -> str:
    parts = []
    title = a.get("item_title", "Unknown item")
    parts.append(f"Auction: {title}")
    if a.get("shop_name"):
        parts.append(f"Shop: {a['shop_name']}")
    if a.get("description"):
        parts.append(f"Description: {a['description']}")
    if a.get("category"):
        parts.append(f"Category: {a['category']}")
    if a.get("retail_price"):
        parts.append(f"Retail price: ${a['retail_price'] / 100:.2f}")
    if a.get("current_highest_bid"):
        parts.append(f"Current bid: ${a['current_highest_bid'] / 100:.2f}")
    if a.get("bid_count"):
        parts.append(f"Bids: {a['bid_count']}")
    return ". ".join(parts)


async def _refresh_loop() -> None:
    http = httpx.AsyncClient(timeout=10.0)
    while True:
        try:
            await recommender.refresh_auctions()

            auctions = recommender._auction_cache
            vector_store.clear()
            for a in auctions:
                vector_store.add(a["auction_id"], _build_auction_text(a), metadata=a)

            shop_ids: set[str] = set()
            for a in auctions:
                sid = a.get("shop_id")
                if sid:
                    shop_ids.add(sid)

            for shop_id in list(shop_ids)[:20]:
                try:
                    resp = await http.get(f"{SHOP_SERVICE_URL}/shops/{shop_id}/reviews")
                    if resp.status_code == 200:
                        data = resp.json()
                        shop_name = "shop"
                        for a in auctions:
                            if a.get("shop_id") == shop_id:
                                shop_name = a.get("shop_name", "shop")
                                break
                        for review in data.get("reviews") or []:
                            comment = review.get("comment", "")
                            if comment:
                                text = f"Review for {shop_name}: {comment} (Rating: {review.get('rating', 0)}/5)"
                                vector_store.add(
                                    f"review-{review['review_id']}", text, metadata=review
                                )
                except Exception:
                    pass

            logger.info(
                "Refreshed %d auctions, %d documents in vector store",
                len(auctions),
                vector_store.count,
            )
        except Exception as e:
            logger.warning("Refresh failed: %s", e)

        await asyncio.sleep(30)


def _build_item_text(event: dict) -> str:
    parts = [f"Item: {event.get('title', 'Unknown')}"]
    if event.get("description"):
        parts.append(f"Description: {event['description']}")
    if event.get("category"):
        parts.append(f"Category: {event['category']}")
    if event.get("retail_value"):
        parts.append(f"Retail value: ${event['retail_value'] / 100:.2f}")
    return ". ".join(parts)


def _build_review_text(event: dict) -> str:
    shop = event.get("shop_name", "a shop")
    comment = event.get("comment", "")
    rating = event.get("rating", 0)
    return f"Review for {shop}: {comment} (Rating: {rating}/5)"


async def _kafka_consumer_loop() -> None:
    try:
        from aiokafka import AIOKafkaConsumer

        consumer = AIOKafkaConsumer(
            "bid.placed",
            "auction.closed",
            "item.created",
            "review.created",
            bootstrap_servers=KAFKA_BROKERS,
            group_id="ai-service",
            auto_offset_reset="latest",
            value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        )
        await consumer.start()
        logger.info("Kafka consumer started")
        try:
            async for msg in consumer:
                try:
                    event = msg.value
                    if msg.topic == "bid.placed":
                        auction_id = event.get("auction_id", "")
                        category = recommender.auction_categories.get(auction_id)
                        recommender.record_bid(
                            event.get("user_id", ""), auction_id, category
                        )
                    elif msg.topic == "item.created":
                        item_id = event.get("item_id", "")
                        text = _build_item_text(event)
                        vector_store.add(f"item-{item_id}", text, metadata={
                            "type": "item",
                            "item_id": item_id,
                            "shop_id": event.get("shop_id", ""),
                            "title": event.get("title", ""),
                            "category": event.get("category", ""),
                        })
                        logger.info("Indexed item %s into vector store", item_id)
                    elif msg.topic == "review.created":
                        review_id = event.get("review_id", "")
                        text = _build_review_text(event)
                        vector_store.add(f"review-{review_id}", text, metadata={
                            "type": "review",
                            "review_id": review_id,
                            "shop_id": event.get("shop_id", ""),
                            "rating": event.get("rating", 0),
                        })
                        logger.info("Indexed review %s into vector store", review_id)
                except Exception as e:
                    logger.warning("Error processing Kafka message: %s", e)
        finally:
            await consumer.stop()
    except Exception as e:
        logger.warning("Kafka consumer unavailable (using API fallback): %s", e)


@asynccontextmanager
async def lifespan(_app: FastAPI):
    refresh_task = asyncio.create_task(_refresh_loop())
    kafka_task = asyncio.create_task(_kafka_consumer_loop())
    yield
    refresh_task.cancel()
    kafka_task.cancel()
    await llm.close()
    await recommender.close()


app = FastAPI(lifespan=lifespan)


@app.get("/ai/recommendations")
async def get_recommendations(
    request: Request,
    lat: float | None = Query(None),
    lng: float | None = Query(None),
):
    user = get_current_user(request)
    if user["role"] != "buyer":
        raise HTTPException(status_code=403, detail="buyers only")

    token = get_bearer_token(request)
    recs = await recommender.recommend(user["user_id"], lat=lat, lng=lng, token=token)
    return {"recommendations": recs}


@app.post("/ai/chat")
async def post_chat(request: Request):
    user = get_current_user(request)
    if user["role"] != "buyer":
        raise HTTPException(status_code=403, detail="buyers only")

    if not llm.is_configured:
        raise HTTPException(status_code=503, detail="AI service is not configured")

    body = await request.json()
    message = (body.get("message") or "").strip()
    if not message:
        raise HTTPException(status_code=400, detail="message is required")

    conversation_id = body.get("conversation_id")
    response, conv_id = await chatbot.chat(message, conversation_id)

    return {"response": response, "conversation_id": conv_id}


@app.exception_handler(HTTPException)
async def http_exception_handler(_request: Request, exc: HTTPException):
    return JSONResponse(status_code=exc.status_code, content={"error": exc.detail})


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=PORT)
