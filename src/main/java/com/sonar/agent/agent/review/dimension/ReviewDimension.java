package com.sonar.agent.agent.review.dimension;

import com.sonar.agent.agent.models.DiffResult;

public interface ReviewDimension {

    String id();

    String focus();

    String severityRules();

    boolean shouldRun(DiffResult diff);
}
