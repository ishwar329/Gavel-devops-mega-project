import asyncio
import time
import unittest
from unittest.mock import AsyncMock, patch

from recommender import Recommender, _haversine_km


def _make_auction(
    auction_id: str,
    category: str = "Bakery",
    bid_count: int = 3,
    status: str = "OPEN",
    end_minutes_from_now: int = 5,
    shop_lat: float | None = None,
    shop_lng: float | None = None,
) -> dict:
    end_ms = (time.time() + end_minutes_from_now * 60) * 1000
    return {
        "auction_id": auction_id,
        "item_title": f"Item {auction_id}",
        "shop_name": "Test Shop",
        "shop_id": "s-1",
        "category": category,
        "status": status,
        "end_time": str(int(end_ms)),
        "bid_count": bid_count,
        "current_highest_bid": 500,
        "retail_price": 1000,
        "image_url": "",
        "shop_logo_url": "",
        "description": "Test item",
        "shop_lat": shop_lat,
        "shop_lng": shop_lng,
    }


class TestHaversine(unittest.TestCase):

    def test_same_point(self):
        self.assertAlmostEqual(_haversine_km(0, 0, 0, 0), 0.0)

    def test_known_distance(self):
        dist = _haversine_km(40.7128, -74.0060, 40.7580, -73.9855)
        self.assertAlmostEqual(dist, 5.3, delta=0.5)


class TestRecommender(unittest.TestCase):

    def setUp(self):
        self.rec = Recommender()

    def test_record_bid(self):
        self.rec.record_bid("u-1", "a-1", "Bakery")
        self.assertIn("a-1", self.rec.user_bids["u-1"])
        self.assertEqual(self.rec.auction_categories["a-1"], "Bakery")

    def test_category_affinity(self):
        self.rec.record_bid("u-1", "a-1", "Bakery")
        self.rec.record_bid("u-1", "a-2", "Bakery")
        self.rec.record_bid("u-1", "a-3", "Meals")
        affinity = self.rec._category_affinity("u-1")
        self.assertAlmostEqual(affinity["Bakery"], 2 / 3)
        self.assertAlmostEqual(affinity["Meals"], 1 / 3)

    def test_category_affinity_empty(self):
        affinity = self.rec._category_affinity("nobody")
        self.assertEqual(affinity, {})

    def test_collaborative_scores(self):
        self.rec.record_bid("u-1", "a-1")
        self.rec.record_bid("u-1", "a-2")
        self.rec.record_bid("u-2", "a-1")
        self.rec.record_bid("u-2", "a-3")
        scores = self.rec._collaborative_scores("u-1")
        self.assertIn("a-3", scores)
        self.assertGreater(scores["a-3"], 0)

    def test_collaborative_no_overlap(self):
        self.rec.record_bid("u-1", "a-1")
        self.rec.record_bid("u-2", "a-2")
        scores = self.rec._collaborative_scores("u-1")
        self.assertEqual(scores, {})

    def test_recommend_excludes_own_bids(self):
        self.rec._auction_cache = [
            _make_auction("a-1"),
            _make_auction("a-2"),
        ]
        self.rec._cache_time = time.time()
        self.rec.record_bid("u-1", "a-1", "Bakery")

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1")
        )
        ids = [r["auction"]["auction_id"] for r in recs]
        self.assertNotIn("a-1", ids)
        self.assertIn("a-2", ids)

    def test_recommend_excludes_closed(self):
        self.rec._auction_cache = [
            _make_auction("a-1", status="CLOSED"),
            _make_auction("a-2", status="OPEN"),
        ]
        self.rec._cache_time = time.time()

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1")
        )
        ids = [r["auction"]["auction_id"] for r in recs]
        self.assertNotIn("a-1", ids)

    def test_recommend_ending_soon_boosted(self):
        self.rec._auction_cache = [
            _make_auction("a-soon", end_minutes_from_now=2, bid_count=1),
            _make_auction("a-later", end_minutes_from_now=30, bid_count=1),
        ]
        self.rec._cache_time = time.time()

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1")
        )
        self.assertEqual(recs[0]["auction"]["auction_id"], "a-soon")

    def test_recommend_proximity_boost(self):
        self.rec._auction_cache = [
            _make_auction("a-near", shop_lat=40.7128, shop_lng=-74.0060, bid_count=0),
            _make_auction("a-far", shop_lat=41.0, shop_lng=-74.5, bid_count=0),
        ]
        self.rec._cache_time = time.time()

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1", lat=40.7128, lng=-74.0060)
        )
        self.assertEqual(recs[0]["auction"]["auction_id"], "a-near")

    def test_recommend_max_8(self):
        self.rec._auction_cache = [
            _make_auction(f"a-{i}") for i in range(15)
        ]
        self.rec._cache_time = time.time()

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1")
        )
        self.assertLessEqual(len(recs), 8)

    def test_recommend_has_reason(self):
        self.rec._auction_cache = [_make_auction("a-1", bid_count=7)]
        self.rec._cache_time = time.time()

        recs = asyncio.get_event_loop().run_until_complete(
            self.rec.recommend("u-1")
        )
        self.assertTrue(len(recs) > 0)
        self.assertIn("reason", recs[0])
        self.assertTrue(len(recs[0]["reason"]) > 0)


if __name__ == "__main__":
    unittest.main()
