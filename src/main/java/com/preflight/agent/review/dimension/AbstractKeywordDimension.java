package com.preflight.agent.review.dimension;

import com.preflight.agent.models.DiffResult;

import java.util.Locale;

abstract class AbstractKeywordDimension implements ReviewDimension {

    private final String[] keywords;

    AbstractKeywordDimension(String... keywords) {
        this.keywords = keywords;
    }

    @Override
    public boolean shouldRun(DiffResult diff) {
        if (diff == null || !diff.hasChanges()) {
            return false;
        }
        if (keywords.length == 0) {
            return true;
        }

        String rawDiff = diff.rawDiff().toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (rawDiff.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
