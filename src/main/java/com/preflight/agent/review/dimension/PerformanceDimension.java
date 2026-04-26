package com.preflight.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class PerformanceDimension extends AbstractKeywordDimension {

    public PerformanceDimension() {
        super("for", "while", "stream", "parallel", "query", "select", "join", "findall", "sleep",
            "synchronized", "lock", "cache", "thread", "executor", "bytes", "stringbuilder",
            "collect", "map");
    }

    @Override
    public String id() {
        return "PERFORMANCE";
    }

    @Override
    public String focus() {
        return """
            Performance hazards: N+1 queries, synchronous I/O in hot paths, blocking work on request
            threads, missing indexes on new queries, unbounded loops or allocations, avoidable quadratic
            work, and cache or concurrency mistakes that can make the application slow.
            """;
    }

    @Override
    public String severityRules() {
        return "Report only severe performance risks that are likely to affect production latency, throughput, or memory.";
    }
}
