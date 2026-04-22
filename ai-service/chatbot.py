import logging
import time
import uuid

from llm import LLMClient
from vector_store import VectorStore

logger = logging.getLogger(__name__)

_SYSTEM_PROMPT = """You are a friendly and helpful assistant for Gavel, a surplus food and item auction platform that helps reduce food waste by auctioning unsold goods from local shops at discounted prices.

Use the context below to answer the user's question about available auctions, items, and shops. Be concise, warm, and helpful. If the context doesn't have the answer, say so honestly and offer general guidance about using the platform.

Current auctions and items:
{context}"""

_MAX_HISTORY = 10
_CONVERSATION_TTL = 1800  # 30 minutes


class Chatbot:
    def __init__(self, llm: LLMClient, vector_store: VectorStore) -> None:
        self.llm = llm
        self.vector_store = vector_store
        self._conversations: dict[str, dict] = {}

    def _cleanup_expired(self) -> None:
        now = time.time()
        expired = [
            cid
            for cid, conv in self._conversations.items()
            if now - conv["last_active"] > _CONVERSATION_TTL
        ]
        for cid in expired:
            del self._conversations[cid]

    async def chat(self, message: str, conversation_id: str | None = None) -> tuple[str, str]:
        self._cleanup_expired()

        if not conversation_id or conversation_id not in self._conversations:
            conversation_id = conversation_id or str(uuid.uuid4())
            self._conversations[conversation_id] = {
                "messages": [],
                "last_active": time.time(),
            }

        conv = self._conversations[conversation_id]
        conv["last_active"] = time.time()

        results = self.vector_store.search(message, top_k=5)
        context_parts = [r["document"]["text"] for r in results]
        context = "\n\n".join(context_parts) if context_parts else "No specific auction data available right now."

        system = _SYSTEM_PROMPT.format(context=context)

        conv["messages"].append({"role": "user", "content": message})
        recent = conv["messages"][-_MAX_HISTORY:]

        try:
            response = await self.llm.chat(system, recent)
        except Exception as e:
            logger.error("LLM call failed: %s", e)
            response = "I'm sorry, I'm having trouble right now. Please try again in a moment."

        conv["messages"].append({"role": "assistant", "content": response})

        return response, conversation_id
