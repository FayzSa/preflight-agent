package com.sonar.agent;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.tools.GitOperationsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitOperationsToolTest {

    @TempDir
    Path tempDir;

    GitOperationsTool gitTool;

    @BeforeEach
    void setUp() {
        gitTool = new GitOperationsTool();
    }

    @Test
    void isGitRepository_returnsFalse_forPlainDirectory() {
        assertThat(gitTool.isGitRepository(tempDir.toString())).isFalse();
    }

    @Test
    void isGitRepository_returnsTrue_forGitRepo() throws Exception {
        runGit(tempDir, "git", "init");
        assertThat(gitTool.isGitRepository(tempDir.toString())).isTrue();
    }

    @Test
    void getRepositoryRoot_returnsDirectory_forNonRepo() {
        String result = gitTool.getRepositoryRoot(tempDir.toString());
        assertThat(result).isEqualTo(tempDir.toString());
    }

    @Test
    void getUncommittedChanges_returnsEmpty_whenNoChanges() throws Exception {
        initRepoWithCommit(tempDir);

        DiffResult result = gitTool.getUncommittedChanges(tempDir.toString());

        assertThat(result.hasChanges()).isFalse();
        assertThat(result.rawDiff()).isBlank();
    }

    @Test
    void getUncommittedChanges_returnsUnstagedDiff() throws Exception {
        initRepoWithCommit(tempDir);

        Path file = tempDir.resolve("App.java");
        Files.writeString(file, "int x = 99;\n");

        DiffResult result = gitTool.getUncommittedChanges(tempDir.toString());

        assertThat(result.hasChanges()).isTrue();
        assertThat(result.rawDiff()).contains("int x = 99;");
    }

    @Test
    void getUncommittedChanges_deduplicatesStagedAndUnstagedForSameFile() throws Exception {
        initRepoWithCommit(tempDir);

        Path file = tempDir.resolve("App.java");
        Files.writeString(file, "int x = 99;\n");
        runGit(tempDir, "git", "add", "App.java");
        // further unstaged modification to the same file
        Files.writeString(file, "int x = 99;\nint y = 1;\n");

        DiffResult result = gitTool.getUncommittedChanges(tempDir.toString());

        // The file should appear only once in the combined diff (staged version preferred)
        long occurrences = result.rawDiff().lines()
                .filter(l -> l.startsWith("+++ b/App.java"))
                .count();
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void installPreCommitHook_writesExecutableHookFile() throws Exception {
        runGit(tempDir, "git", "init");

        gitTool.installPreCommitHook(tempDir.toString());

        Path hookFile = tempDir.resolve(".git/hooks/pre-commit");
        assertThat(Files.exists(hookFile)).isTrue();
        assertThat(hookFile.toFile().canExecute()).isTrue();
        assertThat(Files.readString(hookFile)).contains("ai-fix fix --auto");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void initRepoWithCommit(Path dir) throws Exception {
        runGit(dir, "git", "init");
        runGit(dir, "git", "config", "user.email", "test@test.com");
        runGit(dir, "git", "config", "user.name", "Test");
        runGit(dir, "git", "config", "commit.gpgsign", "false");

        Path file = dir.resolve("App.java");
        Files.writeString(file, "int x = 1;\n");
        runGit(dir, "git", "add", "App.java");
        runGit(dir, "git", "commit", "-m", "initial");
    }

    private void runGit(Path dir, String... command) throws Exception {
        Process p = new ProcessBuilder(command)
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        p.waitFor();
    }
}
