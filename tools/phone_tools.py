import subprocess
import json
import os
import glob
import re
import ast
import operator
import time
import urllib.request
import urllib.parse
from html import unescape
from datetime import datetime, timedelta, timezone, date

import requests

SEARCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) GemmaMobileAgent/1.0"
}

SAFE_MATH_BINARY_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
}

SAFE_MATH_UNARY_OPERATORS = {
    ast.UAdd: operator.pos,
    ast.USub: operator.neg,
}

PROCESS_START_MONOTONIC = time.monotonic()
MONTH_FORMATS = ("%B %d %Y", "%b %d %Y", "%B %d, %Y", "%b %d, %Y", "%B %d", "%b %d")
DISTANCE_UNITS_IN_METERS = {
    "m": 1.0,
    "meter": 1.0,
    "meters": 1.0,
    "km": 1000.0,
    "kilometer": 1000.0,
    "kilometers": 1000.0,
    "mi": 1609.344,
    "mile": 1609.344,
    "miles": 1609.344,
    "ft": 0.3048,
    "foot": 0.3048,
    "feet": 0.3048,
}
WEIGHT_UNITS_IN_KG = {
    "g": 0.001,
    "gram": 0.001,
    "grams": 0.001,
    "kg": 1.0,
    "kilogram": 1.0,
    "kilograms": 1.0,
    "lb": 0.45359237,
    "lbs": 0.45359237,
    "pound": 0.45359237,
    "pounds": 0.45359237,
}
STORAGE_UNITS_IN_BYTES = {
    "b": 1.0,
    "byte": 1.0,
    "bytes": 1.0,
    "kb": 1000.0,
    "mb": 1000.0 ** 2,
    "gb": 1000.0 ** 3,
    "tb": 1000.0 ** 4,
    "kib": 1024.0,
    "mib": 1024.0 ** 2,
    "gib": 1024.0 ** 3,
    "tib": 1024.0 ** 4,
}

def run_termux_command(command):
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        return f"Error: {e.stderr}"


def parse_json_output(output):
    try:
        return json.loads(output)
    except Exception:
        return {"error": output}

def get_battery_status():
    """Returns the battery status of the device."""
    output = run_termux_command(["termux-battery-status"])
    return parse_json_output(output)

def vibrate(duration_ms=200):
    """Vibrates the device for a specified duration."""
    run_termux_command(["termux-vibrate", "-d", str(duration_ms)])
    return f"Vibrated for {duration_ms}ms"

def get_location():
    """Returns the current GPS location."""
    output = run_termux_command(["termux-location"])
    return parse_json_output(output)

def send_sms(number, message):
    """Sends an SMS message to a specific number."""
    run_termux_command(["termux-sms-send", "-n", number, message])
    return f"SMS sent to {number}"

def list_contacts():
    """Lists phone contacts."""
    output = run_termux_command(["termux-contact-list"])
    return parse_json_output(output)

def set_brightness(level):
    """Sets the screen brightness (0-255)."""
    run_termux_command(["termux-brightness", str(level)])
    return f"Brightness set to {level}"

def toast(message):
    """Shows a small popup message (toast) on the screen."""
    run_termux_command(["termux-toast", message])
    return "Toast displayed"

def get_time():
    """Returns the current date and time."""
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def get_utc_time():
    """Returns the current UTC date and time."""
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")


def format_duration(total_seconds):
    total_seconds = int(total_seconds)
    minutes, seconds = divmod(total_seconds, 60)
    hours, minutes = divmod(minutes, 60)
    days, hours = divmod(hours, 24)
    parts = []
    if days:
        parts.append(f"{days}d")
    if hours or parts:
        parts.append(f"{hours}h")
    if minutes or parts:
        parts.append(f"{minutes}m")
    parts.append(f"{seconds}s")
    return " ".join(parts)


def get_chronometer():
    """Returns monotonic elapsed time since the agent process started."""
    elapsed = time.monotonic() - PROCESS_START_MONOTONIC
    return {
        "elapsed_seconds": round(elapsed, 3),
        "elapsed_human": format_duration(elapsed),
    }


