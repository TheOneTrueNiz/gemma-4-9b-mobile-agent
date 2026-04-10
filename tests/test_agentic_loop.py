import asyncio
import unittest
from unittest.mock import patch

from backend.main import ChatRequest, chat


class FakeResponse:
    def __init__(self, content, status_code=200):
        self.status_code = status_code
        self._content = content

    def json(self):
        return {"content": self._content}


class AgenticLoopTests(unittest.TestCase):
    def run_chat(self, message):
        request = ChatRequest(message=message, history=[])
        return asyncio.run(chat(request))

    @patch("backend.main.ensure_actor_engine", return_value=True)
    @patch("backend.main.router.route", return_value=None)
    @patch("backend.main.recall_tool", return_value="No relevant memories found.")
    @patch("backend.main.requests.post")
    def test_malformed_json_recovers_to_direct_answer(self, mock_post, _mock_recall, _mock_route, _mock_engine):
        mock_post.side_effect = [
            FakeResponse('{"tool": "web_search", "args": '),
            FakeResponse("Recovered direct answer."),
        ]

        payload = self.run_chat("tell me something useful")

        self.assertEqual(payload["response"], "Recovered direct answer.")
        trace_types = [item["type"] for item in payload["trace"]]
        self.assertIn("tool_parse", trace_types)
        self.assertIn("state_transition", trace_types)

    @patch("backend.main.ensure_actor_engine", return_value=True)
    @patch("backend.main.router.route", return_value=None)
    @patch("backend.main.recall_tool", return_value="No relevant memories found.")
    @patch("backend.main.verify_with_critic", return_value=True)
    @patch("backend.main.requests.post")
    def test_duplicate_tool_call_gets_blocked_then_recovers(self, mock_post, _mock_critic, _mock_recall, _mock_route, _mock_engine):
        mock_post.side_effect = [
            FakeResponse('{"tool":"web_search","args":{"query":"termux"}}'),
            FakeResponse('{"tool":"web_search","args":{"query":"termux"}}'),
            FakeResponse("Final answer after duplicate tool block."),
        ]

        with patch.dict(
            "backend.main.AVAILABLE_TOOLS",
            {"web_search": lambda query: {"summary": f"search for {query}", "results": []}},
            clear=False,
        ):
            payload = self.run_chat("research termux")

        self.assertEqual(payload["response"], "Final answer after duplicate tool block.")
        blocked_repeat = [
            item for item in payload["trace"]
            if item.get("type") == "tool_execution" and item.get("status") == "blocked_repeat"
        ]
        self.assertEqual(len(blocked_repeat), 1)

    @patch("backend.main.ensure_actor_engine", return_value=True)
    @patch("backend.main.router.route", return_value=None)
    @patch("backend.main.recall_tool", return_value="No relevant memories found.")
    @patch("backend.main.verify_with_critic", return_value=True)
    @patch("backend.main.requests.post")
    def test_successful_tool_then_final_answer(self, mock_post, _mock_critic, _mock_recall, _mock_route, _mock_engine):
        mock_post.side_effect = [
            FakeResponse('{"tool":"web_search","args":{"query":"llama.cpp android"}}'),
            FakeResponse("Here is the answer based on the tool result."),
        ]

        with patch.dict(
            "backend.main.AVAILABLE_TOOLS",
            {"web_search": lambda query: {"summary": f"summary for {query}", "results": []}},
            clear=False,
        ):
            payload = self.run_chat("find llama.cpp android info")

        self.assertEqual(payload["response"], "Here is the answer based on the tool result.")
        tool_exec = [
            item for item in payload["trace"]
            if item.get("type") == "tool_execution" and item.get("status") == "ok"
        ]
        self.assertEqual(len(tool_exec), 1)


if __name__ == "__main__":
    unittest.main()
