import sys
import os

# Add current directory to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), 'backend')))
from main import remember_tool, recall_tool, memory

def test_integration():
    print("--- Testing MemSpire Integration ---")
    
    # 1. Add Hierarchical Fact
    res1 = remember_tool(
        fact="Maya has gymnastics practice every Wednesday",
        wing="World",
        floor="Family"
    )
    print(f"Add Fact 1: {res1}")
    
    # 2. Add another related fact
    res2 = remember_tool(
        fact="The practice starts at 5 PM",
        wing="World",
        floor="Family"
    )
    print(f"Add Fact 2: {res2}")
    
    # 3. Recall
    print("\nRecalling 'gymnastics':")
    recall_res = recall_tool("gymnastics")
    print(recall_res)
    
    # 4. Check Hierarchy
    print("\nFull Hierarchy:")
    hierarchy = memory.get_hierarchy()
    for wing, floor, count in hierarchy:
        print(f"Wing: {wing} | Floor: {floor} | Facts: {count}")

    if "Maya" in recall_res and "World" in recall_res:
        print("\n✅ INTEGRATION TEST PASSED")
        return True
    else:
        print("\n❌ INTEGRATION TEST FAILED")
        return False

if __name__ == "__main__":
    test_integration()
    # Cleanup test db
    if os.path.exists("models/memspire.db"):
        os.remove("models/memspire.db")
