from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from fastapi.responses import FileResponse
from pydantic import BaseModel
import sys
import os
import json
import subprocess
import time
import requests
import re
import uuid

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

# Allow importing the sibling editable MemSpire repo even when another caller
# prepends the Termux home directory and creates a namespace-package collision.
MEMSPIRE_REPO = os.path.abspath(os.path.join(PROJECT_ROOT, "..", "memspire"))
if os.path.exists(os.path.join(MEMSPIRE_REPO, "memspire", "__init__.py")) and MEMSPIRE_REPO not in sys.path:
    sys.path.insert(0, MEMSPIRE_REPO)

from tools.phone_tools import AVAILABLE_TOOLS
from backend.router import router
from backend.safety import validate_tool_call

# --- SMOLCLAW PATTERN: ARGUMENT ALIASING & REPAIR ---
ARG_ALIASES = {
    "directory": "path",
    "folder": "path",
    "dir": "path",
    "file": "file_path",
    "filename": "file_path",
    "msg": "message",
    "txt": "message",
    "content": "text",
    "val": "value",
    "brightness_level": "level",
    "duration": "duration_ms",
    "ms": "duration_ms",
}

def repair_and_alias_json(json_str):
    """Clean and fix common LLM JSON hallucinations."""
    try:
        # 1. Basic cleaning (remove markdown blocks if present)
        json_str = re.sub(r"```json\s*|\s*```", "", json_str).strip()
        data = json.loads(json_str)
        
        # Normalize tool name key
        tool_name = data.get("tool") or data.get("tool_name") or data.get("name")
        # Normalize args key
        args = data.get("args") or data.get("params") or data.get("arguments") or {}
        
        if not tool_name: return None
        
        # 2. Argument Aliasing
        new_args = {}
        for k, v in args.items():
            # Use alias if it exists, otherwise use original key
            clean_key = ARG_ALIASES.get(k.lower(), k)
            new_args[clean_key] = v
            
        return {"tool": tool_name, "args": new_args}
    except Exception as e:
        print(f"🛠️ JSON Repair failed: {e}")
        return None

# --- MEMORY MANAGER (MEMSPIRE) ---
from memspire import MemSpire
MEMORY_DB = os.path.abspath(os.path.join(os.path.dirname(__file__), "../models/memspire.db"))
memory = MemSpire(MEMORY_DB)

def remember_tool(fact, wing="World", floor="General"):
    """Saves a fact into the hierarchical memory (MemSpire). 
    Args:
        fact: The information to remember.
        wing: High-level domain (e.g., Identity, Projects, World).
        floor: Category within the wing (e.g., Family, Coding).
    """
    return memory.add_fact(wing, floor, fact)

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


def rank_recall_results(query, results):
    lowered = (query or "").lower()
    ranked = []
    for wing, floor, content, timestamp in results:
        haystack = f"{wing} {floor} {content}".lower()
        score = 0
        for term in extract_recall_terms(query):
            term_lower = term.lower()
            if term_lower and term_lower in haystack:
                score += 3 if " " in term_lower else 1
        ranked.append((score, timestamp, wing, floor, content))
    ranked.sort(key=lambda item: (-item[0], item[1]), reverse=False)
    return [(wing, floor, content, timestamp) for score, timestamp, wing, floor, content in ranked if score > 0] or results

def recall_tool(query):
    """Searches the hierarchical memory for relevant information."""
    combined = []
    seen = set()
    for term in extract_recall_terms(query):
        for item in memory.recall(term):
            key = tuple(item)
            if key in seen:
                continue
            seen.add(key)
            combined.append(item)
    results = rank_recall_results(query, combined)
    if not results: return "No relevant memories found."
    return "\n".join([f"[{w} > {f}] {c}" for w, f, c, t in results])

AVAILABLE_TOOLS["remember"] = remember_tool
AVAILABLE_TOOLS["recall"] = recall_tool


# --- NATIVE LLAMA-SERVER MANAGEMENT (MAS) ---
MODELS = {
    "actor": {
        "path": os.path.abspath(os.path.join(os.path.dirname(__file__), "../models/gemma-4-e4b-it-q4_k_m.gguf")),
        "port": "8888",
        "threads": "4" 
    }
}
SERVER_BIN = os.path.abspath(os.path.join(os.path.dirname(__file__), "./llama-server"))
actor_process = None
actor_online = False

