#!/bin/bash
# install-hook.sh
# Installs the ai-fix pre-commit hook into the current git repository.
# Usage: bash install-hook.sh [/path/to/repo]

set -e

REPO_DIR="${1:-.}"
HOOKS_DIR="$REPO_DIR/.git/hooks"
HOOK_FILE="$HOOKS_DIR/pre-commit"

if [ ! -d "$HOOKS_DIR" ]; then
  echo "Error: $REPO_DIR does not appear to be a git repository."
  exit 1
fi

cat > "$HOOK_FILE" << 'EOF'
#!/bin/bash
# AI Code Review Pre-Commit Hook
# Installed by ai-fix (https://github.com/fayzsa/sonar_agent)

echo ""
echo "[ ai-fix ] Running AI security scan before commit..."

if command -v ai-fix &>/dev/null; then
  ai-fix fix --auto
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    echo ""
    echo "[ ai-fix ] Critical issues detected — commit blocked."
    echo "[ ai-fix ] Review the output above, then re-stage and re-commit."
    echo "[ ai-fix ] To bypass (not recommended): git commit --no-verify"
    exit 1
  fi
  echo "[ ai-fix ] Scan complete. Proceeding with commit."
elif [ -f "./target/sonar-agent-1.0.0.jar" ]; then
  java -jar ./target/sonar-agent-1.0.0.jar fix --auto
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    echo "[ ai-fix ] Critical issues detected. Commit blocked."
    exit 1
  fi
else
  echo "[ ai-fix ] WARNING: ai-fix not found in PATH. Skipping AI review."
  echo "[ ai-fix ] Install: https://github.com/fayzsa/sonar_agent"
fi

exit 0
EOF

chmod +x "$HOOK_FILE"

echo ""
echo "✓ Pre-commit hook installed at: $HOOK_FILE"
echo ""
echo "  Every 'git commit' will now run 'ai-fix fix --auto' before proceeding."
echo "  Ensure GOOGLE_AI_API_KEY is set in your environment."
echo ""
