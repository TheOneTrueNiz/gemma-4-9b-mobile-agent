import os
import re


def read_meminfo_kb():
    data = {}
    try:
        with open("/proc/meminfo", "r", encoding="utf-8") as handle:
            for line in handle:
                if ":" not in line:
                    continue
                key, value = line.split(":", 1)
                parts = value.strip().split()
                if not parts:
                    continue
                try:
                    data[key] = int(parts[0])
                except ValueError:
                    continue
    except OSError:
        return {}
    return data


def detect_hardware_profile():
    meminfo = read_meminfo_kb()
    total_gb = meminfo.get("MemTotal", 0) / (1024 * 1024)
    available_gb = meminfo.get("MemAvailable", 0) / (1024 * 1024)
    cpu_count = os.cpu_count() or 1

    if total_gb >= 10 and cpu_count >= 8 and available_gb >= 2:
        return "high"
    if total_gb >= 6 and cpu_count >= 6 and available_gb >= 1:
        return "medium"
    return "constrained"


def classify_request_complexity(message):
    lowered = (message or "").strip().lower()
    if not lowered:
        return "simple"

    token_count = len(re.findall(r"\w+", lowered))
    complexity_markers = (
        "compare",
        "analyze",
        "explain why",
        "strategy",
        "plan",
        "research",
        "multiple",
        "step by step",
        "tradeoff",
        "architecture",
        "design",
    )
    simple_markers = (
        "what is",
        "what's",
        "who is",
        "when is",
        "battery",
        "time",
        "date",
    )

    if token_count <= 8 and any(marker in lowered for marker in simple_markers):
        return "simple"
    if token_count >= 18 or any(marker in lowered for marker in complexity_markers):
        return "complex"
    return "medium"


def select_runtime_budget(message, hardware_profile=None):
    profile = hardware_profile or detect_hardware_profile()
    complexity = classify_request_complexity(message)

    budgets = {
        "high": {
            "simple": {"n_predict": 96, "max_steps": 2, "completion_timeout": 45},
            "medium": {"n_predict": 192, "max_steps": 3, "completion_timeout": 75},
            "complex": {"n_predict": 320, "max_steps": 4, "completion_timeout": 120},
        },
        "medium": {
            "simple": {"n_predict": 96, "max_steps": 2, "completion_timeout": 45},
            "medium": {"n_predict": 160, "max_steps": 3, "completion_timeout": 75},
            "complex": {"n_predict": 256, "max_steps": 3, "completion_timeout": 105},
        },
        "constrained": {
            "simple": {"n_predict": 72, "max_steps": 2, "completion_timeout": 35},
            "medium": {"n_predict": 128, "max_steps": 2, "completion_timeout": 60},
            "complex": {"n_predict": 192, "max_steps": 3, "completion_timeout": 90},
        },
    }

    budget = dict(budgets.get(profile, budgets["constrained"])[complexity])
    budget["hardware_profile"] = profile
    budget["complexity"] = complexity
    return budget