def _eval_math_node(node):
    if isinstance(node, ast.Expression):
        return _eval_math_node(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in SAFE_MATH_BINARY_OPERATORS:
        left = _eval_math_node(node.left)
        right = _eval_math_node(node.right)
        return SAFE_MATH_BINARY_OPERATORS[type(node.op)](left, right)
    if isinstance(node, ast.UnaryOp) and type(node.op) in SAFE_MATH_UNARY_OPERATORS:
        return SAFE_MATH_UNARY_OPERATORS[type(node.op)](_eval_math_node(node.operand))
    raise ValueError("Unsupported expression")


def calculate(expression):
    """Evaluates a basic arithmetic expression safely."""
    text = (expression or "").strip()
    if not text:
        raise ValueError("Empty expression")
    if not re.fullmatch(r"[0-9\.\+\-\*\/%\(\)\s]+", text):
        raise ValueError("Expression contains unsupported characters")
    parsed = ast.parse(text, mode="eval")
    return _eval_math_node(parsed)


def parse_reference_date(text, now=None):
    now = now or datetime.now()
    normalized = re.sub(r"(\d+)(st|nd|rd|th)", r"\1", text.strip(), flags=re.I)
    for fmt in MONTH_FORMATS:
        try:
            parsed = datetime.strptime(normalized, fmt)
            if "%Y" not in fmt:
                parsed = parsed.replace(year=now.year)
            return parsed.date()
        except ValueError:
            continue
    try:
        return datetime.strptime(normalized, "%Y-%m-%d").date()
    except ValueError:
        return None


def date_time_reason(query, now=None):
    """Resolves deterministic date and time reasoning queries."""
    now = now or datetime.now()
    today = now.date()
    text = (query or "").strip().lower()

    if text in {"what time is it in utc", "current time in utc", "utc time"}:
        return get_utc_time()

    if text in {"how long have you been running", "chronometer", "elapsed time"}:
        chrono = get_chronometer()
        return f"Elapsed time: {chrono['elapsed_human']} ({chrono['elapsed_seconds']}s)"

    match = re.fullmatch(r"what day is (\d+) days? from (now|today)", text)
    if match:
        delta_days = int(match.group(1))
        target = today + timedelta(days=delta_days)
        return f"{target.isoformat()} is a {target.strftime('%A')}"

    match = re.fullmatch(r"what date is (\d+) days? from (now|today)", text)
    if match:
        delta_days = int(match.group(1))
        target = today + timedelta(days=delta_days)
        return target.isoformat()

    match = re.fullmatch(r"how many days until (.+)", text)
    if match:
        target = parse_reference_date(match.group(1), now=now)
        if not target:
            raise ValueError("Unsupported date format")
        delta = (target - today).days
        return f"{delta} days"

    match = re.fullmatch(r"what day is (.+)", text)
    if match:
        subject = match.group(1).strip()
        target = parse_reference_date(subject, now=now)
        if target:
            return f"{target.isoformat()} is a {target.strftime('%A')}"

    raise ValueError("Unsupported date/time reasoning query")


def format_conversion_value(value):
    rounded = round(float(value), 6)
    if rounded.is_integer():
        return str(int(rounded))
    return f"{rounded:.6f}".rstrip("0").rstrip(".")


def convert_units(value, from_unit, to_unit):
    """Converts deterministic temperature, distance, weight, and storage units."""
    source = (from_unit or "").strip().lower()
    target = (to_unit or "").strip().lower()
    numeric_value = float(value)

    temp_units = {"c", "f", "celsius", "fahrenheit"}
    if source in temp_units and target in temp_units:
        if source in {"celsius", "c"} and target in {"fahrenheit", "f"}:
            return (numeric_value * 9 / 5) + 32
        if source in {"fahrenheit", "f"} and target in {"celsius", "c"}:
            return (numeric_value - 32) * 5 / 9
        return numeric_value

    if source in DISTANCE_UNITS_IN_METERS and target in DISTANCE_UNITS_IN_METERS:
        meters = numeric_value * DISTANCE_UNITS_IN_METERS[source]
        return meters / DISTANCE_UNITS_IN_METERS[target]

    if source in WEIGHT_UNITS_IN_KG and target in WEIGHT_UNITS_IN_KG:
        kilograms = numeric_value * WEIGHT_UNITS_IN_KG[source]
        return kilograms / WEIGHT_UNITS_IN_KG[target]

    if source in STORAGE_UNITS_IN_BYTES and target in STORAGE_UNITS_IN_BYTES:
        bytes_value = numeric_value * STORAGE_UNITS_IN_BYTES[source]
        return bytes_value / STORAGE_UNITS_IN_BYTES[target]

    raise ValueError("Unsupported conversion units")

# --- NEW TOOLS ---

def list_files(directory="."):
    """Lists files in a given directory."""
    try:
        return os.listdir(directory)
    except Exception as e:
        return f"Error listing files: {str(e)}"

def read_file_content(file_path):
    """Reads the first 1000 characters of a text file."""
    try:
        with open(file_path, 'r') as f:
            return f.read(1000)
    except Exception as e:
        return f"Error reading file: {str(e)}"

def search_files(pattern, directory="."):
    """Searches for files matching a pattern (e.g., *.jpg)."""
    return glob.glob(os.path.join(directory, pattern), recursive=True)

def strip_html(value):
    value = re.sub(r"<[^>]+>", "", value or "")
    return unescape(value).strip()

def normalize_search_result_url(url):
    url = unescape(url)
    if url.startswith("//"):
        url = "https:" + url
    parsed = urllib.parse.urlparse(url)
    if parsed.netloc.endswith("duckduckgo.com"):
        redirected = urllib.parse.parse_qs(parsed.query).get("uddg")
        if redirected:
            return urllib.parse.unquote(redirected[0])
    return url

def extract_host_label(url):
    parsed = urllib.parse.urlparse(url)
    host = parsed.netloc.lower()
    if host.startswith("www."):
        host = host[4:]
    return host

def extract_duckduckgo_html_results(html):
    results = []
    pattern = re.compile(
        r'<a[^>]*class="result__a"[^>]*href="(?P<url>[^"]+)"[^>]*>(?P<title>.*?)</a>.*?'
        r'(?:<a[^>]*class="result__snippet"[^>]*>|<div[^>]*class="result__snippet"[^>]*>)(?P<snippet>.*?)</(?:a|div)>',
        re.S,
    )
    for match in pattern.finditer(html):
        url = normalize_search_result_url(match.group("url"))
        results.append({
            "title": strip_html(match.group("title")),
            "url": url,
            "host": extract_host_label(url),
            "snippet": strip_html(match.group("snippet")),
        })
    return results

def fetch_instant_answer(query):
    encoded_query = urllib.parse.quote(query)
    url = f"https://api.duckduckgo.com/?q={encoded_query}&format=json&no_html=1&no_redirect=1"
    with urllib.request.urlopen(url, timeout=15) as response:
        data = json.loads(response.read().decode())
    abstract = data.get("AbstractText", "").strip()
    return {
        "abstract": abstract,
        "source": data.get("AbstractSource", "").strip(),
        "url": data.get("AbstractURL", "").strip(),
    }

def fetch_html_search_results(query):
    res = requests.get(
        "https://html.duckduckgo.com/html/",
        params={"q": query},
        headers=SEARCH_HEADERS,
        timeout=20,
    )
    res.raise_for_status()
    return extract_duckduckgo_html_results(res.text)

def web_search(query):
    """Performs a web search to find current information."""
    try:
        query = (query or "").strip()
        if not query:
            return {"error": "Empty search query."}

        instant = fetch_instant_answer(query)
        results = fetch_html_search_results(query)[:5]

        if not instant["abstract"] and not results:
            return {"query": query, "summary": "No direct results found. Try a broader query.", "results": []}

        summary = instant["abstract"] or (results[0]["snippet"] if results else "")
        return {
            "query": query,
            "summary": summary,
            "instant_answer": instant["abstract"],
            "instant_source": instant["source"],
            "instant_url": instant["url"],
            "results": results,
        }
    except Exception as e:
        return {"error": f"Search error: {str(e)}"}

def tts_speak(text):
    """Speaks the given text out loud using the phone's text-to-speech engine."""
    run_termux_command(["termux-tts-speak", text])
    return f"Speaking: {text}"

def get_clipboard():
    """Returns the current content of the phone's clipboard."""
    return run_termux_command(["termux-clipboard-get"])

def set_clipboard(text):
    """Sets the phone's clipboard to the given text."""
    run_termux_command(["termux-clipboard-set", text])
    return f"Clipboard set to: {text}"

def open_url(url):
    """Opens a URL in the phone's default browser."""
    run_termux_command(["termux-open-url", url])
    return f"Opened URL: {url}"

def torch(enabled=True):
    """Turns the phone's torch (flashlight) on or off."""
    state = "on" if enabled else "off"
    run_termux_command(["termux-torch", state])
    return f"Torch turned {state}"

def get_wifi_info():
    """Returns information about the current WiFi connection."""
    output = run_termux_command(["termux-wifi-connectioninfo"])
    return parse_json_output(output)

# Dictionary of available tools for the agent
AVAILABLE_TOOLS = {
    "get_battery_status": get_battery_status,
    "vibrate": vibrate,
    "get_location": get_location,
    "send_sms": send_sms,
    "list_contacts": list_contacts,
    "set_brightness": set_brightness,
    "toast": toast,
    "get_time": get_time,
    "list_files": list_files,
    "read_file_content": read_file_content,
    "search_files": search_files,
    "web_search": web_search,
    "tts_speak": tts_speak,
    "get_clipboard": get_clipboard,
    "set_clipboard": set_clipboard,
    "open_url": open_url,
    "torch": torch,
    "get_wifi_info": get_wifi_info,
    "calculate": calculate,
    "get_utc_time": get_utc_time,
    "get_chronometer": get_chronometer,
    "date_time_reason": date_time_reason,
    "convert_units": convert_units,
}
