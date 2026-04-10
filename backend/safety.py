import inspect
import os
import re


PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SAFE_BROWSE_ROOTS = [
    PROJECT_ROOT,
    os.path.expanduser("~"),
    os.path.expanduser("~/storage"),
    "/sdcard",
]
DENIED_PATH_PREFIXES = (
    "/proc",
    "/sys",
    "/dev",
    "/system",
    "/vendor",
    "/odm",
    "/apex",
    "/linkerconfig",
    "/etc",
    "/data",
)
MAX_TOOL_ARG_LENGTH = 1000


def coerce_int(value):
    if isinstance(value, bool):
        return None
    try:
        return int(value)
    except Exception:
        return None


def normalize_path(value):
    if not isinstance(value, str) or not value.strip():
        return None
    expanded = os.path.expanduser(value.strip())
    return os.path.abspath(expanded)


def is_safe_path(path):
    normalized = normalize_path(path)
    if not normalized:
        return False
    if any(
        normalized == root or normalized.startswith(root + os.sep)
        for root in SAFE_BROWSE_ROOTS
    ):
        return True
    if normalized.startswith(DENIED_PATH_PREFIXES):
        return False
    return False


def normalize_tool_args(tool_name, args):
    normalized_args = dict(args)
    if tool_name in {"list_files", "search_files"} and "path" in normalized_args and "directory" not in normalized_args:
        normalized_args["directory"] = normalized_args.pop("path")
    if tool_name == "read_file_content" and "path" in normalized_args and "file_path" not in normalized_args:
        normalized_args["file_path"] = normalized_args.pop("path")
    return normalized_args


def validate_tool_call(available_tools, tool_name, args, user_message):
    if tool_name not in available_tools:
        return False, args, f"Unknown tool: {tool_name}"

    normalized_args = normalize_tool_args(tool_name, args)
    signature = inspect.signature(available_tools[tool_name])
    valid_args = set(signature.parameters.keys())
    extra_args = sorted(set(normalized_args.keys()) - valid_args)
    if extra_args:
        return False, normalized_args, f"Unexpected args for {tool_name}: {', '.join(extra_args)}"

    for key, value in normalized_args.items():
        if isinstance(value, str) and len(value) > MAX_TOOL_ARG_LENGTH:
            return False, normalized_args, f"Argument '{key}' is too long."

    if tool_name == "set_brightness":
        level = coerce_int(normalized_args.get("level"))
        if level is None or level < 0 or level > 255:
            return False, normalized_args, "Brightness level must be between 0 and 255."

    if tool_name == "vibrate":
        duration_ms = coerce_int(normalized_args.get("duration_ms", 200))
        if duration_ms is None or duration_ms < 0 or duration_ms > 5000:
            return False, normalized_args, "Vibration duration must be between 0 and 5000ms."

    if tool_name == "send_sms":
        number = str(normalized_args.get("number", "")).strip()
        message = str(normalized_args.get("message", "")).strip()
        lowered = user_message.lower()
        if not number or not message:
            return False, normalized_args, "SMS requires both number and message."
        if not re.fullmatch(r"[0-9+\-\s()]{3,20}", number):
            return False, normalized_args, "SMS number format is invalid."
        if not any(token in lowered for token in ("sms", "text", "message", "send")):
            return False, normalized_args, "SMS sending requires explicit user intent."

    if tool_name in {"list_files", "read_file_content", "search_files"}:
        path_arg = normalized_args.get("directory") or normalized_args.get("path") or normalized_args.get("file_path") or "."
        if not is_safe_path(path_arg):
            return False, normalized_args, f"Path not allowed: {path_arg}"

    if tool_name == "open_url":
        url = str(normalized_args.get("url", "")).strip()
        if not re.match(r"^https?://", url):
            return False, normalized_args, "Only http and https URLs are allowed."

    if tool_name == "web_search":
        query = str(normalized_args.get("query", "")).strip()
        if not query:
            return False, normalized_args, "Search query cannot be empty."

    if tool_name == "calculate":
        expression = str(normalized_args.get("expression", "")).strip()
        if not expression:
            return False, normalized_args, "Calculation expression cannot be empty."

    if tool_name == "tts_speak":
        text = str(normalized_args.get("text", "")).strip()
        if not text:
            return False, normalized_args, "Speech text cannot be empty."

    if tool_name == "set_clipboard":
        text = str(normalized_args.get("text", ""))
        if len(text) > MAX_TOOL_ARG_LENGTH:
            return False, normalized_args, "Clipboard text is too long."

    return True, normalized_args, ""
