import re


GENERIC_REJECTIONS = (
    "ok",
    "okay",
    "thanks",
    "thank you",
    "got it",
    "hello",
    "hi",
    "test",
)

TRANSIENT_PATTERNS = (
    "battery",
    "temperature",
    "news",
    "today",
    "tomorrow",
    "current time",
    "right now",
    "latest",
)

PREFERENCE_MARKERS = (
    "likes ",
    "prefers ",
    "favorite ",
    "loves ",
    "hates ",
)

PROFILE_MARKERS = (
    "name is ",
    "my name is ",
    "birthday",
    "allergic",
    "family",
    "project",
    "schedule",
)


def normalize_memory_text(text):
    normalized = re.sub(r"\s+", " ", (text or "")).strip()
    return normalized.strip("\"'")


def normalize_memory_location(wing, floor):
    clean_wing = normalize_memory_text(wing) or "World"
    clean_floor = normalize_memory_text(floor) or "General"
    return clean_wing[:48], clean_floor[:48]


def classify_memory_value(text):
    lowered = text.lower()
    if any(marker in lowered for marker in PREFERENCE_MARKERS):
        return "preference"
    if any(marker in lowered for marker in PROFILE_MARKERS):
        return "profile"
    if any(word in lowered for word in ("always", "every ", "usually", "works on", "uses ")):
        return "habit"
    return "general"


def assess_memory_candidate(fact, wing="World", floor="General"):
    normalized_fact = normalize_memory_text(fact)
    normalized_wing, normalized_floor = normalize_memory_location(wing, floor)

    if not normalized_fact:
        return {
            "accepted": False,
            "reason": "empty_fact",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    lowered = normalized_fact.lower()
    if len(normalized_fact) < 12:
        return {
            "accepted": False,
            "reason": "too_short",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    if lowered in GENERIC_REJECTIONS:
        return {
            "accepted": False,
            "reason": "generic_acknowledgement",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    if normalized_fact.endswith("?"):
        return {
            "accepted": False,
            "reason": "question_not_memory",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    if lowered.startswith(("send ", "open ", "search ", "call ", "text ", "remember this:")):
        return {
            "accepted": False,
            "reason": "imperative_not_memory",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    if any(pattern in lowered for pattern in TRANSIENT_PATTERNS):
        return {
            "accepted": False,
            "reason": "transient_fact",
            "fact": normalized_fact,
            "wing": normalized_wing,
            "floor": normalized_floor,
            "memory_type": None,
        }

    return {
        "accepted": True,
        "reason": "accepted",
        "fact": normalized_fact,
        "wing": normalized_wing,
        "floor": normalized_floor,
        "memory_type": classify_memory_value(normalized_fact),
    }
