import unittest

from backend.main import append_json_repair_feedback, append_tool_feedback, tool_call_signature


class AgentRecoveryTests(unittest.TestCase):
    def test_append_tool_feedback_adds_blocked_hint(self):
        prompt = append_tool_feedback("User: test\nAssistant:", '{"tool":"x"}', "Blocked tool call", blocked=True)
        self.assertIn("blocked by policy", prompt)
        self.assertTrue(prompt.endswith("Assistant:"))

    def test_append_tool_feedback_adds_rejected_hint(self):
        prompt = append_tool_feedback("User: test\nAssistant:", '{"tool":"x"}', "Critic rejected tool call.", rejected=True)
        self.assertIn("critic rejected", prompt.lower())
        self.assertTrue(prompt.endswith("Assistant:"))

    def test_tool_call_signature_is_order_stable(self):
        left = tool_call_signature("web_search", {"query": "termux", "limit": 3})
        right = tool_call_signature("web_search", {"limit": 3, "query": "termux"})
        self.assertEqual(left, right)

    def test_append_json_repair_feedback_adds_hint(self):
        prompt = append_json_repair_feedback("User: test\nAssistant:", '{"tool": "oops"')
        self.assertIn("malformed or incomplete", prompt)
        self.assertTrue(prompt.endswith("Assistant:"))


if __name__ == "__main__":
    unittest.main()
