import subprocess
import json
import os
import glob
import re
import urllib.request
import urllib.parse
from html import unescape

import requests

SEARCH_HEADERS = {
    "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) GemmaMobileAgent/1.0"
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
    from datetime import datetime
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

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
    "get_wifi_info": get_wifi_info
}
