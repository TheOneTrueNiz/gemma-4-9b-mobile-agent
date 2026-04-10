import unittest
from unittest.mock import patch

from backend.router import router
from datetime import datetime

from tools.phone_tools import calculate, convert_units, date_time_reason, extract_duckduckgo_html_results, web_search


SAMPLE_HTML = """
<div class="result results_links results_links_deep web-result">
  <div class="links_main links_deep result__body">
    <h2 class="result__title">
      <a rel="nofollow" class="result__a" href="https://example.com/one">Example Result One</a>
    </h2>
    <a class="result__snippet">First snippet about the result.</a>
  </div>
</div>
<div class="result results_links results_links_deep web-result">
  <div class="links_main links_deep result__body">
    <h2 class="result__title">
      <a rel="nofollow" class="result__a" href="https://example.com/two">Example Result Two</a>
    </h2>
    <div class="result__snippet">Second snippet about the result.</div>
  </div>
</div>
"""


class SearchAndRouterTests(unittest.TestCase):
    def test_calculate_evaluates_basic_expression(self):
        self.assertEqual(calculate("2 + 2 * 5"), 12)

    def test_calculate_rejects_unsafe_expression(self):
        with self.assertRaises(ValueError):
            calculate("__import__('os').system('id')")

    def test_date_time_reason_handles_relative_weekday(self):
        result = date_time_reason("what day is 10 days from now", now=datetime(2026, 4, 10, 12, 0, 0))
        self.assertEqual(result, "2026-04-20 is a Monday")

    def test_date_time_reason_handles_days_until(self):
        result = date_time_reason("how many days until april 20 2026", now=datetime(2026, 4, 10, 12, 0, 0))
        self.assertEqual(result, "10 days")

    def test_convert_units_temperature(self):
        self.assertEqual(convert_units(100, "c", "f"), 212)

    def test_convert_units_distance(self):
        self.assertAlmostEqual(convert_units(5, "miles", "km"), 8.04672, places=5)

    def test_convert_units_weight(self):
        self.assertAlmostEqual(convert_units(10, "kg", "lb"), 22.0462262, places=6)

    def test_convert_units_storage(self):
        self.assertEqual(convert_units(2048, "mb", "gb"), 2.048)

    def test_extract_duckduckgo_html_results(self):
        results = extract_duckduckgo_html_results(SAMPLE_HTML)
        self.assertEqual(len(results), 2)
        self.assertEqual(results[0]["title"], "Example Result One")
        self.assertEqual(results[1]["url"], "https://example.com/two")
        self.assertEqual(results[0]["host"], "example.com")

    @patch("tools.phone_tools.fetch_html_search_results")
    @patch("tools.phone_tools.fetch_instant_answer")
    def test_web_search_returns_structured_results(self, mock_instant, mock_html):
        mock_instant.return_value = {
            "abstract": "",
            "source": "",
            "url": "",
        }
        mock_html.return_value = [
            {"title": "A", "url": "https://example.com/a", "snippet": "Alpha"},
            {"title": "B", "url": "https://example.com/b", "snippet": "Beta"},
        ]
        result = web_search("test query")
        self.assertEqual(result["query"], "test query")
        self.assertEqual(len(result["results"]), 2)
        self.assertEqual(result["summary"], "Alpha")

    def test_router_formats_search_results(self):
        with patch.dict(
            "backend.router.AVAILABLE_TOOLS",
            {"web_search": lambda query: {
                "summary": "Summary text",
                "results": [
                    {"title": "Result A", "url": "https://example.com/a", "host": "example.com", "snippet": "A"},
                    {"title": "Result B", "url": "https://example.com/b", "host": "example.com", "snippet": "B"},
                ],
            }},
            clear=False,
        ):
            response = router.route("search the web for local llm tools")
        self.assertIn("Search results for 'local llm tools':", response["response"])
        self.assertIn("[example.com]", response["response"])
        self.assertIn("fast_path_search", str(response["trace"]))

    def test_router_routes_math_to_calculator(self):
        response = router.route("what is 2 + 2 * 5?")
        self.assertIn("2 + 2 * 5 = 12", response["response"])
        self.assertEqual(response["trace"][0]["tool"], "calculate")

    def test_router_routes_datetime_reasoning(self):
        with patch.dict(
            "backend.router.AVAILABLE_TOOLS",
            {"date_time_reason": lambda query: "2026-04-20 is a Monday"},
            clear=False,
        ):
            response = router.route("what day is 10 days from now")
        self.assertEqual(response["trace"][0]["tool"], "date_time_reason")
        self.assertIn("2026-04-20 is a Monday", response["response"])

    def test_router_routes_unit_conversion(self):
        response = router.route("convert 5 miles to km")
        self.assertIn("5 miles =", response["response"])
        self.assertEqual(response["trace"][0]["tool"], "convert_units")


if __name__ == "__main__":
    unittest.main()
