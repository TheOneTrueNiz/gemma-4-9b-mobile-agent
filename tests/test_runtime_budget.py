import unittest

from backend.runtime_budget import classify_request_complexity, select_runtime_budget


class RuntimeBudgetTests(unittest.TestCase):
    def test_classify_simple_request(self):
        self.assertEqual(classify_request_complexity("what is 2 plus 2?"), "simple")

    def test_classify_complex_request(self):
        self.assertEqual(
            classify_request_complexity("Analyze the architecture tradeoff and explain why the recovery strategy should change"),
            "complex",
        )

    def test_select_runtime_budget_for_high_profile_simple_request(self):
        budget = select_runtime_budget("what is 2 plus 2?", hardware_profile="high")
        self.assertEqual(budget["n_predict"], 96)
        self.assertEqual(budget["max_steps"], 2)
        self.assertEqual(budget["completion_timeout"], 45)

    def test_select_runtime_budget_for_high_profile_complex_request(self):
        budget = select_runtime_budget("Explain why local mobile agents need strong recovery loops and compare tradeoffs", hardware_profile="high")
        self.assertEqual(budget["n_predict"], 320)
        self.assertEqual(budget["max_steps"], 4)
        self.assertEqual(budget["completion_timeout"], 120)


if __name__ == "__main__":
    unittest.main()
