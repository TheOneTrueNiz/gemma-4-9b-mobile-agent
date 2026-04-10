import unittest
from unittest.mock import patch

from backend.main import extract_recall_terms, rank_recall_results, recall_tool, remember_tool


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


if __name__ == "__main__":
    unittest.main()
