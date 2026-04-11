![Gemini_Generated_Image_ez117mez117mez11](https://github.com/user-attachments/assets/8d04d75f-ca36-465e-bbfa-ffa72ba2cf1b)

Made for people who want their AI running on the device in their pocket, not phoning home to California.

# Gemma 4 Mobile Agent

**A real, offline AI agent that runs on your Android phone in Termux.**

Uses Gemma 4's new edge models:
- **E4B** (Actor) — proposes actions and tool calls
- **E2B** (Critic) — reviews every proposal for safety, logic, and usefulness

No cloud. No API keys. No bullshit. Just the phone, Termux, and GGUF models.

### Core Architecture (Proposer-Critic Loop)

1. **Fast-Path Router** — Instant responses for simple commands (torch on, vibrate, etc.) — skips the LLM entirely.
2. **Actor (Gemma-4-E4B)** — Takes user input and proposes a tool call or plan.
3. **Critic (Gemma-4-E2B)** — Evaluates the proposal. Rejects if unsafe, illogical, or low-value. Can ask for revision.
4. **Tool Execution** — If approved, the tool runs via Termux-API.
5. **Synthesis** — Actor turns the tool result into a natural language response.

This loop makes the agent far more reliable than single-model tool use on tiny hardware.

Recent addition: **MemSpire** hierarchical memory system for persistent context and long-term facts.

### Features

- Fully local & offline (after model download)
- Multimodal support (text + image + audio via Gemma 4)
- Direct phone hardware control:
  - Flashlight (torch)
  - Vibration
  - Location
  - Battery status
  - Clipboard
  - Text-to-Speech (TTS)
  - Camera access
- Web search fallback via DuckDuckGo (when you actually need fresh info)
- Web UI served on `http://localhost:1337` (open in any mobile browser)
- Low-latency MoE architecture from Gemma 4

### Project Structure

gemma-4-9b-mobile-agent/
├── app/              # Native Android launcher app (chat-first home screen)
├── backend/          # Server, llama-server integration, routing logic
├── tools/            # Termux-API wrappers (torch, vibrate, location, etc.)
├── models/           # Put your GGUF files here (not tracked)
├── test_gemma4.py
├── test_memspire_integration.py
├── test_system.py
├── requirements.txt
├── Modelfile
└── backend/main.py   # Main entry point


### Installation (Termux)

```bash
# 1. Install Termux from F-Droid (not Play Store)
pkg update && pkg upgrade -y
pkg install python git termux-api cmake make clang -y

# 2. Clone
git clone https://github.com/TheOneTrueNiz/gemma-4-9b-mobile-agent.git
cd gemma-4-9b-mobile-agent

# 3. Python deps
pip install -r requirements.txt

# 4. Download models (GGUF Q4_K_M recommended for mobile)
# Get them from Hugging Face (bartowski or unsloth quantizations)
mkdir -p models
# Place:
#   gemma-4-e4b-it-q4_k_m.gguf
#   gemma-4-e2b-it-q4_k_m.gguf

### llama-server setup (required):
Build or copy llama-server binary from llama.cpp into backend/.

cd backend
python main.py

Then open your phone browser to http://localhost:1337

Current Status

This is early but functional. The proposer-critic loop + fast-path router already makes it more trustworthy than most tiny agents. MemSpire integration landed yesterday.

### Native Launcher Direction

The repo now also contains a real Android launcher app scaffold in `app/`.

Design target:
- the launcher is the primary interaction shell
- the home screen is the chat UI
- the agent sits between the user and the hardware
- the existing Python backend stays the local agent service on `127.0.0.1:1337`

Current launcher scaffold includes:
- `HOME` / default-launcher intent filter
- chat-first Compose home surface
- app drawer driven by installed launcher activities
- agent / phone / debug overlays
- localhost `/chat` client for the existing backend

To turn it into an installable launcher, open the repo as an Android Studio project and build the `app` module.

Validation:
```bash
python -m unittest discover -s tests -v
```

Tests are in the root if you want to poke around.

License
MIT
