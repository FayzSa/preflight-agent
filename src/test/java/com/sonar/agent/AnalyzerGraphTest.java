package com.sonar.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sonar.agent.agent.AnalyzerGraph;
import com.sonar.agent.agent.models.AnalysisResponse;
import com.sonar.agent.agent.models.FixProposal;
import com.sonar.agent.tools.FileSystemTool;
import com.sonar.agent.tools.GitOperationsTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzerGraphTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec responseSpec;
    @Mock GitOperationsTool gitTool;
    @Mock FileSystemTool fileSystemTool;

    AnalyzerGraph analyzerGraph;
    ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        analyzerGraph = new AnalyzerGraph(chatClient, gitTool, fileSystemTool, objectMapper);
    }

    @Test
    void analyze_returnsEmptyResponse_whenNoDiff() {
        when(gitTool.getUncommittedChanges(anyString()))
                .thenReturn(new com.sonar.agent.agent.models.DiffResult("", List.of()));

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isFalse();
        verifyNoInteractions(chatClient);
    }

    @Test
    void analyze_parsesLlmResponseCorrectly() {
        var diff = new com.sonar.agent.agent.models.DiffResult(
                "+String query = \"SELECT * FROM users WHERE id = \" + userId;",
                List.of(new com.sonar.agent.agent.models.DiffResult.FileDiff(
                        "UserService.java",
                        "+String query = \"SELECT * FROM users WHERE id = \" + userId;"
                ))
        );

        String llmJson = """
                {
                  "issues": [{
                    "filename": "UserService.java",
                    "original_snippet": "String query = \\"SELECT * FROM users WHERE id = \\" + userId;",
                    "fixed_snippet": "String query = \\"SELECT * FROM users WHERE id = ?\\";",
                    "issue_description": "SQL injection vulnerability via string concatenation",
                    "severity": "CRITICAL",
                    "category": "SECURITY"
                  }]
                }
                """;

        when(gitTool.getUncommittedChanges(anyString())).thenReturn(diff);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(llmJson);

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isTrue();
        assertThat(response.issues()).hasSize(1);

        FixProposal issue = response.issues().get(0);
        assertThat(issue.severity()).isEqualTo("CRITICAL");
        assertThat(issue.category()).isEqualTo("SECURITY");
        assertThat(issue.filename()).isEqualTo("UserService.java");
    }

    @Test
    void analyze_returnsEmptyResponse_whenLlmReturnsNoIssues() {
        var diff = new com.sonar.agent.agent.models.DiffResult(
                "+int x = 1;",
                List.of(new com.sonar.agent.agent.models.DiffResult.FileDiff("Main.java", "+int x = 1;"))
        );

        when(gitTool.getUncommittedChanges(anyString())).thenReturn(diff);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn("{\"issues\":[]}");

        AnalysisResponse response = analyzerGraph.analyze("/some/repo");

        assertThat(response.hasIssues()).isFalse();
    }

    @Test
    void applyFixes_delegatesToFileSystemTool() {
        FixProposal fix = new FixProposal(
                "App.java", "old code", "new code",
                "SQL injection", "CRITICAL", "SECURITY"
        );
        AnalysisResponse response = new AnalysisResponse(List.of(fix));

        when(fileSystemTool.applyFix(fix, "/repo")).thenReturn(true);

        List<Boolean> results = analyzerGraph.applyFixes(response, "/repo");

        assertThat(results).containsExactly(true);
        verify(fileSystemTool).applyFix(fix, "/repo");
    }
}
