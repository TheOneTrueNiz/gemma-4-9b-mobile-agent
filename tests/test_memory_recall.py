import unittest
from unittest.mock import patch

from backend.main import extract_recall_terms, rank_recall_results, recall_tool, remember_tool, format_actor_prompt


class MemoryRecallTests(unittest.TestCase):
    def test_extract_recall_terms_keeps_query_and_keywords(self):
        terms = extract_recall_terms("When is Maya gymnastics practice on Wednesday?")
        self.assertEqual(terms[0], "When is Maya gymnastics practice on Wednesday?")
        self.assertIn("maya", terms)
        self.assertIn("gymnastics", terms)

    def test_rank_recall_results_prefers_more_matching_entries(self):
        results = [
            ("World", "Family", "Maya has gymnastics practice every Wednesday", "2026-04-09 00:00:00"),
            ("World", "General", "Maya likes pizza", "2026-04-08 00:00:00"),
        ]
        ranked = rank_recall_results("Maya gymnastics Wednesday", results)
        self.assertEqual(ranked[0][2], "Maya has gymnastics practice every Wednesday")

    @patch("backend.main.memory")
    def test_recall_tool_combines_multiple_terms(self, mock_memory):
        def fake_recall(term):
            mapping = {
                "Maya gymnastics Wednesday": [],
                "maya": [("World", "Family", "Maya likes pizza", "2026-04-08 00:00:00")],
                "gymnastics": [("World", "Family", "Maya has gymnastics practice every Wednesday", "2026-04-09 00:00:00")],
                "wednesday": [("World", "Family", "Maya has gymnastics practice every Wednesday", "2026-04-09 00:00:00")],
            }
            return mapping.get(term, [])

        mock_memory.recall.side_effect = fake_recall
        text = recall_tool("Maya gymnastics Wednesday")
        self.assertIn("gymnastics practice", text)
        self.assertIn("Maya likes pizza", text)

    def test_rank_recall_results_prefers_recency_when_scores_tie(self):
        results = [
            ("World", "Projects", "SmolClaw uses deterministic tool recovery", "2026-04-08 00:00:00"),
            ("World", "Projects", "SmolClaw uses deterministic tool recovery", "2026-04-10 00:00:00"),
        ]
        ranked = rank_recall_results("SmolClaw deterministic recovery", results)
        self.assertEqual(ranked[0][3], "2026-04-10 00:00:00")

    @patch("backend.main.memory")
    def test_remember_tool_rejects_transient_facts(self, mock_memory):
        response = remember_tool("The battery is at 18 percent today", wing="World", floor="Status")
        self.assertIn("Memory rejected: transient_fact", response)
        mock_memory.add_fact.assert_not_called()

    @patch("backend.main.memory")
    def test_remember_tool_saves_normalized_useful_fact(self, mock_memory):
        response = remember_tool("  Maya likes strawberry yogurt  ", wing=" Family ", floor=" Preferences ")
        self.assertIn("Memory saved to Family > Preferences", response)
        self.assertIn("(preference)", response)
        mock_memory.add_fact.assert_called_once_with("Family", "Preferences", "Maya likes strawberry yogurt")

    @patch("backend.main.memory")
    def test_recall_tool_limits_output_volume(self, mock_memory):
        def fake_recall(term):
            if term == "maya":
                return [
                    ("World", "Family", "Maya likes pizza", "2026-04-08 00:00:00"),
                    ("World", "Family", "Maya likes music", "2026-04-09 00:00:00"),
                    ("World", "Family", "Maya likes coding", "2026-04-10 00:00:00"),
                    ("World", "Family", "Maya likes robotics", "2026-04-11 00:00:00"),
                ]
            return []

        mock_memory.recall.side_effect = fake_recall
        text = recall_tool("maya", limit=3)
        self.assertIn("Maya likes robotics", text)
        self.assertIn("Maya likes coding", text)
        self.assertIn("Maya likes music", text)
        self.assertNotIn("Maya likes pizza", text)

    @patch("backend.main.memory")
    def test_format_actor_prompt_injects_only_top_memory_budget(self, mock_memory):
        def fake_recall(term):
            if term == "maya":
                return [
                    ("World", "Family", "Maya likes pizza", "2026-04-08 00:00:00"),
                    ("World", "Family", "Maya has gymnastics practice every Wednesday", "2026-04-11 00:00:00"),
                    ("World", "Projects", "Maya works on SmolClaw", "2026-04-10 00:00:00"),
                    ("World", "Family", "Maya likes robotics", "2026-04-09 00:00:00"),
                ]
            return []

        mock_memory.recall.side_effect = fake_recall
        prompt = format_actor_prompt("maya", history=[])
        self.assertIn("Maya has gymnastics practice every Wednesday", prompt)
        self.assertIn("Maya works on SmolClaw", prompt)
        self.assertIn("Maya likes robotics", prompt)
        self.assertNotIn("Maya likes pizza", prompt)

    @patch("backend.main.memory")
    def test_format_actor_prompt_includes_deterministic_tool_rules(self, mock_memory):
        mock_memory.recall.return_value = []
        prompt = format_actor_prompt("convert 5 miles to km", history=[])
        self.assertIn("DETERMINISTIC TOOL DISCIPLINE", prompt)
        self.assertIn("use calculate", prompt)
        self.assertIn("use date_time_reason", prompt)
        self.assertIn("use convert_units", prompt)
        self.assertIn("use text_utility", prompt)
        self.assertIn("Do not guess or estimate when a deterministic tool applies", prompt)

    @patch("backend.main.memory")
    def test_format_actor_prompt_selects_text_utility_for_text_query(self, mock_memory):
        mock_memory.recall.return_value = []
        prompt = format_actor_prompt("uppercase hello phone agent", history=[])
        self.assertIn("text_utility(operation, text)", prompt)
        self.assertNotIn("send_sms(number, message)", prompt)


if __name__ == "__main__":
    unittest.main()
