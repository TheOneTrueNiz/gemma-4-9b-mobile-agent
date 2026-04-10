import json
import time
import unittest

from backend.agent_runtime import AgentRuntime, clean_final_answer, extract_tool_json_candidate, finalize_user_answer


class FakeResponse:
    def __init__(self, content, status_code=200):
        self.status_code = status_code
        self._content = content

    def json(self):
        return {"content": self._content}


def fake_make_response(response, trace=None, mode="agentic", request_id=None, request_duration_ms=None):
    return {
        "response": response,
        "trace": trace or [],
        "mode": mode,
        "request_id": request_id,
        "request_duration_ms": request_duration_ms,
    }


def fake_repair_and_alias_json(raw):
    try:
        return json.loads(raw)
    except Exception:
        return None


def fake_prompt_builder(message, history):
    return f"User: {message}\nAssistant:"


class AgentRuntimeTests(unittest.TestCase):
    def test_extract_tool_json_candidate_stops_at_first_balanced_object(self):
        raw = '{"tool":"web_search","args":{"query":"termux"}}\nTool Result: {"snippet":"ok"}'
        self.assertEqual(
            extract_tool_json_candidate(raw),
            '{"tool":"web_search","args":{"query":"termux"}}',
        )

    def test_clean_final_answer_strips_protocol_residue(self):
        content = '"4"\n\nTool Result: {"time":"2023-10-27 10:00:00"}\nFINAL NOTE: answer directly.'
        self.assertEqual(clean_final_answer(content), "4")

    def test_finalize_user_answer_replaces_stalled_tool_json(self):
        content = '{"tool":"web_search","args":{"query":"2 plus 2"}}'
        self.assertIn("couldn't complete", finalize_user_answer(content, stalled=True))

    def make_runtime(self, request_completion, *, available_tools=None, verify_with_critic=None, validate_tool_call=None, max_steps=3):
        return AgentRuntime(
            available_tools=available_tools or {},
            validate_tool_call=validate_tool_call or (lambda tools, tool_name, args, message: (True, args, None)),
            repair_and_alias_json=fake_repair_and_alias_json,
            request_completion=request_completion,
            verify_with_critic=verify_with_critic or (lambda proposal: True),
            requires_critic_review=lambda tool_name: True,
            select_runtime_budget=lambda message: {
                "complexity": "medium",
                "hardware_profile": "test",
                "n_predict": 128,
                "max_steps": max_steps,
                "completion_timeout": 30,
            },
            format_actor_prompt=fake_prompt_builder,
            make_response=fake_make_response,
        )

    def test_low_risk_tool_bypasses_critic(self):
        calls = []
        runtime = AgentRuntime(
            available_tools={"web_search": lambda query: {"summary": query}},
            validate_tool_call=lambda tools, tool_name, args, message: (True, args, None),
            repair_and_alias_json=fake_repair_and_alias_json,
            request_completion=lambda prompt, **kwargs: FakeResponse('{"tool":"web_search","args":{"query":"termux"}}'),
            verify_with_critic=lambda proposal: calls.append(proposal) or True,
            requires_critic_review=lambda tool_name: False,
            select_runtime_budget=lambda message: {
                "complexity": "medium",
                "hardware_profile": "test",
                "n_predict": 128,
                "max_steps": 1,
                "completion_timeout": 30,
            },
            format_actor_prompt=fake_prompt_builder,
            make_response=fake_make_response,
        )

        payload = runtime.run(
            message="search termux",
            history=[],
            trace=[{"type": "request", "message": "search termux"}],
            request_id="req4",
            request_started_at=time.time(),
        )

        self.assertEqual(calls, [])
        critic_entries = [item for item in payload["trace"] if item.get("type") == "critic"]
        self.assertEqual(critic_entries[0]["status"], "bypassed")

    def test_runtime_budget_is_applied_to_request_completion(self):
        seen = {}

        def request_completion(prompt, *, n_predict, timeout):
            seen["n_predict"] = n_predict
            seen["timeout"] = timeout
            return FakeResponse("Direct answer.")

        runtime = AgentRuntime(
            available_tools={},
            validate_tool_call=lambda tools, tool_name, args, message: (True, args, None),
            repair_and_alias_json=fake_repair_and_alias_json,
            request_completion=request_completion,
            verify_with_critic=lambda proposal: True,
            requires_critic_review=lambda tool_name: True,
            select_runtime_budget=lambda message: {
                "complexity": "simple",
                "hardware_profile": "high",
                "n_predict": 96,
                "max_steps": 2,
                "completion_timeout": 45,
            },
            format_actor_prompt=fake_prompt_builder,
            make_response=fake_make_response,
        )

        payload = runtime.run(
            message="what is 2 plus 2?",
            history=[],
            trace=[{"type": "request", "message": "what is 2 plus 2?"}],
            request_id="req5",
            request_started_at=time.time(),
        )

        self.assertEqual(payload["response"], "Direct answer.")
        self.assertEqual(seen["n_predict"], 96)
        self.assertEqual(seen["timeout"], 45)
        budget_entries = [item for item in payload["trace"] if item.get("type") == "runtime_budget"]
        self.assertEqual(budget_entries[0]["complexity"], "simple")

    def test_direct_answer_reaches_answer_terminal_state(self):
        runtime = self.make_runtime(lambda prompt, **kwargs: FakeResponse("Final direct answer."))

        payload = runtime.run(
            message="hello",
            history=[],
            trace=[{"type": "request", "message": "hello"}],
            request_id="req1",
            request_started_at=time.time(),
        )

        self.assertEqual(payload["response"], "Final direct answer.")
        self.assertIsInstance(payload["request_duration_ms"], int)
        transitions = [item for item in payload["trace"] if item.get("type") == "state_transition"]
        self.assertEqual(transitions[-1]["to_state"], "ANSWER")
        cleanup_entries = [item for item in payload["trace"] if item.get("type") == "answer_cleanup"]
        self.assertEqual(cleanup_entries[0]["changed"], False)

    def test_mixed_tool_and_answer_content_recovers_clean_final_answer(self):
        runtime = self.make_runtime(
            lambda prompt, **kwargs: FakeResponse('"4"\n\nFINAL NOTE: If you need a tool, respond with valid raw JSON only.')
        )

        payload = runtime.run(
            message="what is 2 plus 2?",
            history=[],
            trace=[{"type": "request", "message": "what is 2 plus 2?"}],
            request_id="req6",
            request_started_at=time.time(),
        )

        self.assertEqual(payload["response"], "4")
        cleanup_entries = [item for item in payload["trace"] if item.get("type") == "answer_cleanup"]
        self.assertEqual(cleanup_entries[0]["changed"], True)

    def test_http_error_reaches_terminal_error_state(self):
        runtime = self.make_runtime(lambda prompt, **kwargs: FakeResponse("", status_code=503))

        payload = runtime.run(
            message="hello",
            history=[],
            trace=[{"type": "request", "message": "hello"}],
            request_id="req2",
            request_started_at=time.time(),
        )

        self.assertIn("Actor engine returned 503", payload["response"])
        transitions = [item for item in payload["trace"] if item.get("type") == "state_transition"]
        self.assertEqual(transitions[-1]["to_state"], "TERMINAL_ERROR")

    def test_repeated_tool_results_can_stall_cleanly(self):
        responses = iter([
            FakeResponse('{"tool":"lookup","args":{"query":"termux"}}'),
            FakeResponse('{"tool":"lookup","args":{"query":"android"}}'),
        ])
        runtime = self.make_runtime(
            lambda prompt, **kwargs: next(responses),
            available_tools={"lookup": lambda query: {"summary": query}},
            max_steps=2,
        )

        payload = runtime.run(
            message="research termux",
            history=[],
            trace=[{"type": "request", "message": "research termux"}],
            request_id="req3",
            request_started_at=time.time(),
        )

        self.assertIn("couldn't complete", payload["response"])
        transitions = [item for item in payload["trace"] if item.get("type") == "state_transition"]
        self.assertEqual(transitions[-1]["to_state"], "STALLED")
        tool_exec = [item for item in payload["trace"] if item.get("type") == "tool_execution"]
        self.assertEqual(len(tool_exec), 2)

    def test_simple_query_duplicate_tool_uses_direct_fallback(self):
        responses = iter([
            FakeResponse('{"tool":"web_search","args":{"query":"2 plus 2"}}'),
            FakeResponse('{"tool":"web_search","args":{"query":"2 plus 2"}}'),
            FakeResponse("4"),
        ])
        runtime = AgentRuntime(
            available_tools={"web_search": lambda query: {"summary": query}},
            validate_tool_call=lambda tools, tool_name, args, message: (True, args, None),
            repair_and_alias_json=fake_repair_and_alias_json,
            request_completion=lambda prompt, **kwargs: next(responses),
            verify_with_critic=lambda proposal: True,
            requires_critic_review=lambda tool_name: False,
            select_runtime_budget=lambda message: {
                "complexity": "simple",
                "hardware_profile": "test",
                "n_predict": 96,
                "max_steps": 2,
                "completion_timeout": 20,
            },
            format_actor_prompt=fake_prompt_builder,
            make_response=fake_make_response,
        )

        payload = runtime.run(
            message="what is 2 plus 2?",
            history=[],
            trace=[{"type": "request", "message": "what is 2 plus 2?"}],
            request_id="req7",
            request_started_at=time.time(),
        )

        self.assertEqual(payload["response"], "4")
        fallback_entries = [item for item in payload["trace"] if item.get("type") == "direct_fallback"]
        self.assertEqual(fallback_entries[-1]["status"], "ok")


if __name__ == "__main__":
    unittest.main()
