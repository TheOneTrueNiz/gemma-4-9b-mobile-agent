import sys
import os
import requests
import time
import subprocess

# Add project directories to path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), 'backend')))
from main import MODELS, SERVER_BIN

def test_engines():
    print("--- Testing Gemma 4 Engine Startup ---")
    
    # Try to start one at a time manually to check for errors
    for name, config in MODELS.items():
        print(f"Testing {name.upper()}...")
        cmd = [
            SERVER_BIN,
            "-m", config["path"],
            "-c", "4096",
            "--port", config["port"],
            "-t", config["threads"],
            "--flash-attn", "on",
            "--log-disable"
        ]
        
        # Launch in background
        p = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        
        # Ready check
        online = False
        for i in range(30): # Wait 30s
            try:
                res = requests.get(f"http://localhost:{config['port']}/health", timeout=1)
                if res.status_code == 200:
                    print(f"✅ {name.upper()} is ONLINE")
                    online = True
                    break
            except:
                time.sleep(1)
        
        # Terminate
        p.terminate()
        p.wait()
        
        if not online:
            print(f"❌ {name.upper()} FAILED to start. Check if model exists: {os.path.exists(config['path'])}")
            return False
            
    return True

if __name__ == "__main__":
    test_engines()
