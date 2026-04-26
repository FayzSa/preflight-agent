package com.sonar.agent.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(60)
public class SolidDimension extends AbstractKeywordDimension {

    public SolidDimension() {
        super("class ", "interface ", "implements", "extends", "new ", "static", "public ",
                "private ", "service", "controller", "repository", "component");
    }

    @Override
    public String id() {
        return "SOLID";
    }

    @Override
    public String focus() {
        return """
                SOLID design failures visible in the diff: new god classes, hard-coded dependencies where
                dependency injection is already used, broken substitutions, oversized interfaces, and
                responsibilities mixed in a way that creates immediate maintenance risk.
                """;
    }

    @Override
    public String severityRules() {
        return "Do not flag SOLID issues unless the violation is directly visible in the diff and creates HIGH production risk.";
    }
}
