package com.sonar.agent.agent.models;

import java.util.List;

public record DiffResult(
    String rawDiff,
    List<FileDiff> fileDiffs
) {
    public record FileDiff(
        String filename,
        String content
    ) {
    }

    public boolean hasChanges() {
        return rawDiff != null && !rawDiff.isBlank();
    }
}
