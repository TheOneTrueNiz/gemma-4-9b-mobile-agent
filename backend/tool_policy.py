READ_ONLY_TOOLS = {
    "get_battery_status",
    "get_location",
    "list_contacts",
    "get_time",
    "list_files",
    "read_file_content",
    "search_files",
    "web_search",
    "get_clipboard",
    "get_wifi_info",
    "recall",
}

LOW_IMPACT_TOOLS = {
    "tts_speak",
    "toast",
    "vibrate",
}

USER_EFFECT_TOOLS = {
    "set_brightness",
    "set_clipboard",
    "open_url",
    "torch",
    "remember",
}

HIGH_RISK_TOOLS = {
    "send_sms",
}

TOOL_KEYWORDS = {
    "get_battery_status": {"battery", "charge", "power"},
    "get_location": {"location", "where", "gps", "map"},
    "list_contacts": {"contacts", "contact", "phonebook"},
    "get_time": {"time", "date", "clock"},
    "list_files": {"files", "folder", "directory", "list"},
    "read_file_content": {"read", "file", "content", "open"},
    "search_files": {"find", "search", "file", "pattern"},
    "web_search": {"search", "web", "news", "look up", "find"},
    "tts_speak": {"say", "speak", "read aloud", "voice"},
    "get_clipboard": {"clipboard", "copied", "paste"},
    "set_clipboard": {"clipboard", "copy"},
    "open_url": {"open", "url", "link", "website", "browser"},
    "torch": {"torch", "flashlight", "light"},
    "set_brightness": {"brightness", "screen", "dim"},
    "vibrate": {"vibrate", "buzz"},
    "toast": {"toast", "popup", "notify"},
    "send_sms": {"sms", "text", "message"},
    "remember": {"remember", "save", "store", "note"},
    "recall": {"remember", "recall", "memory", "know about"},
    "get_wifi_info": {"wifi", "wi-fi", "network", "ssid"},
    "calculate": {"calculate", "compute", "solve", "math", "sum", "plus", "minus", "times", "divide"},
}

DEFAULT_TOOLSET = {
    "web_search",
    "recall",
    "remember",
    "get_time",
}


def get_tool_risk(tool_name):
    if tool_name in HIGH_RISK_TOOLS:
        return "high_risk"
    if tool_name in USER_EFFECT_TOOLS:
        return "user_effect"
    if tool_name in LOW_IMPACT_TOOLS:
        return "low_impact"
    if tool_name in READ_ONLY_TOOLS:
        return "read_only"
    return "unknown"


def requires_critic_review(tool_name):
    return get_tool_risk(tool_name) in {"high_risk", "user_effect", "unknown"}


def select_relevant_tools(message, available_tools):
    lowered = (message or "").lower()
    selected = set(DEFAULT_TOOLSET)
    for tool_name in available_tools.keys():
        keywords = TOOL_KEYWORDS.get(tool_name, set())
        if any(keyword in lowered for keyword in keywords):
            selected.add(tool_name)
    return {
        tool_name: available_tools[tool_name]
        for tool_name in available_tools.keys()
        if tool_name in selected
    }
