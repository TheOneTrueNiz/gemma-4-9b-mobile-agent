import re
import json
import sys
import os

# Add tools to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from tools.phone_tools import AVAILABLE_TOOLS

class FastPathRouter:
    def __init__(self):
        # Define patterns and their associated tools/actions
        self.patterns = [
            # Greetings
            (r"^(hi|hello|hey|greetings)(\s|$)", self.handle_greeting),
            
            # Time
            (r"(what time|current time|date and time)", lambda m: {"response": f"The current time is {AVAILABLE_TOOLS['get_time']()}"}),
            
            # Battery
            (r"(battery|power level|charge)", lambda m: {"response": f"Battery Status: {json.dumps(AVAILABLE_TOOLS['get_battery_status']())}"}),
            
            # Vibration
            (r"(vibrate|buzz)", lambda m: {"response": AVAILABLE_TOOLS['vibrate']()}),
            
            # Location
            (r"(where am i|location|gps)", lambda m: {"response": f"Your current location: {json.dumps(AVAILABLE_TOOLS['get_location']())}"}),
            
            # Brightness (with basic arg extraction)
            (r"(set|change) brightness to (\d+)", self.handle_brightness),
            
            # Listing files
            (r"(list|show) files in (.*)", self.handle_list_files),

            # Torch
            (r"torch (on|off)", lambda m: {"response": AVAILABLE_TOOLS['torch'](m.group(1) == "on")}),
            (r"(flashlight|torch)", lambda m: {"response": AVAILABLE_TOOLS['torch'](True)}),

            # TTS
            (r"(speak|say) (.*)", lambda m: {"response": AVAILABLE_TOOLS['tts_speak'](m.group(2))}),

            # Clipboard
            (r"(get|show) clipboard", lambda m: {"response": f"Clipboard: {AVAILABLE_TOOLS['get_clipboard']()}"}),
            (r"set clipboard to (.*)", lambda m: {"response": AVAILABLE_TOOLS['set_clipboard'](m.group(1))}),

            # Open URL
            (r"open (https?://\S+)", lambda m: {"response": AVAILABLE_TOOLS['open_url'](m.group(1))}),
            (r"open (google|bing|duckduckgo)", self.handle_open_search),
        ]

    def handle_open_search(self, match):
        site = match.group(1).lower()
        urls = {
            "google": "https://www.google.com",
            "bing": "https://www.bing.com",
            "duckduckgo": "https://www.duckduckgo.com"
        }
        return {"response": AVAILABLE_TOOLS['open_url'](urls[site])}

    def handle_greeting(self, match):

        return {"response": "Hello! I am your Gemma assistant. I am running locally and ready to help."}

    def handle_brightness(self, match):
        level = int(match.group(2))
        return {"response": AVAILABLE_TOOLS['set_brightness'](level)}

    def handle_list_files(self, match):
        path = match.group(2).strip()
        # Clean up path aliases
        if "downloads" in path.lower():
            path = os.path.expanduser("~/storage/downloads")
        elif "dcim" in path.lower() or "photos" in path.lower():
            path = os.path.expanduser("~/storage/dcim")
            
        return {"response": f"Files in {path}:\n" + "\n".join(AVAILABLE_TOOLS['list_files'](path)[:10])}

    def route(self, message):
        """Checks if a message matches any fast-path patterns."""
        for pattern, handler in self.patterns:
            match = re.search(pattern, message.lower())
            if match:
                print(f"⚡ Fast-Path match found for: '{message}'")
                return handler(match)
        return None

# Singleton instance
router = FastPathRouter()
