import unittest

from backend.main import make_response, summarize_trace


class TraceSummaryTests(unittest.TestCase):
    def test_summarize_trace_formats_known_entries(self):
        trace = [
            {"type": "request", "message": "battery"},
            {"type": "fast_path_tool", "tool": "get_battery_status", "status": "ok", "duration_ms": 12},
            {"type": "fast_path_search", "query": "termux", "status": "ok", "result_count": 3},
        ]
        summary = summarize_trace(trace)
        self.assertIn("request: battery", summary[0])
        self.assertIn("get_battery_status", summary[1])
        self.assertIn("results=3", summary[2])

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
