import unittest
from vector_store import VectorStore


class TestVectorStore(unittest.TestCase):

    def setUp(self):
        self.vs = VectorStore()
        self.vs.clear()

    def test_empty_search(self):
        results = self.vs.search("anything")
        self.assertEqual(results, [])

    def test_add_and_search(self):
        self.vs.add("1", "Fresh sourdough bread from local bakery")
        self.vs.add("2", "Organic salmon fillet premium quality")
        self.vs.add("3", "Chocolate croissant pastry morning treat")

        results = self.vs.search("bakery bread", top_k=2)
        self.assertTrue(len(results) > 0)
        self.assertEqual(results[0]["document"]["id"], "1")

    def test_search_relevance(self):
        self.vs.add("a", "Sushi platter with fresh salmon and tuna")
        self.vs.add("b", "Vegetable soup with potatoes and carrots")
        self.vs.add("c", "Premium salmon nigiri sushi set")

        results = self.vs.search("sushi salmon", top_k=3)
        ids = [r["document"]["id"] for r in results]
        self.assertIn("a", ids[:2])
        self.assertIn("c", ids[:2])

    def test_metadata_preserved(self):
        self.vs.add("x", "test document about food", metadata={"category": "Bakery", "price": 500})
        results = self.vs.search("food document")
        self.assertTrue(len(results) > 0)
        self.assertEqual(results[0]["document"]["metadata"]["category"], "Bakery")

    def test_clear(self):
        self.vs.add("1", "something important about food")
        self.assertEqual(self.vs.count, 1)
        self.vs.clear()
        self.assertEqual(self.vs.count, 0)

    def test_score_range(self):
        self.vs.add("1", "chocolate cake dessert")
        results = self.vs.search("chocolate cake")
        for r in results:
            self.assertGreaterEqual(r["score"], 0.0)
            self.assertLessEqual(r["score"], 1.01)

    def test_upsert_dedup(self):
        self.vs.add("1", "original text")
        self.vs.add("1", "updated text about auctions")
        self.assertEqual(self.vs.count, 1)
        results = self.vs.search("auctions")
        self.assertEqual(results[0]["document"]["text"], "updated text about auctions")

    def test_unsafe_metadata_filtered(self):
        self.vs.add("1", "test item", metadata={"name": "bread", "tags": ["a", "b"], "nested": {"x": 1}})
        results = self.vs.search("test item")
        meta = results[0]["document"]["metadata"]
        self.assertEqual(meta["name"], "bread")
        self.assertNotIn("tags", meta)
        self.assertNotIn("nested", meta)


if __name__ == "__main__":
    unittest.main()
