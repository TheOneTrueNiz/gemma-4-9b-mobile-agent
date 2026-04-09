import unittest
from unittest.mock import patch

from backend.router import router
from tools.phone_tools import extract_duckduckgo_html_results, web_search


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


if __name__ == "__main__":
    unittest.main()
