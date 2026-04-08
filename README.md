# Gemma 4 Mobile Agent: Your Local Mobile AI Agent 📱🤖

Gemma 4 Mobile Agent is a powerful, locally-hosted AI agent designed to run directly on your phone via Termux. It leverages the latest **Gemma 4** models (released April 2, 2026): **E4B** for action and **E2B** for critical review.

## ✨ Features

- **Local Execution:** No internet required for reasoning.
- **Proposer-Critic (SmolClaw):** Dual-model architecture for safer and more reliable tool usage.
- **Gemma 4 Edge Models:** Native multimodal capabilities (Text, Image, Audio) and MoE architecture for extreme speed on mobile.
- **Fast-Path Router:** Instant response for common commands without LLM overhead.
- **Phone Tools:** Control torch, vibrate, battery, location, clipboard, TTS, and more.
- **Web Search:** Integrated with DuckDuckGo for real-time information.
- **Memory Management:** Remembers past facts and context.

## 🛠️ Installation Guide (Termux)

### 1. Install Termux
Download and install the latest Termux from [F-Droid](https://f-droid.org/en/packages/com.termux/).

### 2. Update and Install Dependencies
Run the following commands in Termux:

```bash
pkg update && pkg upgrade
pkg install python git termux-api cmake make clang
```

### 3. Clone the Repository
```bash
git clone https://github.com/yourusername/gemma-4-mobile-agent.git
cd gemma-4-mobile-agent
```

### 4. Install Python Requirements
```bash
pip install -r requirements.txt
```

### 5. Download Gemma 4 Models
You will need the GGUF versions of Gemma 4 models. Place them in the `models/` directory:
- `gemma-4-e4b-it-q4_k_m.gguf` (Actor - 4B)
- `gemma-4-e2b-it-q4_k_m.gguf` (Critic - 2B)

*You can download these from Hugging Face repositories (e.g., bartowski or unsloth).*


### 6. Get `llama-server`
You'll need a compatible `llama-server` binary for Termux (aarch64). You can build it from `llama.cpp` or find a pre-built binary and place it in `backend/llama-server`.

```bash
# Example build from source
git clone https://github.com/ggml-org/llama.cpp
cd llama.cpp
make llama-server
cp llama-server ../gemma-4-mobile-agent/backend/
```


## 🚀 Running the Agent

Start the backend server:
```bash
python backend/main.py
```

Access the UI at: `http://localhost:1337` in your mobile browser.

## ⚙️ How it Works

1. **Router:** Checks if the query matches a "Fast-Path" (e.g., "torch on").
2. **Actor (Gemma 4 E4B):** If not a fast-path, the Actor model proposes a tool call based on the user's request.
3. **Critic (Gemma 4 E2B):** The Critic model reviews the proposed tool call for safety and logic.
4. **Tool Execution:** If approved, the tool is executed via `termux-api`.
5. **Synthesis:** The Actor generates a final response based on the tool result.


## ⚖️ License
MIT License.
