# ai-fix — AI-Native Code Review CLI

A blazing-fast terminal tool that implements **Shift-Left** DevOps by scanning your uncommitted Git changes, identifying critical security vulnerabilities and bugs using an LLM, and automatically applying fixes to your local files — all before you commit.

```
╔══════════════════════════════════════╗
║    AI Code Review  ·  Shift-Left     ║
╚══════════════════════════════════════╝
```

## How it works

1. **Extract** — Runs `git diff -U5` to capture only modified lines and their context
2. **Analyze** — Sends the diff to Gemini 2.5 Flash wrapped in a "Senior Staff Engineer" system prompt
3. **Format** — The LLM returns a strict JSON payload with `original_snippet` and `fixed_snippet`
4. **Patch** — The tool locates each snippet in your local file and replaces it in-place

If no critical issues are found, the process exits cleanly. No noise.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21+ |
| Maven | 3.9+ |
| Git | Any modern version |
| GraalVM *(native image only)* | 21+ |
| Google AI API key | — |

---

## Setup

### 1. Set your API key

```bash
export GOOGLE_AI_API_KEY="AIza..."
```

Or store it persistently with the built-in config command after first run:

```bash
ai-fix config-set-key --api-key AIza...
```

### 2. Build (JVM — for development)

```bash
./mvnw clean package
```

### 3. Run

```bash
# Interactive shell
java -jar target/sonar-agent-1.0.0.jar

# Single command
java -jar target/sonar-agent-1.0.0.jar scan --path /path/to/your/project
```

### 4. Compile to native binary (production)

Requires GraalVM with `native-image` installed.

```bash
./mvnw -Pnative native:compile
```

This produces `target/ai-fix`. Move it to your PATH:

```bash
mv target/ai-fix /usr/local/bin/ai-fix
```

---

## Usage

### `scan` — Read-only analysis

Analyzes your uncommitted changes and prints a report without modifying any files.

```bash
ai-fix scan
ai-fix scan --path /path/to/project
```

**Example output:**
```
Found 2 critical issue(s):

[1] CRITICAL  SECURITY
    File    : src/main/java/UserService.java
    Issue   : SQL injection via string concatenation
    Before  : String query = "SELECT * FROM users WHERE id = " + userId;
    After   : String query = "SELECT * FROM users WHERE id = ?";

[2] HIGH  CRITICAL_BUG
    File    : src/main/java/FileProcessor.java
    Issue   : InputStream never closed — resource leak
    Before  : InputStream is = new FileInputStream(file);
    After   : try (InputStream is = new FileInputStream(file)) {
```

---

### `fix` — Analyze and auto-apply fixes

Runs the same analysis as `scan` and then applies all fixes directly to your files. Timestamped backups are created automatically (`.backup.YYYYMMDD_HHmmss`).

```bash
ai-fix fix
ai-fix fix --path /path/to/project
```

---

### `install-hook` — Attach to git pre-commit

Installs a `pre-commit` hook so every `git commit` automatically runs `ai-fix fix` before allowing the commit to proceed.

```bash
ai-fix install-hook
ai-fix install-hook --path /path/to/project
```

Alternatively, use the shell script directly:

```bash
bash install-hook.sh
```

---

### `config-set-key` — Store API key

```bash
ai-fix config-set-key --api-key AIza...
```

### `config-set` — Set any property

```bash
# Switch to a different model
ai-fix config-set --key spring.ai.google.genai.chat.options.model --value gemini-2.5-flash
```

### `config-show` — View current config

```bash
ai-fix config-show
```

---

## Docker

```bash
# Build the image
docker compose build

# Run a scan on the current directory
docker compose run --rm ai-fix scan

# Apply fixes
docker compose run --rm ai-fix fix
```

The current directory is mounted as `/workspace` inside the container.

---

## What the LLM looks for

The tool instructs the LLM to flag only **CRITICAL** and **HIGH** severity issues:

| Category | Examples |
|---|---|
| `SECURITY` | SQL injection, XSS, command injection, hardcoded secrets, path traversal, SSRF |
| `CRITICAL_BUG` | Null pointer dereferences, resource leaks, race conditions, integer overflow |
| `DATA_INTEGRITY` | Unsafe casting, silent data loss, off-by-one errors |

Style issues, naming conventions, and minor improvements are intentionally ignored to keep the signal-to-noise ratio high.

---

## Supported languages

Language detection is automatic based on file extension:

`Java` · `Kotlin` · `JavaScript` · `TypeScript` · `Python` · `Go` · `Rust` · `C#` · `PHP` · `Ruby`

---

## Project structure

```
src/main/java/com/sonar/agent/
├── Main.java                        # Spring Boot entry point
├── command/
│   ├── AnalyzeCommand.java          # scan / fix / install-hook CLI commands
│   └── ConfigCommand.java           # config-set-key / config-show commands
├── agent/
│   ├── AnalyzerGraph.java           # Extract → Analyze → Format → Patch workflow
│   ├── SystemPrompts.java           # Senior Staff Engineer LLM persona
│   └── models/
│       ├── DiffResult.java          # Git diff output
│       ├── FixProposal.java         # Single LLM fix (original + fixed snippet)
│       └── AnalysisResponse.java    # Full LLM JSON response
├── tools/
│   ├── GitOperationsTool.java       # Runs git diff via ProcessBuilder
│   └── FileSystemTool.java          # Reads, backs up, and patches local files
└── config/
    ├── AiConfig.java                # ObjectMapper bean
    └── AgentConfig.java             # ChatClient bean wired to Google Gemini
```

---

## Tech stack

| Component | Technology |
|---|---|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.3 |
| CLI engine | Spring Shell 3.3 |
| AI integration | Spring AI 1.0 (Google Gemini) |
| LLM | Gemini 2.5 Flash |
| Build | Maven |
| Native binary | GraalVM Native Image |

---

## GitHub App — Automated PR Reviews

Run ai-fix as a webhook server to automatically review every pull request.

### 1. Configure a GitHub webhook

In your repository (or organisation), go to **Settings → Webhooks → Add webhook** and set:

| Field | Value |
|---|---|
| Payload URL | `https://your-server/webhook` |
| Content type | `application/json` |
| Secret | A random string (copy it for the next step) |
| Events | **Pull requests** |

### 2. Start the server

```bash
export AI_FIX_WEBHOOK_SECRET="<the-secret-from-above>"
export AI_FIX_WEBHOOK_GITHUB_TOKEN="ghp_..."   # PAT with pull_requests:write + contents:read
export GOOGLE_AI_API_KEY="AIza..."

java -jar target/sonar-agent-1.0.0.jar --spring.profiles.active=webhook
```

Or with Docker:

```bash
GOOGLE_AI_API_KEY=AIza... \
AI_FIX_WEBHOOK_SECRET=mysecret \
AI_FIX_WEBHOOK_GITHUB_TOKEN=ghp_... \
docker compose --profile webhook up
```

### 3. What happens on each PR

| Scenario | Review posted |
|---|---|
| No issues found | ✅ `COMMENT` — scan passed |
| Issues found | 🔴 `REQUEST_CHANGES` — lists every issue with before/after snippets |

The server accepts `opened`, `synchronize`, and `reopened` events and ignores everything else.

---

## Roadmap

- [x] Local CLI execution with manual triggering
- [x] Git pre-commit hook integration
- [x] Dynamic language detection with language-specific prompts
- [x] Docker / docker-compose support
- [x] GitHub App — REST API for automated PR reviews

---

## License

MIT
