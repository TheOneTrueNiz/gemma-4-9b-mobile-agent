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
- backend health, start, and restart controls through Termux
- direct home-bar app launch routing for commands like `open chrome`
- persistent launcher usage state for recents and launch counts
- ranked app resolution instead of raw string matching

Launcher code layout:
- `MainActivity.kt` for Android entry/wiring
- `LauncherUi.kt` for Compose surfaces and overlays
- `BackendClient.kt` for localhost backend calls
- `TermuxBridge.kt` for launcher-to-Termux backend control
- `LauncherModels.kt` for shared launcher data types
- `LauncherResolver.kt` for home-input intent resolution and app ranking
- `LauncherUsageStore.kt` for persistent launcher state

To turn it into an installable launcher, open the repo as an Android Studio project and build the `app` module.

Wrapper and build entry points:
```bash
./tools/check_android_launcher_env.sh
./tools/build_android_launcher.sh
./tools/install_android_launcher.sh
```

What they do:
- `check_android_launcher_env.sh` verifies Java 17 and Android SDK presence
- `build_android_launcher.sh` runs the env check and then builds `:app:assembleDebug`
- `install_android_launcher.sh` builds if needed, copies the debug APK into shared
  downloads when possible, and opens the Android package installer from Termux
- `start_backend_from_launcher.sh` is the Termux-side entry point the launcher uses
  to start or restart the Python backend
- on non-`x86_64` Linux hosts, the build script also expects a host-native `aapt2`
  and will pass it through `android.aapt2FromMavenOverride`

Launcher-managed backend setup:
```bash
mkdir -p ~/.termux
printf 'allow-external-apps=true\n' >> ~/.termux/termux.properties
termux-reload-settings
```

Android-side requirement:
- grant `Gemma Launcher` the `Run commands in Termux environment` permission

Without those two pieces, the launcher can still work as a native home shell, but
it will not be able to spin Gemma up automatically through Termux.

Arm64 / aarch64 Linux note:
```bash
apt-get install aapt
```

That provides `/usr/bin/aapt2`, which is needed because Google's Maven-hosted
`aapt2` binary is `x86_64`-only in this environment.

Gradle / Android compatibility for the launcher module:
- Android Gradle Plugin `8.5.2`
- Gradle `8.7`
- JDK `17`

Validation:
```bash
python -m unittest discover -s tests -v
./tools/build_android_launcher.sh
```

Install and test on-device:
```bash
./tools/install_android_launcher.sh
```

Then set `Gemma Launcher` as the default Home app in Android settings.

Tests are in the root if you want to poke around.

License
MIT
