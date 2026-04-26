package com.sonar.agent.agent.review.orchestration;

import com.sonar.agent.agent.models.DiffResult;
import com.sonar.agent.agent.models.FixProposal;

import java.util.List;

public interface ReviewStrategy {

    List<FixProposal> review(DiffResult diff, String language);
}
