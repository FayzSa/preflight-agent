package com.sonar.agent.tools;

import com.sonar.agent.agent.models.DiffResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GitOperationsTool {

    private static final Logger log = LoggerFactory.getLogger(GitOperationsTool.class);

    /**
     * Extracts all uncommitted changes (staged + unstaged) from the git repository.
     * Uses -U5 context lines to give the LLM enough surrounding code to understand intent.
     */
    public DiffResult getUncommittedChanges(String workingDirectory) {
        try {
            String stagedDiff = runGitCommand(workingDirectory, "git", "diff", "--cached", "-U5");
            String unstagedDiff = runGitCommand(workingDirectory, "git", "diff", "-U5");

            String combinedDiff = buildCombinedDiff(stagedDiff, unstagedDiff);

            if (combinedDiff.isBlank()) {
                return new DiffResult("", List.of());
            }

            return new DiffResult(combinedDiff, parseDiff(combinedDiff));

        } catch (Exception e) {
            log.error("Failed to get git diff: {}", e.getMessage());
            throw new RuntimeException("Failed to execute git diff: " + e.getMessage(), e);
        }
    }

    public boolean isGitRepository(String directory) {
        try {
            String output = runGitCommand(directory, "git", "rev-parse", "--is-inside-work-tree");
            return "true".equalsIgnoreCase(output.trim());
        } catch (Exception e) {
            return false;
        }
    }

    public String getRepositoryRoot(String directory) {
        try {
            return runGitCommand(directory, "git", "rev-parse", "--show-toplevel").trim();
        } catch (Exception e) {
            return directory;
        }
    }

    public void installPreCommitHook(String repoDirectory) throws Exception {
        Path hooksDir = Paths.get(repoDirectory, ".git", "hooks");
        if (!Files.exists(hooksDir)) {
            throw new IllegalStateException("Git hooks directory not found: " + hooksDir);
        }

        Path hookFile = hooksDir.resolve("pre-commit");

        String hookContent = """
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
                        echo "[ ai-fix ] Critical issues detected. Review and re-commit."
                        echo "[ ai-fix ] To skip (not recommended): git commit --no-verify"
                        exit 1
                    fi
                    echo "[ ai-fix ] Scan complete. Proceeding with commit."
                else
                    echo "[ ai-fix ] WARNING: ai-fix not found in PATH. Skipping AI review."
                    echo "[ ai-fix ] Install: https://github.com/fayzsa/sonar_agent"
                fi

                exit 0
                """;

        Files.writeString(hookFile, hookContent);
        hookFile.toFile().setExecutable(true);
        log.info("Pre-commit hook installed at: {}", hookFile);
    }

    private String runGitCommand(String directory, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(directory));
        pb.redirectErrorStream(false);

        Process process = pb.start();

        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        process.waitFor();
        return output;
    }

    private String buildCombinedDiff(String staged, String unstaged) {
        if (staged.isBlank() && unstaged.isBlank()) return "";
        if (staged.isBlank()) return unstaged;
        if (unstaged.isBlank()) return staged;

        // Deduplicate: if same file appears in both, prefer staged
        return staged + "\n" + unstaged;
    }

    private List<DiffResult.FileDiff> parseDiff(String rawDiff) {
        List<DiffResult.FileDiff> fileDiffs = new ArrayList<>();

        StringBuilder currentContent = new StringBuilder();
        String currentFile = null;

        for (String line : rawDiff.split("\n")) {
            if (line.startsWith("diff --git")) {
                if (currentFile != null) {
                    fileDiffs.add(new DiffResult.FileDiff(currentFile, currentContent.toString()));
                }
                currentContent = new StringBuilder();
                currentFile = null;
            } else if (line.startsWith("+++ b/")) {
                currentFile = line.substring(6);
            }
            currentContent.append(line).append("\n");
        }

        if (currentFile != null) {
            fileDiffs.add(new DiffResult.FileDiff(currentFile, currentContent.toString()));
        }

        return fileDiffs;
    }
}
