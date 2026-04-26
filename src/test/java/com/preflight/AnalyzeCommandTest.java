package com.preflight;

import com.preflight.agent.AnalyzerGraph;
import com.preflight.agent.models.AnalysisResponse;
import com.preflight.agent.models.FixProposal;
import com.preflight.command.AnalyzeCommand;
import com.preflight.tools.GitOperationsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeCommandTest {

    @Mock AnalyzerGraph analyzerGraph;
    @Mock GitOperationsTool gitTool;

    AnalyzeCommand command;

    @BeforeEach
    void setUp() {
        command = new AnalyzeCommand(analyzerGraph, gitTool);
    }

    @Test
    void scan_returnsCleanMessage_whenNoIssues() {
        when(gitTool.isGitRepository(anyString())).thenReturn(true);
        when(gitTool.getRepositoryRoot(anyString())).thenReturn("/repo");
        when(analyzerGraph.analyze("/repo")).thenReturn(new AnalysisResponse(List.of()));

        String result = command.scan(".");

        assertThat(result).contains("No critical issues found");
    }

    @Test
    void scan_returnsIssues_whenFoundByLLM() {
        FixProposal issue = new FixProposal(
                "UserService.java",
                "String query = \"SELECT * FROM users WHERE id = \" + userId;",
                "String query = \"SELECT * FROM users WHERE id = ?\";",
                "SQL injection", "CRITICAL", "SECURITY"
        );

        when(gitTool.isGitRepository(anyString())).thenReturn(true);
        when(gitTool.getRepositoryRoot(anyString())).thenReturn("/repo");
        when(analyzerGraph.analyze("/repo")).thenReturn(new AnalysisResponse(List.of(issue)));

        String result = command.scan(".");

        assertThat(result).contains("1 critical issue");
        assertThat(result).contains("UserService.java");
        assertThat(result).contains("CRITICAL");
        assertThat(result).contains("SECURITY");
    }

    @Test
    void scan_returnsError_whenNotGitRepository() {
        when(gitTool.isGitRepository(anyString())).thenReturn(false);

        String result = command.scan("/not/a/repo");

        assertThat(result).contains("Not a git repository");
    }

    @Test
    void fix_returnsCleanMessage_whenNoIssues() {
        when(gitTool.isGitRepository(anyString())).thenReturn(true);
        when(gitTool.getRepositoryRoot(anyString())).thenReturn("/repo");
        when(analyzerGraph.analyze("/repo")).thenReturn(new AnalysisResponse(List.of()));

        String result = command.fix(".", false);

        assertThat(result).isBlank();
    }

    @Test
    void fix_reportsAppliedAndFailedCounts() {
        FixProposal fixable = new FixProposal(
                "App.java", "old", "new", "desc", "CRITICAL", "SECURITY"
        );
        FixProposal unfixable = new FixProposal(
                "Other.java", "x", "y", "desc2", "HIGH", "CRITICAL_BUG"
        );
        AnalysisResponse response = new AnalysisResponse(List.of(fixable, unfixable));

        when(gitTool.isGitRepository(anyString())).thenReturn(true);
        when(gitTool.getRepositoryRoot(anyString())).thenReturn("/repo");
        when(analyzerGraph.analyze("/repo")).thenReturn(response);
        when(analyzerGraph.applyFixes(response, "/repo")).thenReturn(List.of(true, false));

        String result = command.fix(".", false);

        assertThat(result).contains("Applied 1 fix(es) successfully");
        assertThat(result).contains("1 fix(es) could not be applied");
    }

    @Test
    void installHook_returnsSuccess_forValidRepo() throws Exception {
        when(gitTool.isGitRepository(anyString())).thenReturn(true);
        when(gitTool.getRepositoryRoot(anyString())).thenReturn("/repo");

        String result = command.installHook(".");

        assertThat(result).contains("Pre-commit hook installed");
    }

    @Test
    void installHook_returnsError_forNonRepo() {
        when(gitTool.isGitRepository(anyString())).thenReturn(false);

        String result = command.installHook("/not/git");

        assertThat(result).contains("Not a git repository");
    }
}
