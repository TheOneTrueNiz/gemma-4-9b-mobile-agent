import json
import unittest
from pathlib import Path


FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "chat_regressions.json"


class RegressionFixtureSchemaTests(unittest.TestCase):
    def test_fixture_has_expected_shape(self):
        cases = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(cases), 3)
        for case in cases:
            self.assertIn("name", case)
            self.assertIn("message", case)
            self.assertIsInstance(case.get("response_contains", []), list)
            self.assertIsInstance(case.get("trace_types", []), list)


if __name__ == "__main__":
    unittest.main()
