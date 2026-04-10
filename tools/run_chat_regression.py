import argparse
import json
import sys
from pathlib import Path

import requests


def load_cases(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def run_case(base_url, case):
    response = requests.post(
        f"{base_url.rstrip('/')}/chat",
        json={"message": case["message"], "history": []},
        timeout=90,
    )
    response.raise_for_status()
    payload = response.json()

    failures = []
    if case.get("expected_mode") and payload.get("mode") != case["expected_mode"]:
        failures.append(f"mode expected {case['expected_mode']} got {payload.get('mode')}")

    body = payload.get("response", "")
    for expected in case.get("response_contains", []):
        if expected not in body:
            failures.append(f"response missing substring: {expected}")

    trace_types = [item.get("type") for item in payload.get("trace", [])]
    for expected_type in case.get("trace_types", []):
        if expected_type not in trace_types:
            failures.append(f"trace missing type: {expected_type}")

    first_trace = next((item for item in payload.get("trace", []) if item.get("type") != "request"), {})
    expected_trace = case.get("expected_first_trace", {})
    for key, expected_value in expected_trace.items():
        actual_value = first_trace.get(key)
        if actual_value != expected_value:
            failures.append(f"first trace {key} expected {expected_value!r} got {actual_value!r}")

    return payload, failures


def main(argv=None):
    parser = argparse.ArgumentParser(description="Run live chat regression fixtures against the local backend")
    parser.add_argument(
        "--fixture",
        default="tests/fixtures/chat_regressions.json",
        help="Fixture JSON path",
    )
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:1337",
        help="Backend base URL",
    )
    args = parser.parse_args(argv)

    cases = load_cases(args.fixture)
    had_failures = False

    for case in cases:
        payload, failures = run_case(args.base_url, case)
        status = "PASS" if not failures else "FAIL"
        print(f"[{status}] {case['name']}: {case['message']}")
        if failures:
            had_failures = True
            for failure in failures:
                print(f"  - {failure}")
        else:
            print(f"  - mode={payload.get('mode')} request_id={payload.get('request_id')}")

    return 1 if had_failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
