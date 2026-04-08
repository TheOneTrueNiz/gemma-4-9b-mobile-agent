import subprocess
import json
import os
import glob
import urllib.request
import urllib.parse

def run_termux_command(command):
    try:
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        return f"Error: {e.stderr}"

def get_battery_status():
    """Returns the battery status of the device."""
    output = run_termux_command(["termux-battery-status"])
    return json.loads(output)

def vibrate(duration_ms=200):
    """Vibrates the device for a specified duration."""
    run_termux_command(["termux-vibrate", "-d", str(duration_ms)])
    return f"Vibrated for {duration_ms}ms"

def get_location():
    """Returns the current GPS location."""
    output = run_termux_command(["termux-location"])
    return json.loads(output)

def send_sms(number, message):
    """Sends an SMS message to a specific number."""
    run_termux_command(["termux-sms-send", "-n", number, message])
    return f"SMS sent to {number}"

def list_contacts():
    """Lists phone contacts."""
    output = run_termux_command(["termux-contact-list"])
    return json.loads(output)

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

def web_search(query):
    """Performs a web search to find current information."""
    try:
        # Using a public SearXNG instance for quick search
        encoded_query = urllib.parse.quote(query)
        url = f"https://api.duckduckgo.com/?q={encoded_query}&format=json&no_html=1"
        with urllib.request.urlopen(url) as response:
            data = json.loads(response.read().decode())
            abstract = data.get("AbstractText", "")
            results = [r.get("Text") for r in data.get("RelatedTopics", []) if isinstance(r, dict) and "Text" in r][:3]
            if not abstract and not results:
                return "No direct results found. Try a broader query."
            return {"abstract": abstract, "related": results}
    except Exception as e:
        return f"Search error: {str(e)}"

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
    return json.loads(output)

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

