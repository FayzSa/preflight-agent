package com.sonar.agent.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class CriticalBugDimension extends AbstractKeywordDimension {

    public CriticalBugDimension() {
        super("null", "optional", "close", "stream", "connection", "lock", "synchronized",
                "thread", "executor", "while", "for", "overflow", "divide", "parse", "cast");
    }

    @Override
    public String id() {
        return "CRITICAL_BUG";
    }

    @Override
    public String focus() {
        return """
                Critical correctness failures: null pointer dereferences, resource leaks, race
                conditions, integer overflow or underflow, infinite loops, deadlocks, invalid casts,
                and exceptions that will break normal production flows.
                """;
    }

    @Override
    public String severityRules() {
        return "Report only defects likely to crash, corrupt execution, leak resources, or block production use.";
    }
}
