from datetime import datetime
import re

from backend.memory_policy import classify_memory_value


DEFAULT_RECALL_LIMIT = 5
PROMPT_RECALL_LIMIT = 3


def extract_recall_terms(query):
    base = (query or "").strip()
    terms = []
    if base:
        terms.append(base)
    token_candidates = re.findall(r"[A-Za-z0-9_'-]+", base.lower())
    seen = {base.lower()} if base else set()
    for token in token_candidates:
        if len(token) < 4 or token in seen:
            continue
        seen.add(token)
        terms.append(token)
        if len(terms) >= 5:
            break
    return terms


def parse_memory_timestamp(timestamp):
    if not timestamp:
        return datetime.min
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(timestamp, fmt)
        except ValueError:
            continue
    return datetime.min


def score_memory_result(query, result):
    wing, floor, content, timestamp = result
    haystack = f"{wing} {floor} {content}".lower()
    score = 0
    for term in extract_recall_terms(query):
        term_lower = term.lower()
        if not term_lower or term_lower not in haystack:
            continue
        score += 6 if " " in term_lower else 2
        if content.lower().startswith(term_lower):
            score += 2
        if term_lower == wing.lower() or term_lower == floor.lower():
            score += 1

    memory_type = classify_memory_value(content)
    type_bonus = {
        "profile": 5,
        "habit": 4,
        "preference": 3,
        "general": 1,
    }.get(memory_type, 0)
    score += type_bonus
    return score, parse_memory_timestamp(timestamp)


def rank_recall_results(query, results):
    ranked = []
    for result in results:
        score, parsed_time = score_memory_result(query, result)
        wing, floor, content, timestamp = result
        ranked.append((score, parsed_time, wing, floor, content, timestamp))
    ranked.sort(key=lambda item: (-item[0], -item[1].timestamp() if item[1] != datetime.min else float("inf"), item[2], item[3], item[4]))
    filtered = [(wing, floor, content, timestamp) for score, parsed_time, wing, floor, content, timestamp in ranked if score > 0]
    return filtered or results


def recall_results(memory_backend, query):
    combined = []
    seen = set()
    for term in extract_recall_terms(query):
        for item in memory_backend.recall(term):
            key = tuple(item)
            if key in seen:
                continue
            seen.add(key)
            combined.append(item)
    return rank_recall_results(query, combined)


def format_recall_results(results, *, limit=DEFAULT_RECALL_LIMIT):
    limited = list(results[:limit]) if limit is not None else list(results)
    if not limited:
        return "No relevant memories found."
    return "\n".join([f"[{w} > {f}] {c}" for w, f, c, t in limited])
