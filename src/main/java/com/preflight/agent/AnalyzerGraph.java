package com.preflight.agent;

import com.preflight.agent.models.AnalysisResponse;
import com.preflight.agent.models.DiffResult;
import com.preflight.agent.review.ReviewOrchestrator;
import com.preflight.tools.FileSystemTool;
import com.preflight.tools.GitOperationsTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the full Analyze -> Format -> Patch workflow.
 * <p>
 * Graph steps:
 * 1. Extract - GitOperationsTool fetches git diff -U5.
 * 2. Analyze - ReviewOrchestrator selects dimensions and asks the LLM.
 * 3. Format - Response is parsed into a typed AnalysisResponse.
 * 4. Patch - FileSystemTool applies each FixProposal to the local file.
 */
@Service
public class AnalyzerGraph {

    private static final Logger log = LoggerFactory.getLogger(AnalyzerGraph.class);

    private final GitOperationsTool gitTool;
    private final FileSystemTool fileSystemTool;
    private final ReviewOrchestrator reviewOrchestrator;

    public AnalyzerGraph(GitOperationsTool gitTool,
                         FileSystemTool fileSystemTool,
                         ReviewOrchestrator reviewOrchestrator) {
        this.gitTool = gitTool;
        this.fileSystemTool = fileSystemTool;
        this.reviewOrchestrator = reviewOrchestrator;
    }

    public AnalysisResponse analyze(String workingDirectory) {
        DiffResult diff = gitTool.getUncommittedChanges(workingDirectory);

        if (!diff.hasChanges()) {
            log.info("No uncommitted changes detected.");
            return new AnalysisResponse(List.of());
        }

        log.info("Diff captured: {} file(s) modified", diff.fileDiffs().size());
        return analyze(diff);
    }

    public AnalysisResponse analyze(DiffResult diff) {
        return reviewOrchestrator.review(diff);
    }

    public List<Boolean> applyFixes(AnalysisResponse response, String workingDirectory) {
        return response.issues().stream()
            .map(issue -> fileSystemTool.applyFix(issue, workingDirectory))
            .toList();
    }
}
