import unittest

from backend.main import append_tool_feedback


class AgentRecoveryTests(unittest.TestCase):
    def test_append_tool_feedback_adds_blocked_hint(self):
        prompt = append_tool_feedback("User: test\nAssistant:", '{"tool":"x"}', "Blocked tool call", blocked=True)
        self.assertIn("blocked by policy", prompt)
        self.assertTrue(prompt.endswith("Assistant:"))

    def test_append_tool_feedback_adds_rejected_hint(self):
        prompt = append_tool_feedback("User: test\nAssistant:", '{"tool":"x"}', "Critic rejected tool call.", rejected=True)
        self.assertIn("critic rejected", prompt.lower())
        self.assertTrue(prompt.endswith("Assistant:"))


if __name__ == "__main__":
    unittest.main()
