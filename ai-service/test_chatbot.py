import asyncio
import time
import unittest
from unittest.mock import AsyncMock

from chatbot import Chatbot, _CONVERSATION_TTL
from llm import LLMClient
from vector_store import VectorStore


class TestChatbot(unittest.TestCase):

    def setUp(self):
        self.llm = LLMClient()
        self.llm.chat = AsyncMock(return_value="Here are some great bakery deals!")
        self.vs = VectorStore()
        self.vs.add("a-1", "Fresh sourdough bread from Downtown Bakery. Category: Bakery. Current bid: $3.50")
        self.vs.add("a-2", "Organic salmon fillet from Sea Market. Category: Meals. Current bid: $8.00")
        self.chatbot = Chatbot(self.llm, self.vs)

    def test_new_conversation(self):
        response, conv_id = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("What bakery items are available?")
        )
        self.assertEqual(response, "Here are some great bakery deals!")
        self.assertTrue(len(conv_id) > 0)
        self.llm.chat.assert_called_once()

    def test_conversation_continuity(self):
        _, conv_id = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("Hello")
        )
        _, conv_id2 = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("What about meals?", conv_id)
        )
        self.assertEqual(conv_id, conv_id2)
        self.assertEqual(self.llm.chat.call_count, 2)
        last_call_messages = self.llm.chat.call_args[0][1]
        self.assertEqual(len(last_call_messages), 3)

    def test_explicit_conversation_id(self):
        _, conv_id = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("Hello", "my-custom-id")
        )
        self.assertEqual(conv_id, "my-custom-id")

    def test_llm_failure_graceful(self):
        self.llm.chat = AsyncMock(side_effect=Exception("LLM down"))
        response, _ = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("Hello")
        )
        self.assertIn("sorry", response.lower())

    def test_expired_conversation_cleanup(self):
        _, conv_id = asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("Hello", "old-conv")
        )
        self.chatbot._conversations["old-conv"]["last_active"] = time.time() - _CONVERSATION_TTL - 10

        asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("New message")
        )
        self.assertNotIn("old-conv", self.chatbot._conversations)

    def test_context_from_vector_store(self):
        asyncio.get_event_loop().run_until_complete(
            self.chatbot.chat("Tell me about bread")
        )
        system_prompt = self.llm.chat.call_args[0][0]
        self.assertIn("sourdough", system_prompt.lower())


if __name__ == "__main__":
    unittest.main()
