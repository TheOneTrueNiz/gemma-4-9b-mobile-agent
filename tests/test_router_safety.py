import unittest

from backend.router import router


class RouterSafetyTests(unittest.TestCase):
    def test_fast_path_blocks_sensitive_directory_listing(self):
        response = router.route("list files in /etc")
        self.assertIn("Blocked tool call", response["response"])


if __name__ == "__main__":
    unittest.main()
