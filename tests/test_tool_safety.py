import unittest

from backend.safety import is_safe_path, validate_tool_call
from tools.phone_tools import AVAILABLE_TOOLS


class ToolSafetyTests(unittest.TestCase):
    def test_blocks_sensitive_path(self):
        allowed, _, reason = validate_tool_call(AVAILABLE_TOOLS, "list_files", {"path": "/etc"}, "list files in /etc")
        self.assertFalse(allowed)
        self.assertIn("Path not allowed", reason)

    def test_allows_project_path(self):
        self.assertTrue(is_safe_path("."))

    def test_requires_explicit_sms_intent(self):
        allowed, _, reason = validate_tool_call(
            AVAILABLE_TOOLS,
            "send_sms",
            {"number": "+15551234567", "message": "hello"},
            "say hello to Bob",
        )
        self.assertFalse(allowed)
        self.assertIn("explicit user intent", reason)

    def test_allows_explicit_sms_intent(self):
        allowed, _, reason = validate_tool_call(
            AVAILABLE_TOOLS,
            "send_sms",
            {"number": "+15551234567", "message": "hello"},
            "send a text to +15551234567 saying hello",
        )
        self.assertTrue(allowed)
        self.assertEqual(reason, "")

    def test_blocks_invalid_url_scheme(self):
        allowed, _, reason = validate_tool_call(
            AVAILABLE_TOOLS,
            "open_url",
            {"url": "javascript:alert(1)"},
            "open this",
        )
        self.assertFalse(allowed)
        self.assertIn("http and https", reason)


if __name__ == "__main__":
    unittest.main()
