import unittest

from backend.main import make_response
from backend.router import router


class TraceResponseTests(unittest.TestCase):
    def test_make_response_includes_trace_mode_and_request_id(self):
        payload = make_response("ok", trace=[{"type": "demo"}], mode="fast_path", request_id="req123")
        self.assertEqual(payload["response"], "ok")
        self.assertEqual(payload["mode"], "fast_path")
        self.assertEqual(payload["request_id"], "req123")
        self.assertEqual(payload["trace"][0]["type"], "demo")

    def test_fast_path_returns_trace_payload(self):
        payload = router.route("list files in /etc")
        self.assertIn("trace", payload)
        self.assertEqual(payload["trace"][0]["status"], "blocked")


if __name__ == "__main__":
    unittest.main()
