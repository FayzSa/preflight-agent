package com.sonar.agent;

import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.tools.FileSystemTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileSystemToolTest {

    @TempDir
    Path tempDir;

    FileSystemTool fileSystemTool;

    @BeforeEach
    void setUp() {
        fileSystemTool = new FileSystemTool();
        ReflectionTestUtils.setField(fileSystemTool, "backupEnabled", true);
    }

    @Test
    void applyFix_replacesSnippetAndCreatesBackup() throws Exception {
        Path file = tempDir.resolve("UserService.java");
        Files.writeString(file, "String query = \"SELECT * FROM users WHERE id = \" + userId;\n");

        FixProposal proposal = new FixProposal(
                file.getFileName().toString(),
                "String query = \"SELECT * FROM users WHERE id = \" + userId;",
                "String query = \"SELECT * FROM users WHERE id = ?\";",
                "SQL injection", "CRITICAL", "SECURITY"
        );

        boolean result = fileSystemTool.applyFix(proposal, tempDir.toString());

        assertThat(result).isTrue();
        String updated = Files.readString(file);
        assertThat(updated).contains("SELECT * FROM users WHERE id = ?");
        assertThat(updated).doesNotContain("+ userId");

        long backupCount = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().contains(".backup."))
                .count();
        assertThat(backupCount).isEqualTo(1);
    }

    @Test
    void applyFix_returnsFalse_whenFileDoesNotExist() {
        FixProposal proposal = new FixProposal(
                "nonexistent.java", "old", "new", "desc", "HIGH", "SECURITY"
        );

        boolean result = fileSystemTool.applyFix(proposal, tempDir.toString());

        assertThat(result).isFalse();
    }

    @Test
    void applyFix_returnsFalse_whenSnippetNotFound() throws Exception {
        Path file = tempDir.resolve("App.java");
        Files.writeString(file, "int x = 1;\n");

        FixProposal proposal = new FixProposal(
                file.getFileName().toString(),
                "this snippet does not exist",
                "replacement",
                "desc", "HIGH", "SECURITY"
        );

        boolean result = fileSystemTool.applyFix(proposal, tempDir.toString());

        assertThat(result).isFalse();
        assertThat(Files.readString(file)).isEqualTo("int x = 1;\n");
    }

    @Test
    void applyFix_skipsBackup_whenBackupDisabled() throws Exception {
        ReflectionTestUtils.setField(fileSystemTool, "backupEnabled", false);

        Path file = tempDir.resolve("Main.java");
        Files.writeString(file, "String s = null;\n");

        FixProposal proposal = new FixProposal(
                file.getFileName().toString(),
                "String s = null;",
                "String s = \"\";",
                "null ref", "HIGH", "CRITICAL_BUG"
        );

        boolean result = fileSystemTool.applyFix(proposal, tempDir.toString());

        assertThat(result).isTrue();
        long backupCount = Files.list(tempDir)
                .filter(p -> p.getFileName().toString().contains(".backup."))
                .count();
        assertThat(backupCount).isEqualTo(0);
    }

    @Test
    void applyFix_handlesCrlfLineEndings() throws Exception {
        Path file = tempDir.resolve("App.java");
        Files.writeString(file, "int x = 1;\r\nint y = 2;\r\n");

        FixProposal proposal = new FixProposal(
                file.getFileName().toString(),
                "int x = 1;",
                "int x = 42;",
                "magic number", "HIGH", "CRITICAL_BUG"
        );

        boolean result = fileSystemTool.applyFix(proposal, tempDir.toString());

        assertThat(result).isTrue();
        String updated = Files.readString(file);
        assertThat(updated).contains("int x = 42;");
        assertThat(updated).contains("\r\n");
    }

    @Test
    void readFile_returnsFileContents() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello world");

        String content = fileSystemTool.readFile(file.toString());

        assertThat(content).isEqualTo("hello world");
    }
}
