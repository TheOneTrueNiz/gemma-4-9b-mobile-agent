import json
import tempfile
import unittest
from pathlib import Path

from bridge import CodexBridge


class BridgeCliTests(unittest.TestCase):
    def test_trace_summary_formats_known_items(self):
        bridge = CodexBridge()
        trace = [
            {"type": "request", "message": "battery"},
            {"type": "fast_path_tool", "tool": "get_battery_status", "status": "ok", "duration_ms": 123},
            {"type": "critic", "step": 1, "status": "approved"},
        ]
        summary = bridge.trace_summary(trace)
        self.assertIn("request: battery", summary[0])
        self.assertIn("get_battery_status", summary[1])
        self.assertIn("critic step 1: approved", summary[2])

    def test_filter_trace_by_kind(self):
        bridge = CodexBridge()
        trace = [
            {"type": "request"},
            {"type": "actor"},
            {"type": "tool_execution"},
        ]
        filtered = bridge.filter_trace(["actor"], trace)
        self.assertEqual(filtered, [{"type": "actor"}])

    def test_load_and_export_turns(self):
        bridge = CodexBridge()
        sample_turns = [
            {"user": "hi", "assistant": "hello", "trace": [], "mode": "fast_path", "request_id": "abc"},
        ]
        with tempfile.TemporaryDirectory() as tmpdir:
            input_path = Path(tmpdir) / "turns-in.json"
            output_path = Path(tmpdir) / "turns-out.json"
            input_path.write_text(json.dumps(sample_turns), encoding="utf-8")
            count = bridge.load_turns(input_path)
            self.assertEqual(count, 1)
            bridge.export_turns(output_path)
            exported = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(exported[0]["user"], "hi")


if __name__ == "__main__":
    unittest.main()