def start_engine(name, config):
    print(f"--- Launching {name.upper()} Engine ---")
    cmd = [
        SERVER_BIN,
        "-m", config["path"],
        "-c", "4096",
        "--port", config["port"],
        "-t", config["threads"],
        "-b", "512",
        "--log-disable",
        "--cache-type-k", "q4_0",
        "--cache-type-v", "q4_0"
    ]
    process = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    
    # Ready check
    for i in range(120):
        try:
            res = requests.get(f"http://localhost:{config['port']}/health", timeout=1)
            if res.status_code == 200:
                print(f"✅ {name.upper()} is ONLINE")
                return process
        except:
            time.sleep(1)
    return process


def ensure_actor_engine():
    global actor_process, actor_online
    if actor_online:
        return True

    if not os.path.exists(SERVER_BIN):
        print(f"⚠️ llama-server binary not found at {SERVER_BIN}")
        return False

    if not os.path.exists(MODELS["actor"]["path"]):
        print(f"⚠️ actor model not found at {MODELS['actor']['path']}")
        return False

    actor_process = start_engine("actor", MODELS["actor"])
    try:
        res = requests.get(f"http://localhost:{MODELS['actor']['port']}/health", timeout=2)
        actor_online = res.status_code == 200
    except Exception:
        actor_online = False
    return actor_online


def stop_actor_engine():
    global actor_process, actor_online
    if actor_process and actor_process.poll() is None:
        actor_process.terminate()
        try:
            actor_process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            actor_process.kill()
    actor_process = None
    actor_online = False


@asynccontextmanager
async def lifespan(app: FastAPI):
    ensure_actor_engine()
    yield
    stop_actor_engine()


app = FastAPI(lifespan=lifespan)
app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")


@app.get("/")
async def read_index():
    return FileResponse(os.path.join(os.path.dirname(__file__), "static/index.html"))


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "actor_online": ensure_actor_engine(),
        "actor_model": os.path.basename(MODELS["actor"]["path"]),
    }

class ChatRequest(BaseModel):
    message: str
    history: list = []


def summarize_trace(trace):
    lines = []
    for item in trace or []:
        item_type = item.get("type", "unknown")
        if item_type == "request":
            lines.append(f"request: {item.get('message', '')}")
        elif item_type in {"fast_path_tool", "tool_execution"}:
            lines.append(
                f"{item_type}: {item.get('tool')} status={item.get('status')} duration_ms={item.get('duration_ms', 'n/a')}"
            )
        elif item_type == "fast_path_search":
            lines.append(
                f"fast_path_search: query={item.get('query', '')} status={item.get('status')} results={item.get('result_count', 0)}"
            )
        elif item_type == "actor":
            lines.append(f"actor step {item.get('step')}: {item.get('status')}")
        elif item_type == "critic":
            lines.append(f"critic step {item.get('step')}: {item.get('status')}")
        elif item_type == "tool_parse":
            parsed = item.get("parsed") or {}
            lines.append(f"tool_parse step {item.get('step')}: {parsed.get('tool', 'unknown')}")
        elif item_type == "exception":
            lines.append(f"exception: {item.get('message', '')}")
        else:
            lines.append(f"{item_type}: {json.dumps(item, ensure_ascii=False)}")
    return lines


def make_response(response, trace=None, mode="agentic", request_id=None, request_duration_ms=None):
    active_trace = trace or []
    return {
        "response": response,
        "trace": active_trace,
        "trace_summary": summarize_trace(active_trace),
        "mode": mode,
        "request_id": request_id,
        "request_duration_ms": request_duration_ms,
    }

