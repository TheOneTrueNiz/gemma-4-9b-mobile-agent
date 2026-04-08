import sys
import os
import json

# Add project directories to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))
from tools.phone_tools import AVAILABLE_TOOLS
from backend.main import MemoryManager

def test_memory():
    print("--- Testing Memory Manager ---")
    test_file = "models/test_memory.json"
    mem = MemoryManager(test_file)
    res = mem.add("The secret password is 'GemmaIsGreat'")
    print(f"Add memory result: {res}")
    
    recalled = mem.recall("secret password")
    print(f"Recall result: {recalled}")
    
    if os.path.exists(test_file):
        os.remove(test_file)
        print("Test memory file cleaned up.")
    return len(recalled) > 0

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

if __name__ == "__main__":
    m = test_memory()
    s = test_search()
    f = test_file_system()
    
    print("\n" + "="*20)
    print(f"Memory: {'PASSED' if m else 'FAILED'}")
    print(f"Search: {'PASSED' if s else 'FAILED'}")
    print(f"Files:  {'PASSED' if f else 'FAILED'}")
    print("="*20)
