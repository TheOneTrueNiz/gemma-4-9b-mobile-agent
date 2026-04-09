import re
import json
import sys
import os

# Add tools to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from tools.phone_tools import AVAILABLE_TOOLS
from backend.safety import validate_tool_call

class FastPathRouter:
    def __init__(self):
        # Define patterns and their associated tools/actions
        self.patterns = [
            # Greetings
            (r"^(hi|hello|hey|greetings)(\s|$)", self.handle_greeting),
            
            # Time
            (r"^(what time is it|current time|date and time)$", lambda m: {"response": f"The current time is {AVAILABLE_TOOLS['get_time']()}"}),
            
            # Battery
            (r"^(battery|power level|charge level|check battery)$", lambda m: {"response": f"Battery Status: {json.dumps(AVAILABLE_TOOLS['get_battery_status']())}"}),

            
            # Vibration
            (r"^(vibrate|buzz)$", lambda m: {"response": AVAILABLE_TOOLS['vibrate']()}),
            
            # Location
            (r"^(where am i|get location|gps)$", lambda m: {"response": f"Your current location: {json.dumps(AVAILABLE_TOOLS['get_location']())}"}),
            
            # Brightness
            (r"^(set|change) brightness to (\d+)$", self.handle_brightness),
            
            # Listing files
            (r"^(list|show) files in (.*)$", self.handle_list_files),

            # Torch
            (r"^torch (on|off)$", lambda m: {"response": AVAILABLE_TOOLS['torch'](m.group(1) == "on")}),
            (r"^(flashlight|torch)$", lambda m: {"response": AVAILABLE_TOOLS['torch'](True)}),


            # TTS
            (r"^(speak|say) (.*)$", lambda m: {"response": AVAILABLE_TOOLS['tts_speak'](m.group(2))}),

            # Clipboard
            (r"^(get|show) clipboard$", lambda m: {"response": f"Clipboard: {AVAILABLE_TOOLS['get_clipboard']()}"}),
            (r"^set clipboard to (.*)$", lambda m: {"response": AVAILABLE_TOOLS['set_clipboard'](m.group(1))}),

            # Open URL
            (r"^open (https?://\S+)$", lambda m: {"response": AVAILABLE_TOOLS['open_url'](m.group(1))}),
            (r"^open (google|bing|duckduckgo)$", self.handle_open_search),

            # Search
            (r"^(search|look up|find) (the web for )?(.*)$", self.handle_web_search),

        ]

    def handle_open_search(self, match):
        site = match.group(1).lower()
        urls = {
            "google": "https://www.google.com",
            "bing": "https://www.bing.com",
            "duckduckgo": "https://www.duckduckgo.com"
        }
        return self.execute_tool("open_url", {"url": urls[site]}, f"open {site}")

    def handle_greeting(self, match):
        return {"response": "Hello! I am your Gemma 4 Mobile Agent. I am running locally and ready to help."}

    def handle_web_search(self, match):
        query = match.group(3).strip().strip("'\"")
        result = AVAILABLE_TOOLS["web_search"](query)
        if isinstance(result, dict) and result.get("results"):
            lines = [f"Search results for '{query}':"]
            if result.get("summary"):
                lines.append(result["summary"])
            for item in result["results"][:3]:
                lines.append(f"- {item['title']}: {item['url']}")
            return {"response": "\n".join(lines)}
        return {"response": json.dumps(result)}


    def handle_brightness(self, match):
        level = int(match.group(2))
        return self.execute_tool("set_brightness", {"level": level}, match.string)

    def handle_list_files(self, match):
        path = match.group(2).strip()
        # Clean up path aliases
        if "downloads" in path.lower():
            path = os.path.expanduser("~/storage/downloads")
        elif "dcim" in path.lower() or "photos" in path.lower():
            path = os.path.expanduser("~/storage/dcim")

        return self.execute_tool("list_files", {"directory": path}, match.string, formatter=lambda result: f"Files in {path}:\n" + "\n".join(result[:10]))

    def execute_tool(self, tool_name, args, user_message, formatter=None):
        allowed, normalized_args, reason = validate_tool_call(AVAILABLE_TOOLS, tool_name, args, user_message)
        if not allowed:
            return {"response": f"Blocked tool call: {reason}"}
        result = AVAILABLE_TOOLS[tool_name](**normalized_args)
        if formatter:
            return {"response": formatter(result)}
        return {"response": result}

    def route(self, message):
        """Checks if a message matches any fast-path patterns."""
        clean_msg = message.lower().strip()
        for pattern, handler in self.patterns:
            # Use match for greetings to ensure they don't hijack complex sentences
            if "hi|" in pattern or "hello|" in pattern:
                match = re.match(pattern, clean_msg)
            else:
                match = re.search(pattern, clean_msg)
                
            if match:
                print(f"⚡ Fast-Path match found for: '{message}'")
                return handler(match)
        return None


# Singleton instance
router = FastPathRouter()
