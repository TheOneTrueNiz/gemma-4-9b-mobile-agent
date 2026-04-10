import json
import time


def append_tool_feedback(prompt, assistant_content, tool_result, *, blocked=False, rejected=False):
    prompt += f"{assistant_content}\nTool Result: {json.dumps(tool_result)}\n"
    if blocked:
        prompt += "System: The requested tool action was blocked by policy. Choose a safer alternative tool or answer directly without the blocked action.\n"
    elif rejected:
        prompt += "System: The critic rejected that tool call. Choose a different safe tool or answer directly.\n"
    prompt += "Assistant:"
    return prompt


def append_json_repair_feedback(prompt, assistant_content):
    prompt += f"{assistant_content}\n"
    prompt += "System: Your last tool JSON was malformed or incomplete. If you need a tool, respond with valid raw JSON only. Otherwise answer directly in plain text.\n"
    prompt += "Assistant:"
    return prompt


def tool_call_signature(tool_name, args):
    normalized_items = []
    for key in sorted((args or {}).keys()):
        value = args[key]
        normalized_items.append((key, json.dumps(value, sort_keys=True)))
    return tool_name, tuple(normalized_items)


def trace_state_transition(trace, from_state, to_state, reason, *, step=None):
    entry = {
        "type": "state_transition",
        "from_state": from_state,
        "to_state": to_state,
        "reason": reason,
    }
    if step is not None:
        entry["step"] = step
    trace.append(entry)
    return to_state


