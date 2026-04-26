package com.preflight.agent.review;

import com.preflight.agent.models.AnalysisResponse;
import com.preflight.agent.models.DiffResult;
import com.preflight.agent.review.orchestration.ReviewStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReviewOrchestrator.class);

    private final ReviewStrategy reviewStrategy;

    public ReviewOrchestrator(ReviewStrategy reviewStrategy) {
        this.reviewStrategy = reviewStrategy;
    }

    public AnalysisResponse review(DiffResult diff) {
        if (diff == null || !diff.hasChanges()) {
            return new AnalysisResponse(List.of());
        }

        String language = detectLanguage(diff);
        log.info("Sending diff to LLM review strategy (language: {})...", language);
        return new AnalysisResponse(reviewStrategy.review(diff, language));
    }

    private String detectLanguage(DiffResult diff) {
        List<String> languages = diff.fileDiffs().stream()
            .map(DiffResult.FileDiff::filename)
            .map(this::mapExtensionToLanguage)
            .filter(lang -> !"Unknown".equals(lang))
            .distinct()
            .toList();

        if (languages.isEmpty()) {
            return "Unknown";
        }
        if (languages.size() == 1) {
            return languages.get(0);
        }
        return String.join(", ", languages);
    }

    private String mapExtensionToLanguage(String filename) {
        if (filename.endsWith(".java")) {
            return "Java";
        }
        if (filename.endsWith(".kt")) {
            return "Kotlin";
        }
        if (filename.endsWith(".js")) {
            return "JavaScript";
        }
        if (filename.endsWith(".ts") || filename.endsWith(".tsx")) {
            return "TypeScript";
        }
        if (filename.endsWith(".py")) {
            return "Python";
        }
        if (filename.endsWith(".go")) {
            return "Go";
        }
        if (filename.endsWith(".rs")) {
            return "Rust";
        }
        if (filename.endsWith(".cs")) {
            return "C#";
        }
        if (filename.endsWith(".php")) {
            return "PHP";
        }
        if (filename.endsWith(".rb")) {
            return "Ruby";
        }
        return "Unknown";
    }
}
