package com.preflight.agent.review.orchestration;

import com.preflight.agent.models.DiffResult;
import com.preflight.agent.models.FixProposal;

import java.util.List;

public interface ReviewStrategy {

    List<FixProposal> review(DiffResult diff, String language);
}
