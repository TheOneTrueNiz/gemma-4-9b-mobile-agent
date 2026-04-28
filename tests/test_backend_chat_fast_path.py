import asyncio
import unittest
from unittest.mock import patch

from backend.main import ChatRequest, chat


class BackendChatFastPathTests(unittest.TestCase):
    def test_fast_path_runs_without_actor_engine(self):
        fast_payload = {
            "response": "3 words",
            "trace": [{"type": "fast_path_tool", "tool": "text_utility"}],
        }
        with patch("backend.main.router.route", return_value=fast_payload), patch(
            "backend.main.ensure_actor_engine",
            return_value=False,
        ):
            payload = asyncio.run(
                chat(ChatRequest(message="count words in 'one two three'", history=[]))
            )

        self.assertEqual(payload["mode"], "fast_path")
        self.assertEqual(payload["response"], "3 words")

    def test_health_check_does_not_spawn_duplicate_actor_processes(self):
        class FakeProcess:
            def poll(self):
                return None

        fake_process = FakeProcess()
        with patch("backend.main.actor_healthcheck", return_value=False), patch(
            "backend.main.start_engine"
        ) as start_engine:
            with patch("backend.main.actor_process", fake_process), patch(
                "backend.main.actor_online",
                False,
            ):
                ready = __import__("backend.main", fromlist=["ensure_actor_engine"]).ensure_actor_engine()

        self.assertFalse(ready)
        start_engine.assert_not_called()
