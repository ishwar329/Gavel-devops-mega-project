import logging
import math
import time
from datetime import datetime, timezone

import httpx

from config import AUCTION_SERVICE_URL, USER_SERVICE_URL

logger = logging.getLogger(__name__)


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    R = 6371.0
    d_lat = math.radians(lat2 - lat1)
    d_lng = math.radians(lng2 - lng1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(d_lng / 2) ** 2
    )
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def _parse_end_time_ms(value) -> float:
    if isinstance(value, (int, float)):
        return float(value)
    s = str(value)
    if s.isdigit():
        return float(s)
    try:
        dt = datetime.fromisoformat(s.replace("Z", "+00:00"))
        return dt.timestamp() * 1000
    except Exception:
        return 0.0


class Recommender:
    def __init__(self) -> None:
        self.user_bids: dict[str, set[str]] = {}
        self.auction_categories: dict[str, str] = {}
        self._auction_cache: list[dict] = []
        self._cache_time: float = 0
        self._http = httpx.AsyncClient(timeout=10.0)

    def record_bid(self, user_id: str, auction_id: str, category: str | None = None) -> None:
        self.user_bids.setdefault(user_id, set()).add(auction_id)
        if category:
            self.auction_categories[auction_id] = category

    async def refresh_auctions(self) -> None:
        try:
            resp = await self._http.get(f"{AUCTION_SERVICE_URL}/auctions")
            resp.raise_for_status()
            data = resp.json()
            self._auction_cache = data.get("auctions") or []
            self._cache_time = time.time()
            for a in self._auction_cache:
                cat = a.get("category")
                if cat:
                    self.auction_categories[a["auction_id"]] = cat
        except Exception as e:
            logger.warning("Failed to refresh auctions: %s", e)

    async def get_active_auctions(self) -> list[dict]:
        if time.time() - self._cache_time > 30:
            await self.refresh_auctions()
        return self._auction_cache

    def _category_affinity(self, user_id: str) -> dict[str, float]:
        my_bids = self.user_bids.get(user_id, set())
        if not my_bids:
            return {}
        counts: dict[str, int] = {}
        for auction_id in my_bids:
            cat = self.auction_categories.get(auction_id)
            if cat:
                counts[cat] = counts.get(cat, 0) + 1
        total = sum(counts.values()) or 1
        return {cat: count / total for cat, count in counts.items()}

    def _collaborative_scores(self, user_id: str) -> dict[str, float]:
        my_bids = self.user_bids.get(user_id, set())
        if not my_bids:
            return {}
        scores: dict[str, float] = {}
        for other_id, other_bids in self.user_bids.items():
            if other_id == user_id:
                continue
            overlap = len(my_bids & other_bids)
            if overlap == 0:
                continue
            similarity = overlap / len(my_bids | other_bids)
            for auction_id in other_bids - my_bids:
                scores[auction_id] = scores.get(auction_id, 0) + similarity
        if scores:
            max_score = max(scores.values())
            if max_score > 0:
                scores = {k: v / max_score for k, v in scores.items()}
        return scores

    async def recommend(
        self,
        user_id: str,
        lat: float | None = None,
        lng: float | None = None,
        token: str | None = None,
    ) -> list[dict]:
        auctions = await self.get_active_auctions()

        if user_id not in self.user_bids and token:
            await self._bootstrap_user_bids(user_id, token)

        my_bid_ids = self.user_bids.get(user_id, set())
        cat_affinity = self._category_affinity(user_id)
        cf_scores = self._collaborative_scores(user_id)
        now_ms = time.time() * 1000

        scored: list[dict] = []
        for auction in auctions:
            aid = auction.get("auction_id", "")
            status = auction.get("status", "")
            end_time_ms = _parse_end_time_ms(auction.get("end_time", ""))

            if status == "CLOSED" or end_time_ms < now_ms:
                continue
            if aid in my_bid_ids:
                continue

            score = 0.0
            reasons: list[str] = []

            cat = auction.get("category", "Others")
            cat_score = cat_affinity.get(cat, 0)
            score += cat_score * 0.4
            if cat_score > 0.3:
                reasons.append(f"Popular in {cat}, your favorite category")

            remaining = end_time_ms - now_ms
            if 0 < remaining < 300_000:
                score += 0.25
                reasons.append("Ending soon — bid now!")
            elif 0 < remaining < 600_000:
                score += 0.15
                reasons.append("Ending in a few minutes")
            elif 0 < remaining < 900_000:
                score += 0.08

            bid_count = auction.get("bid_count", 0)
            pop_score = min(bid_count / 10.0, 1.0)
            score += pop_score * 0.2
            if bid_count >= 5:
                reasons.append(f"Trending — {bid_count} bids")

            shop_lat = auction.get("shop_lat")
            shop_lng = auction.get("shop_lng")
            if lat is not None and lng is not None and shop_lat and shop_lng:
                dist = _haversine_km(lat, lng, shop_lat, shop_lng)
                if dist < 2:
                    score += 0.15
                    reasons.append(f"Just {dist:.1f} km away")
                elif dist < 5:
                    score += 0.10
                    reasons.append(f"{dist:.1f} km away")
                elif dist < 10:
                    score += 0.05

            cf = cf_scores.get(aid, 0)
            if cf > 0:
                score += cf * 0.15
                reasons.append("Bidders like you also liked this")

            if not reasons:
                if bid_count > 0:
                    reasons.append(f"{bid_count} bid{'s' if bid_count != 1 else ''} so far")
                else:
                    reasons.append("New listing — be the first to bid")

            scored.append({
                "auction": auction,
                "reason": reasons[0],
                "score": round(score, 4),
            })

        scored.sort(key=lambda x: x["score"], reverse=True)
        return scored[:8]

    async def _bootstrap_user_bids(self, user_id: str, token: str) -> None:
        try:
            resp = await self._http.get(
                f"{USER_SERVICE_URL}/users/{user_id}/bids",
                headers={"Authorization": f"Bearer {token}"},
            )
            if resp.status_code == 200:
                bids = resp.json().get("bids") or []
                for bid in bids:
                    auction_id = bid.get("auction_id", "")
                    if auction_id:
                        self.user_bids.setdefault(user_id, set()).add(auction_id)
        except Exception as e:
            logger.warning("Failed to bootstrap user bids: %s", e)

    async def close(self) -> None:
        await self._http.aclose()
