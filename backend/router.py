import re
import json
import sys
import os
import time

# Add tools to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from tools.phone_tools import AVAILABLE_TOOLS, format_conversion_value
from backend.safety import validate_tool_call


FAST_PATH_FAMILIES = {
    "calculate": "math",
    "date_time_reason": "time",
    "convert_units": "conversion",
    "text_utility": "utility",
    "web_search": "search",
}


def user_safe_blocked_message(reason=None):
    text = (reason or "").strip()
    lowered = text.lower()
    if "path not allowed" in lowered:
        return "I can't access that path. Try a location inside your home or storage directories."
    if "brightness" in lowered:
        return "I can't set brightness with that value. Use a level between 0 and 255."
    if "http and https" in lowered or "url" in lowered:
        return "I can't open that link as requested. Try a standard http or https URL."
    if "search query cannot be empty" in lowered:
        return "I need a search query before I can search the web."
    return "I can't do that request safely. Try a safer or more specific alternative."


class FastPathRouter:
    def __init__(self):
        # Define patterns and their associated tools/actions
        self.patterns = [
            # Greetings
            (r"^(hi|hello|hey|greetings)(\s|$)", self.handle_greeting),
            
            # Time
            (r"^(what time is it|current time|date and time)$", self.handle_time),
            
            # Battery
            (r"^(battery|power level|charge level|check battery)$", self.handle_battery),

            
            # Vibration
            (r"^(vibrate|buzz)$", self.handle_vibrate),
            
            # Location
            (r"^(where am i|get location|gps)$", self.handle_location),
            
            # Brightness
            (r"^(set|change) brightness to (\d+)$", self.handle_brightness),
            
            # Listing files
            (r"^(list|show) files in (.*)$", self.handle_list_files),

            # Torch
            (r"^torch (on|off)$", self.handle_torch_state),
            (r"^(flashlight|torch)$", self.handle_torch_on),


            # TTS
            (r"^(speak|say) (.*)$", self.handle_tts),

            # Clipboard
            (r"^(get|show) clipboard$", self.handle_get_clipboard),
            (r"^set clipboard to (.*)$", self.handle_set_clipboard),

            # Open URL
            (r"^open (https?://\S+)$", self.handle_open_url),
            (r"^open (google|bing|duckduckgo)$", self.handle_open_search),

            # Search
            (r"^(search|look up|find) (the web for )?(.*)$", self.handle_web_search),

        ]
        self.math_pattern = re.compile(r"[0-9][0-9\.\s\+\-\*\/%\(\)]*[0-9\)]")
        self.datetime_prefixes = (
            "what day is ",
            "what date is ",
            "how many days until ",
            "what time is it in utc",
            "current time in utc",
            "utc time",
            "how long have you been running",
            "chronometer",
            "elapsed time",
        )
        self.conversion_pattern = re.compile(
            r"^(?:convert\s+)?(?P<value>-?\d+(?:\.\d+)?)\s*(?P<from_unit>[a-zA-Z]+)\s+(?:to|in)\s+(?P<to_unit>[a-zA-Z]+)\??$"
        )
        self.text_utility_patterns = [
            (
                re.compile(r"^(?:count|how many)\s+words\s+(?:are in|in)\s+(?P<text>.+)\??$", re.I),
                "count_words",
            ),
            (
                re.compile(r"^(?:count|how many)\s+characters\s+(?:are in|in)\s+(?P<text>.+)\??$", re.I),
                "count_characters",
            ),
            (
                re.compile(r"^(?:count|how many)\s+lines\s+(?:are in|in)\s+(?P<text>.+)\??$", re.I),
                "count_lines",
            ),
            (
                re.compile(r"^uppercase\s+(?P<text>.+)\??$", re.I),
                "uppercase",
            ),
            (
                re.compile(r"^lowercase\s+(?P<text>.+)\??$", re.I),
                "lowercase",
            ),
            (
                re.compile(r"^reverse\s+(?P<text>.+)\??$", re.I),
                "reverse",
            ),
        ]

    def handle_open_search(self, match):
        site = match.group(1).lower()
        urls = {
            "google": "https://www.google.com",
            "bing": "https://www.bing.com",
            "duckduckgo": "https://www.duckduckgo.com"
        }
        return self.execute_tool("open_url", {"url": urls[site]}, f"open {site}", route="open_search")

    def handle_greeting(self, match):
        return {
            "response": "Hello! I am your Gemma 4 Mobile Agent. I am running locally and ready to help.",
            "trace": [{"type": "fast_path", "route": "greeting", "status": "ok"}],
        }

    def handle_time(self, match):
        return self.execute_tool("get_time", {}, match.string, formatter=lambda result: f"The current time is {result}", route="time")

    def handle_battery(self, match):
        return self.execute_tool("get_battery_status", {}, match.string, formatter=lambda result: f"Battery Status: {json.dumps(result)}", route="battery")

    def handle_vibrate(self, match):
        return self.execute_tool("vibrate", {}, match.string, route="vibrate")

    def handle_location(self, match):
        return self.execute_tool("get_location", {}, match.string, formatter=lambda result: f"Your current location: {json.dumps(result)}", route="location")

    def handle_torch_state(self, match):
        enabled = match.group(1) == "on"
        return self.execute_tool("torch", {"enabled": enabled}, match.string, route="torch_state")

    def handle_torch_on(self, match):
        return self.execute_tool("torch", {"enabled": True}, match.string, route="torch_on")

    def handle_tts(self, match):
        return self.execute_tool("tts_speak", {"text": match.group(2)}, match.string, route="tts")

    def handle_get_clipboard(self, match):
        return self.execute_tool("get_clipboard", {}, match.string, formatter=lambda result: f"Clipboard: {result}", route="get_clipboard")

    def handle_set_clipboard(self, match):
        return self.execute_tool("set_clipboard", {"text": match.group(1)}, match.string, route="set_clipboard")

    def handle_open_url(self, match):
        return self.execute_tool("open_url", {"url": match.group(1)}, match.string, route="open_url")

    def handle_web_search(self, match):
        query = match.group(3).strip().strip("'\"")
        result = AVAILABLE_TOOLS["web_search"](query)
        if isinstance(result, dict) and result.get("results"):
            lines = [f"Search results for '{query}':"]
            if result.get("summary"):
                lines.append(result["summary"])
            for item in result["results"][:3]:
                host = item.get("host") or "source"
                lines.append(f"- {item['title']} [{host}]: {item['url']}")
            return {
                "response": "\n".join(lines),
                "trace": [{
                    "type": "fast_path_search",
                    "route": "web_search",
                    "query": query,
                    "status": "ok",
                    "result_count": len(result["results"]),
                }],
            }
        return {
            "response": json.dumps(result),
            "trace": [{
                "type": "fast_path_search",
                "route": "web_search",
                "query": query,
                "status": "empty",
            }],
        }

    def handle_calculate(self, expression, user_message):
        return self.execute_tool(
            "calculate",
            {"expression": expression},
            user_message,
            formatter=lambda result: f"{expression} = {result}",
            route="calculate",
        )

    def handle_date_time_reason(self, query, user_message):
        return self.execute_tool(
            "date_time_reason",
            {"query": query},
            user_message,
            route="date_time_reason",
        )

    def handle_convert_units(self, value, from_unit, to_unit, user_message):
        return self.execute_tool(
            "convert_units",
            {"value": value, "from_unit": from_unit, "to_unit": to_unit},
            user_message,
            formatter=lambda result: f"{format_conversion_value(float(value))} {from_unit} = {format_conversion_value(result)} {to_unit}",
            route="convert_units",
        )

    def handle_text_utility(self, operation, text, user_message):
        def formatter(result):
            if result["unit"] == "text":
                return result["value"]
            return f"{result['value']} {result['unit']}"

        return self.execute_tool(
            "text_utility",
            {"operation": operation, "text": text},
            user_message,
            formatter=formatter,
            route="text_utility",
        )

    def maybe_route_math(self, message):
        clean_msg = message.lower().strip()
        candidate = clean_msg
        for prefix in (
            "what is ",
            "what's ",
            "calculate ",
            "compute ",
            "solve ",
            "evaluate ",
        ):
            if candidate.startswith(prefix):
                candidate = candidate[len(prefix):]
                break
        candidate = candidate.rstrip(" ?")
        if not self.math_pattern.fullmatch(candidate):
            return None
        if not any(op in candidate for op in ("+", "-", "*", "/", "%", "(", ")")):
            return None
        return self.handle_calculate(candidate, message)

    def maybe_route_datetime(self, message):
        clean_msg = message.lower().strip()
        if any(clean_msg.startswith(prefix) or clean_msg == prefix for prefix in self.datetime_prefixes):
            return self.handle_date_time_reason(clean_msg.rstrip(" ?"), message)
        return None

    def maybe_route_conversion(self, message):
        clean_msg = message.lower().strip()
        match = self.conversion_pattern.fullmatch(clean_msg)
        if not match:
            return None
        return self.handle_convert_units(
            match.group("value"),
            match.group("from_unit"),
            match.group("to_unit"),
            message,
        )

    def maybe_route_text_utility(self, message):
        clean_msg = message.strip()
        for pattern, operation in self.text_utility_patterns:
            match = pattern.fullmatch(clean_msg)
            if match:
                return self.handle_text_utility(operation, match.group("text"), message)
        return None


    def handle_brightness(self, match):
        level = int(match.group(2))
        return self.execute_tool("set_brightness", {"level": level}, match.string, route="brightness")

    def handle_list_files(self, match):
        path = match.group(2).strip()
        # Clean up path aliases
        if "downloads" in path.lower():
            path = os.path.expanduser("~/storage/downloads")
        elif "dcim" in path.lower() or "photos" in path.lower():
            path = os.path.expanduser("~/storage/dcim")

        return self.execute_tool(
            "list_files",
            {"directory": path},
            match.string,
            formatter=lambda result: f"Files in {path}:\n" + "\n".join(result[:10]),
            route="list_files",
        )

    def execute_tool(self, tool_name, args, user_message, formatter=None, route=None):
        started_at = time.time()
        allowed, normalized_args, reason = validate_tool_call(AVAILABLE_TOOLS, tool_name, args, user_message)
        family = FAST_PATH_FAMILIES.get(tool_name, "device")
        if not allowed:
            return {
                "response": user_safe_blocked_message(reason),
                "trace": [{
                    "type": "fast_path_tool",
                    "route": route or tool_name,
                    "family": family,
                    "deterministic": family in {"math", "time", "conversion", "utility"},
                    "tool": tool_name,
                    "args": normalized_args,
                    "status": "blocked",
                    "reason": reason,
                    "duration_ms": int((time.time() - started_at) * 1000),
                }],
            }
        result = AVAILABLE_TOOLS[tool_name](**normalized_args)
        response = formatter(result) if formatter else result
        return {
            "response": response,
            "trace": [{
                "type": "fast_path_tool",
                "route": route or tool_name,
                "family": family,
                "deterministic": family in {"math", "time", "conversion", "utility"},
                "tool": tool_name,
                "args": normalized_args,
                "status": "ok",
                "result_preview": str(result)[:200],
                "duration_ms": int((time.time() - started_at) * 1000),
            }],
        }

    def route(self, message):
        """Checks if a message matches any fast-path patterns."""
        clean_msg = message.lower().strip()
        math_route = self.maybe_route_math(message)
        if math_route:
            print(f"⚡ Fast-Path math match found for: '{message}'")
            return math_route
        datetime_route = self.maybe_route_datetime(message)
        if datetime_route:
            print(f"⚡ Fast-Path datetime match found for: '{message}'")
            return datetime_route
        conversion_route = self.maybe_route_conversion(message)
        if conversion_route:
            print(f"⚡ Fast-Path conversion match found for: '{message}'")
            return conversion_route
        text_utility_route = self.maybe_route_text_utility(message)
        if text_utility_route:
            print(f"⚡ Fast-Path text utility match found for: '{message}'")
            return text_utility_route
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
