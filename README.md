# ai-fix - AI-Native Code Review CLI

ai-fix scans your uncommitted Git changes before you commit, asks the selected AI provider to look for high-impact issues, and can apply the proposed fixes directly to your local files.

It supports three AI providers:

| Provider | CLI id | Default model |
|---|---|---|
| Google Gemini | `gemini` | `gemini-2.5-flash` |
| OpenAI | `openai` | `gpt-4.1-mini` |
| Anthropic Claude | `claude` | `claude-sonnet-4-20250514` |

You must select one provider and configure its API key before running `scan`, `fix`, or webhook reviews.

---

## How It Works

1. **Extract** - runs `git diff -U5` to capture modified lines and nearby context.
2. **Select dimensions** - cheap heuristics choose relevant review lenses such as security, regression, performance, or SOLID.
3. **Analyze** - sends the diff to the selected AI provider.
4. **Format** - expects strict JSON with `original_snippet`, `fixed_snippet`, severity, and category.
5. **Patch** - for `fix`, replaces each exact snippet in your local files and creates backups.

If no critical or high severity issues are found, the process exits cleanly.

---

## Prerequisites

| Requirement | Version |
|---|---|
| JDK | 21+ |
| Maven | 3.9+ |
| Git | Any modern version |
| GraalVM | 21+, native image only |
| AI API key | Gemini, OpenAI, or Claude |

---

## Setup

### 1. Build

```bash
./mvnw clean package
```

### 2. Start the shell

You can run the application directly via Java:

```bash
java -jar target/preflight-agent-1.0.0.jar
```

####  Make ai-fix a Global Command (Recommended)
To run ai-fix from any directory without typing the full Java command, add an alias or wrapper function for your operating system. (Note: Replace /absolute/path/to/ with the actual path to your cloned repository).

###### macOS / Linux (Bash/Zsh):
Add an alias to your shell profile (~/.bashrc or ~/.zshrc)


```bash
echo 'alias ai-fix="java -jar /absolute/path/to/target/preflight-agent-1.0.0.jar"' >> ~/.bashrc
source ~/.bashrc```
```
###### Windows (PowerShell)
Open your PowerShell profile (notepad $PROFILE) and add this function:

```powershell
function ai-fix {
    java -jar "D:\Projects\preflight-agent\target\preflight-agent-1.0.0.jar" $args
}
```
Reload your profile by running . $PROFILE.


### 3. Select Your AI Provider

Pick exactly one provider before execution:

```bash
ai-fix config-select-ai --provider gemini
```

Other valid choices:

```bash
ai-fix config-select-ai --provider openai
ai-fix config-select-ai --provider claude
```

### 4. Store The Provider API Key

Gemini:

```bash
ai-fix config-set-key --provider gemini --api-key AIza...
```

OpenAI:

```bash
ai-fix config-set-key --provider openai --api-key sk-...
```

Claude:

```bash
ai-fix config-set-key --provider claude --api-key sk-ant-...
```

Keys are saved in `~/.aifix/config.properties` and masked by `config-show`.

### 5. Optional: Override The Model

```bash
ai-fix config-set-model --provider gemini --model gemini-2.5-flash
ai-fix config-set-model --provider openai --model gpt-4.1-mini
ai-fix config-set-model --provider claude --model claude-sonnet-4-20250514
```

### 6. Verify Configuration

```bash
ai-fix config-show
```

Example saved config:

```properties
ai-fix.ai.provider=openai
ai-fix.ai.openai.api-key=sk-...
ai-fix.ai.openai.model=gpt-4.1-mini
```

---

## Environment Variable Alternative

You can configure the selected provider without writing `~/.aifix/config.properties`.

| Setting | Environment variable |
|---|---|
| Selected provider | `AI_FIX_AI_PROVIDER` |
| Gemini key | `AI_FIX_GEMINI_API_KEY`, `GEMINI_API_KEY`, or `GOOGLE_AI_API_KEY` |
| OpenAI key | `AI_FIX_OPENAI_API_KEY` or `OPENAI_API_KEY` |
| Claude key | `AI_FIX_CLAUDE_API_KEY` or `ANTHROPIC_API_KEY` |
| Gemini model | `AI_FIX_GEMINI_MODEL` |
| OpenAI model | `AI_FIX_OPENAI_MODEL` |
| Claude model | `AI_FIX_CLAUDE_MODEL` |
| Max output tokens | `AI_FIX_AI_MAX_OUTPUT_TOKENS` |
| Temperature | `AI_FIX_AI_TEMPERATURE` |

Example:

```bash
export AI_FIX_AI_PROVIDER=openai
export OPENAI_API_KEY="sk-..."
ai-fix scan --path /path/to/project
```

---

## Usage

### `scan` - Read-only Analysis

```bash
ai-fix scan
ai-fix scan --path /path/to/project
```

### `fix` - Analyze And Apply Fixes

```bash
ai-fix fix
ai-fix fix --path /path/to/project
```

Timestamped backups are created before edits.

### `install-hook` - Git Pre-Commit Hook

```bash
ai-fix install-hook
ai-fix install-hook --path /path/to/project
```

The hook runs `ai-fix fix --auto` before every commit.

---

## Configuration Commands

| Command | Purpose |
|---|---|
| `config-select-ai --provider <gemini\|openai\|claude>` | Select the AI provider used by future runs |
| `config-set-key --provider <provider> --api-key <key>` | Store a provider-specific API key |
| `config-set-model --provider <provider> --model <model>` | Override a provider's model |
| `config-set --key <key> --value <value>` | Set any raw config property |
| `config-show` | Show saved config with secrets masked |

The selected provider is read at execution time, so changing it affects the next `scan`, `fix`, or webhook review without rebuilding.

---

## Review Dimensions

The analyzer uses separate review dimensions instead of one giant prompt:

| Category | Examples |
|---|---|
| `SECURITY` | SQL injection, XSS, SSRF, command injection, hardcoded secrets, auth bypass |
| `CRITICAL_BUG` | Null dereferences, resource leaks, races, infinite loops, deadlocks |
| `DATA_INTEGRITY` | Silent data loss, unsafe casts, bad transaction boundaries, off-by-one data errors |
| `REGRESSION` | Breaking public contracts, response shape changes, incompatible defaults |
| `PERFORMANCE` | N+1 queries, blocking hot paths, unbounded loops, avoidable quadratic work |
| `SOLID` | Only directly visible high-risk design violations in the diff |

Only `CRITICAL` and `HIGH` findings are accepted. Style, naming, and minor suggestions are intentionally ignored.

---

## Supported Languages

Language detection is automatic from file extension:

`Java`, `Kotlin`, `JavaScript`, `TypeScript`, `Python`, `Go`, `Rust`, `C#`, `PHP`, `Ruby`

