import unittest

from backend.main import make_response, summarize_trace


class TraceSummaryTests(unittest.TestCase):
    def test_summarize_trace_formats_known_entries(self):
        trace = [
            {"type": "request", "message": "battery"},
            {"type": "state_transition", "from_state": "INIT", "to_state": "ACTOR_THINK", "reason": "request_completion"},
            {
                "type": "fast_path_tool",
                "route": "battery",
                "family": "device",
                "tool": "get_battery_status",
                "status": "ok",
                "duration_ms": 12,
            },
            {"type": "fast_path_search", "query": "termux", "status": "ok", "result_count": 3},
            {
                "type": "runtime_budget",
                "hardware_profile": "high",
                "complexity": "simple",
                "max_steps": 2,
                "n_predict": 96,
                "completion_timeout": 45,
            },
            {"type": "direct_fallback", "reason": "duplicate_tool_call", "status": "ok"},
            {"type": "answer_cleanup", "source": "direct_fallback"},
        ]
        summary = summarize_trace(trace)
        self.assertIn("request: battery", summary[0])
        self.assertIn("INIT -> ACTOR_THINK", summary[1])
        self.assertIn("battery", summary[2])
        self.assertIn("family=device", summary[2])
        self.assertIn("results=3", summary[3])
        self.assertIn("runtime_budget:", summary[4])
        self.assertIn("direct_fallback:", summary[5])
        self.assertIn("answer_cleanup:", summary[6])

    def test_make_response_includes_trace_summary(self):
        payload = make_response(
            "ok",
            trace=[{"type": "request", "message": "hi"}],
            mode="fast_path",
            request_id="abc",
            request_duration_ms=42,
        )
        self.assertIn("trace_summary", payload)
        self.assertEqual(payload["trace_summary"][0], "request: hi")
        self.assertEqual(payload["request_duration_ms"], 42)


if __name__ == "__main__":
    unittest.main()
