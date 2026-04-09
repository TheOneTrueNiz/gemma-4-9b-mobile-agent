from fastapi import FastAPI, HTTPException
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

# Add tools directory to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from tools.phone_tools import AVAILABLE_TOOLS
from backend.router import router

app = FastAPI()
app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")


@app.get("/")
async def read_index():
    return FileResponse(os.path.join(os.path.dirname(__file__), "static/index.html"))

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

def recall_tool(query):
    """Searches the hierarchical memory for relevant information."""
    results = memory.recall(query)
    if not results: return "No relevant memories found."
    return "\n".join([f"[{w} > {f}] {c}" for w, f, c, t in results])

AVAILABLE_TOOLS["remember"] = remember_tool
AVAILABLE_TOOLS["recall"] = recall_tool


# --- NATIVE LLAMA-SERVER MANAGEMENT (MAS) ---
MODELS = {
    "actor": {
        "path": os.path.abspath(os.path.join(os.path.dirname(__file__), "../models/gemma-4-e4b-it-q4_k_m.gguf")),
        "port": "8888",
        "threads": "6" # Increased for Snapdragon 8 Gen 3 (8 cores)
    },
    "critic": {
        "path": os.path.abspath(os.path.join(os.path.dirname(__file__), "../models/gemma-4-e2b-it-q4_k_m.gguf")),
        "port": "8889",
        "threads": "2" 
    }
}
SERVER_BIN = os.path.abspath(os.path.join(os.path.dirname(__file__), "./llama-server"))

def start_engine(name, config):
    print(f"--- Launching {name.upper()} Engine ---")
    cmd = [
        SERVER_BIN,
        "-m", config["path"],
        "-c", "2048", # Faster mobile response
        "--port", config["port"],
        "-t", config["threads"],
        "-b", "512",
        "--flash-attn", "on",
        "--cache-type-k", "q4_0", # Turbo Quant: KV Cache compression
        "--cache-type-v", "q4_0",
        "--log-disable"
    ]


    process = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    
    # Ready check
    for i in range(120): # Increased timeout for heavy mobile loading
        try:
            res = requests.get(f"http://localhost:{config['port']}/health", timeout=1)
            if res.status_code == 200:
                print(f"✅ {name.upper()} is ONLINE (Port {config['port']})")
                return process
        except:
            if i % 10 == 0: print(f"  {name.upper()} is loading... ({i}s)")
            time.sleep(1)

    return process

# Start both engines
actor_process = start_engine("actor", MODELS["actor"])
critic_process = start_engine("critic", MODELS["critic"])

class ChatRequest(BaseModel):
    message: str
    history: list = []

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
    prompt += "When remembering things, try to categorize them into logical Wings (Identity, World, Projects) and Floors (Family, Hobbies, TaskX).\n"
    prompt += "JSON FORMAT: {\"tool\": \"tool_name\", \"args\": {\"arg_name\": \"value\"}}\n\n"
    prompt += f"AVAILABLE TOOLS:\n{tools_list}\n\n"
    if mem_str:
        prompt += f"RELEVANT MEMORIES (MemSpire):\n{mem_str}\n\n"
    for h in history:
        prompt += f"User: {h['user']}\nAssistant: {h['assistant']}\n"
    prompt += f"User: {message}\nAssistant:"
    return prompt


def verify_with_critic(proposal):
    """SmolClaw pattern: Proposer-Critic Split"""
    print(f"🔍 Critic reviewing proposal: {proposal[:50]}...")
    
    critic_prompt = "System: You are the CRITIC. Your job is to approve or reject tool calls. Reject only if harmful or logically broken.\n\n"
    critic_prompt += "EXAMPLES:\n"
    critic_prompt += "Proposal: {\"tool\": \"vibrate\", \"args\": {\"duration_ms\": 200}} -> Decision: APPROVED\n"
    critic_prompt += "Proposal: {\"tool\": \"send_sms\", \"args\": {\"number\": \"123\", \"message\": \"hi\"}} -> Decision: APPROVED\n"
    critic_prompt += "Proposal: {\"tool\": \"list_files\", \"args\": {\"path\": \"/etc/shadow\"}} -> Decision: REJECTED: Security risk\n\n"
    critic_prompt += f"Proposal: {proposal}\n\nDecision:"
    
    try:
        res = requests.post("http://localhost:8889/completion", json={
            "prompt": critic_prompt, "n_predict": 16, "stop": ["\n"], "stream": False
        }, timeout=30)

        decision = res.json()["content"].strip().upper()
        print(f"⚖️ Critic Decision: {decision}")
        return "APPROVED" in decision
    except Exception as e:
        print(f"⚠️ Critic failed: {e}")
        return True # Default to pass


@app.post("/chat")
async def chat(request: ChatRequest):
    print(f"📥 Incoming message: {request.message}")
    
    # 1. Fast-Path
    fast_response = router.route(request.message)
    if fast_response: 
        print(f"⚡ Fast-Path match: {fast_response['response'][:50]}...")
        return fast_response

    # 2. Actor Proposes
    print("🤖 ACTOR is thinking...")
    prompt = format_actor_prompt(request.message, request.history)
    try:
        res = requests.post("http://localhost:8888/completion", json={
            "prompt": prompt, "n_predict": 512, "stop": ["User:", "\n\n"], "stream": False
        }, timeout=120)
        
        if res.status_code != 200:
            print(f"❌ ACTOR error: {res.status_code} - {res.text}")
            return {"response": f"Error: Actor engine returned {res.status_code}"}
            
        content = res.json()["content"].strip()
        print(f"🧠 ACTOR proposal: {content[:100]}...")
        
        # 3. Repair & Validate Tool Usage
        if "{" in content and "}" in content:
            start, end = content.find("{"), content.rfind("}") + 1
            tool_json_raw = content[start:end]
            
            # SmolClaw Repair Layer
            tool_call = repair_and_alias_json(tool_json_raw)
            
            if tool_call:
                print(f"⚙️ Validated tool call: {tool_call['tool']}")
                if verify_with_critic(json.dumps(tool_call)):
                    tool_name = tool_call.get("tool")
                    args = tool_call.get("args", {})
                    
                    if tool_name in AVAILABLE_TOOLS:
                        print(f"🚀 Executing tool: {tool_name}")
                        try:
                            result = AVAILABLE_TOOLS[tool_name](**args)
                        except Exception as te:
                            result = f"Error executing tool: {str(te)}"
                        
                        print(f"✅ Tool Result: {str(result)[:100]}...")
                        
                        # 4. Final Synthesis
                        print("✍️ Synthesizing final response...")
                        final_res = requests.post("http://localhost:8888/completion", json={
                            "prompt": prompt + content + f"\nTool Result: {json.dumps(result)}\nAssistant:",
                            "n_predict": 256, "stop": ["User:", "\n\n"], "stream": False
                        }, timeout=120)
                        return {"response": final_res.json()["content"].strip(), "tool_used": tool_name}
                else:
                    print("⚖️ CRITIC REJECTED the tool call.")
                    return {"response": "My tool request was rejected by my critic. How else can I help?"}
            else:
                print("🛠️ JSON Repair failed to produce a valid tool call.")
                
    except Exception as e:
        print(f"⚠️ Chat Exception: {str(e)}")
        return {"response": f"Error: {str(e)}"}
    
    print("📤 Returning plain text response.")
    return {"response": content}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=1337)