---

## Docker

```bash
docker compose build
docker compose run --rm ai-fix scan
docker compose run --rm ai-fix fix
```

Pass provider configuration as environment variables:

```bash
AI_FIX_AI_PROVIDER=claude \
ANTHROPIC_API_KEY=sk-ant-... \
docker compose run --rm ai-fix scan
```

---

## GitHub App - Automated PR Reviews

Run ai-fix as a webhook server to automatically review pull requests.

### 1. Configure GitHub Webhook

In your repository or organization, go to **Settings -> Webhooks -> Add webhook**:

| Field | Value |
|---|---|
| Payload URL | `https://your-server/webhook` |
| Content type | `application/json` |
| Secret | A random shared secret |
| Events | Pull requests |

### 2. Start The Server

Example with OpenAI:

```bash
export AI_FIX_WEBHOOK_SECRET="<webhook-secret>"
export AI_FIX_WEBHOOK_GITHUB_TOKEN="ghp_..."
export AI_FIX_AI_PROVIDER=openai
export OPENAI_API_KEY="sk-..."

java -jar target/preflight-agent-1.0.0.jar --spring.profiles.active=webhook
```

Example with Claude and Docker:

```bash
AI_FIX_AI_PROVIDER=claude \
ANTHROPIC_API_KEY=sk-ant-... \
AI_FIX_WEBHOOK_SECRET=mysecret \
AI_FIX_WEBHOOK_GITHUB_TOKEN=ghp_... \
docker compose --profile webhook up
```

### 3. Review Behavior

| Scenario | Review posted |
|---|---|
| No issues found | `COMMENT` - scan passed |
| Issues found | `REQUEST_CHANGES` - lists every issue with before/after snippets |

The server accepts `opened`, `synchronize`, and `reopened` events.

---

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| CLI engine | Spring Shell 3.3 |
| AI providers | Gemini REST API, OpenAI Responses API, Anthropic Messages API |
| JSON | Jackson |
| Build | Maven |
| Native binary | GraalVM Native Image |

---

## API References

- [Gemini generateContent REST API](https://ai.google.dev/api)
- [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses)
- [Anthropic Messages API](https://docs.anthropic.com/en/api/messages-examples)

---

## License

MIT
