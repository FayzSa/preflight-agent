package com.sonar.agent.agent.review.prompt;

public sealed interface PromptSection
    permits PromptSection.Role, PromptSection.Task, PromptSection.Rules,
    PromptSection.OutputSchema, PromptSection.DimensionFocus {

    String render();

    record Role(String content) implements PromptSection {
        @Override
        public String render() {
            return content;
        }
    }

    record Task(String content) implements PromptSection {
        @Override
        public String render() {
            return content;
        }
    }

    record Rules(String content) implements PromptSection {
        @Override
        public String render() {
            return content;
        }
    }

    record OutputSchema(String content) implements PromptSection {
        @Override
        public String render() {
            return content;
        }
    }

    record DimensionFocus(String content) implements PromptSection {
        @Override
        public String render() {
            return content;
        }
    }
}
