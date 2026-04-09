import sys
import os

PROJECT_ROOT = os.path.abspath(os.path.dirname(__file__))
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

from tools.phone_tools import AVAILABLE_TOOLS
from backend.main import health, recall_tool, remember_tool

def test_memory():
    print("--- Testing MemSpire Integration ---")
    fact = "Test memory: Gemma mobile agent system check"
    res = remember_tool(fact, wing="Testing", floor="Smoke")
    print(f"Remember result: {res}")

    recalled = recall_tool("Gemma mobile agent system check")
    print(f"Recall result: {recalled}")
    return fact in recalled

def test_search():
    print("\n--- Testing Web Search ---")
    res = AVAILABLE_TOOLS["web_search"]("Albert Einstein")
    print(f"Search Result: {res}")
    return isinstance(res, dict) or "abstract" in res

def test_file_system():
    print("\n--- Testing File System ---")
    files = AVAILABLE_TOOLS["list_files"](".")
    print(f"Files found: {len(files)}")
    return len(files) > 0

async def test_backend_health():
    print("\n--- Testing Backend Health Payload ---")
    payload = await health()
    print(f"Health payload: {payload}")
    return payload.get("status") == "ok" and "actor_online" in payload

if __name__ == "__main__":
    import asyncio
    m = test_memory()
    s = test_search()
    f = test_file_system()
    h = asyncio.run(test_backend_health())
    
    print("\n" + "="*20)
    print(f"Memory: {'PASSED' if m else 'FAILED'}")
    print(f"Search: {'PASSED' if s else 'FAILED'}")
    print(f"Files:  {'PASSED' if f else 'FAILED'}")
    print(f"Health: {'PASSED' if h else 'FAILED'}")
    print("="*20)
