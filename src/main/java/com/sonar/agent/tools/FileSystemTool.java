package com.sonar.agent.tools;

import com.sonar.agent.agent.models.FixProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class FileSystemTool {

    private static final Logger log = LoggerFactory.getLogger(FileSystemTool.class);
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public String readFile(String filePath) throws IOException {
        return Files.readString(Paths.get(filePath));
    }

    /**
     * Locates the original_snippet in the file and replaces it with fixed_snippet.
     * Creates a timestamped backup before modification.
     */
    public boolean applyFix(FixProposal proposal, String baseDirectory) {
        try {
            Path filePath = resolveFilePath(baseDirectory, proposal.filename());

            if (!Files.exists(filePath)) {
                log.error("File not found: {}", filePath);
                return false;
            }

            String content = Files.readString(filePath);
            String normalizedOriginal = normalizeLineEndings(proposal.originalSnippet());
            String normalizedContent = normalizeLineEndings(content);

            if (!normalizedContent.contains(normalizedOriginal)) {
                log.warn("Original snippet not found in {}", filePath.getFileName());
                log.debug("Snippet searched:\n{}", normalizedOriginal);
                return false;
            }

            createBackup(filePath);

            String fixedContent = normalizedContent.replace(
                    normalizedOriginal,
                    normalizeLineEndings(proposal.fixedSnippet())
            );

            // Preserve original line endings (CRLF on Windows)
            if (content.contains("\r\n")) {
                fixedContent = fixedContent.replace("\n", "\r\n");
            }

            Files.writeString(filePath, fixedContent);
            log.info("Fix applied to: {}", filePath);
            return true;

        } catch (IOException e) {
            log.error("Failed to apply fix to {}: {}", proposal.filename(), e.getMessage());
            return false;
        }
    }

    private void createBackup(Path filePath) throws IOException {
        String timestamp = LocalDateTime.now().format(BACKUP_TIMESTAMP);
        Path backupPath = Paths.get(filePath + ".backup." + timestamp);
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Backup created: {}", backupPath.getFileName());
    }

    private Path resolveFilePath(String baseDirectory, String filename) {
        Path file = Paths.get(filename);
        if (file.isAbsolute()) {
            return file;
        }
        return Paths.get(baseDirectory).resolve(file);
    }

    private String normalizeLineEndings(String text) {
        if (text == null) return "";
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }
}
