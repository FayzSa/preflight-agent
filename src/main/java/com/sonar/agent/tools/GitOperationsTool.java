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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            String root = runGitCommand(directory, "git", "rev-parse", "--show-toplevel").trim();
            return Paths.get(root).toAbsolutePath().normalize()
                    .equals(Paths.get(directory).toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    public String getRepositoryRoot(String directory) {
        try {
            if (!isGitRepository(directory)) {
                return directory;
            }
            return runGitCommand(directory, "git", "rev-parse", "--show-toplevel").trim();
        } catch (Exception e) {
            return directory;
        }
    }

    public DiffResult buildFromRawDiff(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            return new DiffResult("", List.of());
        }
        return new DiffResult(rawDiff, parseDiff(rawDiff));
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
        String errorOutput;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            output = reader.lines().collect(Collectors.joining("\n"));
            errorOutput = errorReader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = errorOutput.isBlank() ? "exit code " + exitCode : errorOutput;
            throw new RuntimeException("Git command failed: " + errorMsg);
        }
        return output;
    }

    private String buildCombinedDiff(String staged, String unstaged) {
        if (staged.isBlank() && unstaged.isBlank()) return "";
        if (staged.isBlank()) return unstaged;
        if (unstaged.isBlank()) return staged;

        // Prefer staged version when the same file appears in both diffs
        Set<String> stagedFiles = extractFilenames(staged);
        String filteredUnstaged = filterOutFiles(unstaged, stagedFiles);
        return filteredUnstaged.isBlank() ? staged : staged + "\n" + filteredUnstaged;
    }

    private Set<String> extractFilenames(String diff) {
        Set<String> files = new HashSet<>();
        for (String line : diff.split("\n")) {
            if (line.startsWith("+++ b/")) {
                files.add(line.substring(6));
            }
        }
        return files;
    }

    private String filterOutFiles(String diff, Set<String> excludeFiles) {
        StringBuilder result = new StringBuilder();
        for (String section : diff.split("(?=diff --git )")) {
            boolean excluded = excludeFiles.stream().anyMatch(f -> section.contains("+++ b/" + f));
            if (!excluded && !section.isBlank()) {
                result.append(section);
            }
        }
        return result.toString().trim();
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
