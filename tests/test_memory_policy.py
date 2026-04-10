import unittest

from backend.memory_policy import assess_memory_candidate, classify_memory_value, normalize_memory_text


class MemoryPolicyTests(unittest.TestCase):
    def test_normalize_memory_text_collapses_whitespace(self):
        self.assertEqual(normalize_memory_text("  Maya   likes   pizza \n"), "Maya likes pizza")

    def test_assess_memory_candidate_rejects_question(self):
        decision = assess_memory_candidate("What is the weather today?")
        self.assertFalse(decision["accepted"])
        self.assertEqual(decision["reason"], "question_not_memory")

    def test_assess_memory_candidate_rejects_imperative(self):
        decision = assess_memory_candidate("Search the web for llama.cpp")
        self.assertFalse(decision["accepted"])
        self.assertEqual(decision["reason"], "imperative_not_memory")

    def test_assess_memory_candidate_accepts_preference(self):
        decision = assess_memory_candidate("Maya likes strawberry yogurt", wing="Family", floor="Preferences")
        self.assertTrue(decision["accepted"])
        self.assertEqual(decision["memory_type"], "preference")
        self.assertEqual(decision["wing"], "Family")
        self.assertEqual(decision["floor"], "Preferences")

    def test_classify_memory_value_marks_profile(self):
        self.assertEqual(classify_memory_value("My name is Niz"), "profile")


if __name__ == "__main__":
    unittest.main()