def extract_tool_json_candidate(content):
    start = content.find("{")
    if start == -1:
        return None

    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(content)):
        char = content[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return content[start:index + 1]
    return content[start:].strip()


def clean_final_answer(content):
    text = (content or "").strip()
    if not text:
        return text

    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        upper = stripped.upper()
        if not stripped:
            if lines and lines[-1]:
                lines.append("")
            continue
        if stripped.startswith("Tool Result:"):
            continue
        if upper.startswith("SYSTEM:"):
            continue
        if upper.startswith("FINAL NOTE:"):
            continue
        lines.append(stripped)

    cleaned = "\n".join(line for line in lines if line != "" or len(lines) == 1).strip()
    if cleaned.startswith('"') and cleaned.endswith('"'):
        try:
            unwrapped = json.loads(cleaned)
            if isinstance(unwrapped, str):
                cleaned = unwrapped.strip()
        except Exception:
            pass
    return cleaned or text


def looks_like_tool_call(content):
    text = (content or "").strip()
    if not text.startswith("{"):
        return False
    try:
        parsed = json.loads(text)
    except Exception:
        return False
    return isinstance(parsed, dict) and isinstance(parsed.get("tool"), str)


def finalize_user_answer(content, *, stalled=False):
    cleaned = clean_final_answer(content)
    if looks_like_tool_call(cleaned):
        if stalled:
            return "I couldn't complete that tool workflow cleanly. Please try again with a narrower request."
        return "I couldn't turn that into a clean final answer. Please try again."
    return cleaned


class AgentRuntime:
    def __init__(
        self,
        *,
        available_tools,
        validate_tool_call,
        repair_and_alias_json,
        request_completion,
        verify_with_critic,
        requires_critic_review,
        select_runtime_budget,
        format_actor_prompt,
        make_response,
    ):
        self.available_tools = available_tools
        self.validate_tool_call = validate_tool_call
        self.repair_and_alias_json = repair_and_alias_json
        self.request_completion = request_completion
        self.verify_with_critic = verify_with_critic
        self.requires_critic_review = requires_critic_review
        self.select_runtime_budget = select_runtime_budget
        self.format_actor_prompt = format_actor_prompt
        self.make_response = make_response

    def run(self, *, message, history, trace, request_id, request_started_at):
        runtime_budget = self.select_runtime_budget(message)
        current_prompt = self.format_actor_prompt(message, history)
        last_content = ""
        seen_tool_calls = set()
        state = "INIT"
        trace.append({
            "type": "runtime_budget",
            "complexity": runtime_budget["complexity"],
            "hardware_profile": runtime_budget["hardware_profile"],
            "n_predict": runtime_budget["n_predict"],
            "max_steps": runtime_budget["max_steps"],
            "completion_timeout": runtime_budget["completion_timeout"],
        })
        trace_state_transition(trace, "REQUEST_RECEIVED", state, "agentic_path_selected")

        for step in range(runtime_budget["max_steps"]):
            step_number = step + 1
            state = trace_state_transition(trace, state, "ACTOR_THINK", "request_completion", step=step_number)
            try:
                response = self.request_completion(
                    current_prompt,
                    n_predict=runtime_budget["n_predict"],
                    timeout=runtime_budget["completion_timeout"],
                )
            except Exception as exc:
                trace_state_transition(trace, state, "TERMINAL_ERROR", "exception", step=step_number)
                return self.make_response(
                    f"Error: {str(exc)}",
                    trace=trace + [{"type": "exception", "step": step_number, "message": str(exc)}],
                    request_id=request_id,
                    request_duration_ms=self._request_duration_ms(request_started_at),
                )

            if response.status_code != 200:
                trace_state_transition(trace, state, "TERMINAL_ERROR", "actor_http_error", step=step_number)
                return self.make_response(
                    f"Error: Actor engine returned {response.status_code}",
                    trace=trace + [{
                        "type": "actor",
                        "step": step_number,
                        "status": "http_error",
                        "status_code": response.status_code,
                    }],
                    request_id=request_id,
                    request_duration_ms=self._request_duration_ms(request_started_at),
                )

            content = response.json()["content"].strip()
            last_content = content
            trace.append({
                "type": "actor",
                "step": step_number,
                "status": "ok",
                "content_preview": content[:240],
            })
            state = trace_state_transition(trace, state, "ACTOR_RESPONSE", "completion_received", step=step_number)

            if "{" in content:
                state = trace_state_transition(trace, state, "TOOL_PARSE", "json_candidate_detected", step=step_number)
                tool_json_raw = extract_tool_json_candidate(content)
                tool_call = self.repair_and_alias_json(tool_json_raw)
                trace.append({
                    "type": "tool_parse",
                    "step": step_number,
                    "raw": tool_json_raw[:240],
                    "parsed": tool_call,
                })

                if tool_call is None:
                    trace.append({
                        "type": "tool_parse",
                        "step": step_number,
                        "status": "malformed",
                    })
                    state = trace_state_transition(trace, state, "ACTOR_THINK", "json_repair_feedback", step=step_number)
                    current_prompt = append_json_repair_feedback(current_prompt, content)
                    last_content = content
                    continue

                tool_name = tool_call.get("tool")
                args = tool_call.get("args", {})
                if self.requires_critic_review(tool_name):
                    state = trace_state_transition(trace, state, "CRITIC_REVIEW", "tool_call_parsed", step=step_number)
                    if not self.verify_with_critic(json.dumps(tool_call)):
                        trace.append({
                            "type": "critic",
                            "step": step_number,
                            "status": "rejected",
                            "proposal": tool_call,
                        })
                        rejection = "Critic rejected tool call."
                        state = trace_state_transition(trace, state, "ACTOR_THINK", "critic_rejected", step=step_number)
                        current_prompt = append_tool_feedback(current_prompt, content, rejection, rejected=True)
                        last_content = rejection
                        continue

                    trace.append({
                        "type": "critic",
                        "step": step_number,
                        "status": "approved",
                    })
                else:
                    trace.append({
                        "type": "critic",
                        "step": step_number,
                        "status": "bypassed",
                        "reason": "low_risk_tool",
                    })
                state = trace_state_transition(trace, state, "TOOL_VALIDATE", "critic_approved", step=step_number)
                is_valid, args, validation_error = self.validate_tool_call(
                    self.available_tools,
                    tool_name,
                    args,
                    message,
                )

                if not is_valid:
                    result = f"Blocked tool call: {validation_error}"
                    trace.append({
                        "type": "tool_execution",
                        "step": step_number,
                        "tool": tool_name,
                        "args": args,
                        "status": "blocked",
                        "reason": validation_error,
                    })
                    state = trace_state_transition(trace, state, "ACTOR_THINK", "tool_blocked", step=step_number)
                    current_prompt = append_tool_feedback(current_prompt, content, result, blocked=True)
                    last_content = result
                    continue

                signature = tool_call_signature(tool_name, args)
                if signature in seen_tool_calls:
                    result = f"Blocked repeated tool call: {tool_name}"
                    trace.append({
                        "type": "tool_execution",
                        "step": step_number,
                        "tool": tool_name,
                        "args": args,
                        "status": "blocked_repeat",
                        "reason": "duplicate_tool_call",
                    })
                    state = trace_state_transition(trace, state, "ACTOR_THINK", "duplicate_tool_call", step=step_number)
                    current_prompt = append_tool_feedback(current_prompt, content, result, blocked=True)
                    last_content = result
                    continue

                if tool_name in self.available_tools:
                    state = trace_state_transition(trace, state, "TOOL_EXECUTE", "validated_tool_call", step=step_number)
                    seen_tool_calls.add(signature)
                    try:
                        result = self.available_tools[tool_name](**args)
                    except Exception as tool_error:
                        result = f"Error: {str(tool_error)}"
                    trace.append({
                        "type": "tool_execution",
                        "step": step_number,
                        "tool": tool_name,
                        "args": args,
                        "status": "ok" if not str(result).startswith("Error:") else "error",
                        "result_preview": str(result)[:240],
                    })
                    state = trace_state_transition(trace, state, "ACTOR_THINK", "tool_result_available", step=step_number)
                    current_prompt = append_tool_feedback(current_prompt, content, result)
                    continue

            trace_state_transition(trace, state, "ANSWER", "direct_response", step=step_number)
            cleaned_content = finalize_user_answer(content, stalled=False)
            trace.append({
                "type": "answer_cleanup",
                "step": step_number,
                "changed": cleaned_content != content,
                "cleaned_preview": cleaned_content[:240],
            })
            return self.make_response(
                cleaned_content,
                trace=trace,
                request_id=request_id,
                request_duration_ms=self._request_duration_ms(request_started_at),
            )

        trace_state_transition(trace, state, "STALLED", "max_steps_reached", step=runtime_budget["max_steps"])
        cleaned_last_content = finalize_user_answer(last_content, stalled=True)
        trace.append({
            "type": "answer_cleanup",
            "step": runtime_budget["max_steps"],
            "changed": cleaned_last_content != last_content,
            "cleaned_preview": cleaned_last_content[:240],
        })
        return self.make_response(
            cleaned_last_content,
            trace=trace,
            request_id=request_id,
            request_duration_ms=self._request_duration_ms(request_started_at),
        )

    def _request_duration_ms(self, request_started_at):
        return int((time.time() - request_started_at) * 1000)