def format_actor_prompt(message, history):
    # Fetch relevant memories from MemSpire
    relevant_memories = recall_tool(message)
    mem_str = relevant_memories if "No relevant memories" not in relevant_memories else ""

    tools_info = []
    for name, func in AVAILABLE_TOOLS.items():
        import inspect
        sig = inspect.signature(func)
        args_str = ", ".join(sig.parameters.keys())
        tools_info.append(f"  - {name}({args_str}): {func.__doc__}")

    tools_list = "\n".join(tools_info)

    prompt = f"System: You are the ACTOR, a powerful AI assistant running locally on a phone. Your goal is to help the user using available tools. Output JSON for tool calls.\n"
    prompt += "You have a hierarchical memory system called MemSpire (Wing > Floor > Cell).\n"
    prompt += "Never access sensitive system paths like /proc, /sys, /dev, /system, /vendor, /odm, /apex, /etc, or /data.\n"
    prompt += "Use send_sms only when the user explicitly asks to send a text message.\n"
    prompt += "IMPORTANT: Do NOT output code blocks (```). Do NOT explain your plan. ONLY output raw JSON when you need a tool.\n"
    prompt += "JSON FORMAT: {\"tool\": \"tool_name\", \"args\": {\"arg_name\": \"value\"}}\n\n"
    prompt += "EXAMPLE MULTI-STEP:\nUser: What's the weather like here?\nAssistant: {\"tool\": \"get_location\", \"args\": {}}\nTool Result: {\"latitude\": 40.7, \"longitude\": -74.0}\nAssistant: {\"tool\": \"web_search\", \"args\": {\"query\": \"weather in New York\"}}\n\n"
    prompt += f"AVAILABLE TOOLS:\n{tools_list}\n\n"

    if mem_str:
        prompt += f"RELEVANT MEMORIES (MemSpire):\n{mem_str}\n\n"
    for h in history:
        prompt += f"User: {h['user']}\nAssistant: {h['assistant']}\n"
    prompt += f"User: {message}\nAssistant:"
    return prompt


def verify_with_critic(proposal):
    """SmolClaw pattern: Proposer-Critic Split (Running on same engine)"""
    print(f"🔍 Critic reviewing proposal: {proposal[:50]}...")
    if not ensure_actor_engine():
        return False
    
    critic_prompt = "System: You are the CRITIC. Your job is to approve or reject tool calls. Reject only if harmful or logically broken.\n\n"
    critic_prompt += "EXAMPLES:\n"
    critic_prompt += "Proposal: {\"tool\": \"vibrate\", \"args\": {\"duration_ms\": 200}} -> Decision: APPROVED\n"
    critic_prompt += "Proposal: {\"tool\": \"send_sms\", \"args\": {\"number\": \"123\", \"message\": \"hi\"}} -> Decision: APPROVED\n"
    critic_prompt += "Proposal: {\"tool\": \"list_files\", \"args\": {\"path\": \"/etc/shadow\"}} -> Decision: REJECTED: Security risk\n\n"
    critic_prompt += f"Proposal: {proposal}\n\nDecision:"
    
    try:
        res = requests.post("http://localhost:8888/completion", json={
            "prompt": critic_prompt, "n_predict": 16, "stop": ["\n"], "stream": False
        }, timeout=30)

        decision = res.json()["content"].strip().upper()
        print(f"⚖️ Critic Decision: {decision}")
        return "APPROVED" in decision
    except Exception as e:
        print(f"⚠️ Critic failed: {e}")
        return True # Default to pass


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



