package com.preflight.agent.review.dimension;

import com.preflight.agent.models.DiffResult;

public interface ReviewDimension {

    String id();

    String focus();

    String severityRules();

    boolean shouldRun(DiffResult diff);
}
