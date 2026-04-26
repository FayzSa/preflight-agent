package com.sonar.agent.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class RegressionDimension extends AbstractKeywordDimension {

    public RegressionDimension() {
        super("public ", "protected ", "api", "endpoint", "controller", "service", "interface",
                "record ", "class ", "enum ", "return", "throw", "config", "property", "json",
                "request", "response");
    }

    @Override
    public String id() {
        return "REGRESSION";
    }

    @Override
    public String focus() {
        return """
                Behavior regressions: changed public contracts, altered response shapes, changed error
                behavior, backwards-incompatible defaults, removed side effects callers depend on, and
                semantic changes that are visible to existing consumers.
                """;
    }

    @Override
    public String severityRules() {
        return "Report only visible behavior breaks with strong evidence from the diff.";
    }
}
