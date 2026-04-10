import unittest

from backend.tool_policy import get_tool_risk, requires_critic_review, select_relevant_tools


class ToolPolicyTests(unittest.TestCase):
    def test_read_only_tool_bypasses_critic(self):
        self.assertEqual(get_tool_risk("web_search"), "read_only")
        self.assertFalse(requires_critic_review("web_search"))

    def test_high_risk_tool_requires_critic(self):
        self.assertEqual(get_tool_risk("send_sms"), "high_risk")
        self.assertTrue(requires_critic_review("send_sms"))

    def test_select_relevant_tools_adds_query_specific_tools(self):
        tools = {
            "web_search": object(),
            "recall": object(),
            "remember": object(),
            "send_sms": object(),
            "torch": object(),
            "calculate": object(),
        }
        selected = select_relevant_tools("turn on the flashlight", tools)
        self.assertIn("torch", selected)
        self.assertIn("web_search", selected)
        self.assertNotIn("send_sms", selected)

    def test_select_relevant_tools_adds_calculate_for_math(self):
        tools = {
            "web_search": object(),
            "recall": object(),
            "remember": object(),
            "calculate": object(),
        }
        selected = select_relevant_tools("calculate 44 / 11", tools)
        self.assertIn("calculate", selected)
