package com.preflight;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.shell.interactive.enabled=false",
        "spring.shell.script.enabled=false"
})
class MainContextTest {

    @Test
    void contextLoadsWithoutSelectedAiProvider() {
        // Provider keys are resolved when a review runs, not while the CLI starts.
    }
}