@app.post("/chat")
async def chat(request: ChatRequest):
    print(f"📥 Incoming message: {request.message}")
    request_started_at = time.time()
    request_id = uuid.uuid4().hex[:8]
    trace = [{
        "type": "request",
        "message": request.message,
    }]
    if not ensure_actor_engine():
        return make_response(
            "Error: actor engine is offline. Check the model path and llama-server binary.",
            trace=trace + [{"type": "engine", "status": "offline"}],
            request_id=request_id,
            request_duration_ms=int((time.time() - request_started_at) * 1000),
        )
    
    # 1. Fast-Path
    fast_response = router.route(request.message)
    if fast_response: 
        print(f"⚡ Fast-Path match: {fast_response['response'][:50]}...")
        return make_response(
            fast_response["response"],
            trace=trace + fast_response.get("trace", []),
            mode="fast_path",
            request_id=request_id,
            request_duration_ms=int((time.time() - request_started_at) * 1000),
        )

    # 2. Agentic Loop (Max 3 steps)
    current_prompt = format_actor_prompt(request.message, request.history)
    last_content = ""
    seen_tool_calls = set()
    
    for step in range(3):
        print(f"🤖 ACTOR step {step+1} is thinking...")
        try:
            res = requests.post("http://localhost:8888/completion", json={
                "prompt": current_prompt, "n_predict": 512, 
                "stop": ["User:", "Assistant:", "<|turn|>", "<turn|>", "<eos>"], 
                "stream": False
            }, timeout=120)
            
            if res.status_code != 200:
                return make_response(
                    f"Error: Actor engine returned {res.status_code}",
                    trace=trace + [{"type": "actor", "step": step + 1, "status": "http_error", "status_code": res.status_code}],
                    request_id=request_id,
                    request_duration_ms=int((time.time() - request_started_at) * 1000),
                )
                
            content = res.json()["content"].strip()
            last_content = content
            trace.append({
                "type": "actor",
                "step": step + 1,
                "status": "ok",
                "content_preview": content[:240],
            })
            
            # Check for tool usage
            if "{" in content and "}" in content:
                start, end = content.find("{"), content.rfind("}") + 1
                tool_json_raw = content[start:end]
                tool_call = repair_and_alias_json(tool_json_raw)
                trace.append({
                    "type": "tool_parse",
                    "step": step + 1,
                    "raw": tool_json_raw[:240],
                    "parsed": tool_call,
                })
                if tool_call is None:
                    trace.append({
                        "type": "tool_parse",
                        "step": step + 1,
                        "status": "malformed",
                    })
                    current_prompt = append_json_repair_feedback(current_prompt, content)
                    last_content = content
                    continue
                
                if verify_with_critic(json.dumps(tool_call)):
                    trace.append({
                        "type": "critic",
                        "step": step + 1,
                        "status": "approved",
                    })
                    tool_name = tool_call.get("tool")
                    args = tool_call.get("args", {})
                    is_valid, args, validation_error = validate_tool_call(AVAILABLE_TOOLS, tool_name, args, request.message)
                    if not is_valid:
                        result = f"Blocked tool call: {validation_error}"
                        print(f"🛑 {result}")
                        trace.append({
                            "type": "tool_execution",
                            "step": step + 1,
                            "tool": tool_name,
                            "args": args,
                            "status": "blocked",
                            "reason": validation_error,
                        })
                        current_prompt = append_tool_feedback(current_prompt, content, result, blocked=True)
                        last_content = result
                        continue

                    signature = tool_call_signature(tool_name, args)
                    if signature in seen_tool_calls:
                        result = f"Blocked repeated tool call: {tool_name}"
                        print(f"🛑 {result}")
                        trace.append({
                            "type": "tool_execution",
                            "step": step + 1,
                            "tool": tool_name,
                            "args": args,
                            "status": "blocked_repeat",
                            "reason": "duplicate_tool_call",
                        })
                        current_prompt = append_tool_feedback(current_prompt, content, result, blocked=True)
                        last_content = result
                        continue
                    
                    if tool_name in AVAILABLE_TOOLS:
                        print(f"🚀 Executing tool: {tool_name}")
                        seen_tool_calls.add(signature)
                        try:
                            result = AVAILABLE_TOOLS[tool_name](**args)
                        except Exception as te:
                            result = f"Error: {str(te)}"
                        trace.append({
                            "type": "tool_execution",
                            "step": step + 1,
                            "tool": tool_name,
                            "args": args,
                            "status": "ok" if not str(result).startswith("Error:") else "error",
                            "result_preview": str(result)[:240],
                        })
                        
                        print(f"✅ Tool Result: {str(result)[:50]}...")
                        # Feed result back and loop
                        current_prompt = append_tool_feedback(current_prompt, content, result)
                        continue 
                trace.append({
                    "type": "critic",
                    "step": step + 1,
                    "status": "rejected",
                    "proposal": tool_call,
                })
                rejection = "Critic rejected tool call."
                current_prompt = append_tool_feedback(current_prompt, content, rejection, rejected=True)
                last_content = rejection
                continue
            
            # If no tool call or finished, return final content
            print("📤 Returning final response.")
            return make_response(content, trace=trace, request_id=request_id)
                
        except Exception as e:
            print(f"⚠️ Chat Exception: {str(e)}")
            return make_response(
                f"Error: {str(e)}",
                trace=trace + [{"type": "exception", "step": step + 1, "message": str(e)}],
                request_id=request_id,
                request_duration_ms=int((time.time() - request_started_at) * 1000),
            )
    
    return make_response(
        last_content,
        trace=trace,
        request_id=request_id,
        request_duration_ms=int((time.time() - request_started_at) * 1000),
    )



if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=1337)
