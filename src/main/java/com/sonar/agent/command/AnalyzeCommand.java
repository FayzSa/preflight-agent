package com.sonar.agent.command;

import com.sonar.agent.agent.AnalyzerGraph;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.tools.GitOperationsTool;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

@ShellComponent
public class AnalyzeCommand {

    private static final String ANSI_RESET  = "[0m";
    private static final String ANSI_RED    = "[31m";
    private static final String ANSI_GREEN  = "[32m";
    private static final String ANSI_YELLOW = "[33m";
    private static final String ANSI_CYAN   = "[36m";
    private static final String ANSI_BOLD   = "[1m";

    private final AnalyzerGraph analyzerGraph;
    private final GitOperationsTool gitTool;

    public AnalyzeCommand(AnalyzerGraph analyzerGraph, GitOperationsTool gitTool) {
        this.analyzerGraph = analyzerGraph;
        this.gitTool = gitTool;
    }

    @ShellMethod(key = "scan", value = "Analyze uncommitted changes for security vulnerabilities and critical bugs")
    public String scan(
            @ShellOption(value = "--path", defaultValue = ".") String path
    ) {
        try {
            String repoRoot = resolveAndValidateRepo(path);
            if (repoRoot == null) return red("Error: Not a git repository: " + path);

            printHeader();
            System.out.println("  Scanning: " + repoRoot);
            System.out.println("  Sending diff to AI...\n");

            AnalysisResponse response = analyzerGraph.analyze(repoRoot);

            if (!response.hasIssues()) {
                return green("✓ No critical issues found. Code looks clean!");
            }

            return formatIssues(response.issues());

        } catch (Exception e) {
            return handleError(e);
        }
    }

    @ShellMethod(key = "fix", value = "Analyze uncommitted changes and automatically apply AI-generated fixes")
    public String fix(
            @ShellOption(value = "--path", defaultValue = ".") String path,
            @ShellOption(value = "--auto", defaultValue = "false",
                         help = "Non-interactive mode for pre-commit hooks") boolean auto
    ) {
        try {
            String repoRoot = resolveAndValidateRepo(path);
            if (repoRoot == null) return red("Error: Not a git repository: " + path);

            if (!auto) printHeader();
            System.out.println("  Analyzing: " + repoRoot);
            System.out.println("  Fetching diff and consulting AI...\n");

            AnalysisResponse response = analyzerGraph.analyze(repoRoot);

            if (!response.hasIssues()) {
                System.out.println(green("✓ No critical issues found. Proceeding with commit."));
                return "";
            }

            System.out.println(formatIssues(response.issues()));
            System.out.println();

            List<Boolean> results = analyzerGraph.applyFixes(response, repoRoot);

            long applied = results.stream().filter(b -> b).count();
            long failed  = results.stream().filter(b -> !b).count();

            StringBuilder sb = new StringBuilder();
            sb.append(green(String.format("✓ Applied %d fix(es) successfully.", applied)));
            if (failed > 0) {
                sb.append("\n").append(yellow(String.format("⚠ %d fix(es) could not be applied automatically — manual review required.", failed)));
                if (auto) {
                    // Signal the pre-commit hook to block the commit
                    System.exit(1);
                }
            }
            sb.append("\n  Backup files created with .backup.* extension");
            return sb.toString();

        } catch (Exception e) {
            if (auto) System.exit(1);
            return handleError(e);
        }
    }

    @ShellMethod(key = "install-hook", value = "Install AI code review as a git pre-commit hook in the current repository")
    public String installHook(
            @ShellOption(value = "--path", defaultValue = ".") String path
    ) {
        try {
            String repoRoot = resolveAndValidateRepo(path);
            if (repoRoot == null) return red("Error: Not a git repository: " + path);

            gitTool.installPreCommitHook(repoRoot);
            return green("✓ Pre-commit hook installed at " + repoRoot + "/.git/hooks/pre-commit\n"
                    + "  ai-fix will now run automatically before every git commit.");
        } catch (Exception e) {
            return red("Failed to install hook: " + e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String resolveAndValidateRepo(String path) {
        if (!gitTool.isGitRepository(path)) return null;
        return gitTool.getRepositoryRoot(path);
    }

    private void printHeader() {
        System.out.println();
        System.out.println(cyan(ANSI_BOLD + "╔══════════════════════════════════════╗"));
        System.out.println(cyan(ANSI_BOLD + "║    AI Code Review  ·  Shift-Left     ║"));
        System.out.println(cyan(ANSI_BOLD + "╚══════════════════════════════════════╝") + ANSI_RESET);
        System.out.println();
    }

    private String formatIssues(List<FixProposal> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append(red(String.format("Found %d critical issue(s):\n", issues.size())));

        for (int i = 0; i < issues.size(); i++) {
            FixProposal issue = issues.get(i);
            sb.append("\n")
              .append(yellow(String.format("[%d] %s", i + 1, issue.severity())))
              .append("  ").append(issue.category())
              .append("\n    File    : ").append(issue.filename())
              .append("\n    Issue   : ").append(issue.issueDescription())
              .append("\n    Before  :\n").append(red(indent(issue.originalSnippet(), 12)))
              .append("\n    After   :\n").append(green(indent(issue.fixedSnippet(), 12)))
              .append("\n");
        }
        return sb.toString();
    }

    private String handleError(Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        if (msg.contains("AI provider is not selected")) {
            return red("AI provider is not selected.\n"
                    + "  Run: config-select-ai --provider gemini\n"
                    + "  Then: config-set-key --provider gemini --api-key <your-key>\n"
                    + "  Supported providers: gemini, openai, claude");
        }
        if (msg.contains("API key is not configured") || msg.contains("api-key") || msg.contains("401")) {
            return red("API key not configured for the selected AI provider.\n"
                    + "  Run: config-show\n"
                    + "  Then set a key, for example:\n"
                    + "  config-set-key --provider openai --api-key <your-key>");
        }
        return red("Error: " + msg);
    }

    private String indent(String text, int spaces) {
        if (text == null) return "";
        String pad = " ".repeat(spaces);
        return text.lines().map(l -> pad + l).reduce((a, b) -> a + "\n" + b).orElse(text);
    }

    private String red(String t)    { return ANSI_RED    + t + ANSI_RESET; }
    private String green(String t)  { return ANSI_GREEN  + t + ANSI_RESET; }
    private String yellow(String t) { return ANSI_YELLOW + t + ANSI_RESET; }
    private String cyan(String t)   { return ANSI_CYAN   + t + ANSI_RESET; }
}
