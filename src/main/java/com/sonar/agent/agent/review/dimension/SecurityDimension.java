package com.sonar.agent.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class SecurityDimension extends AbstractKeywordDimension {

    public SecurityDimension() {
        super("password", "secret", "token", "auth", "jwt", "sql", "query", "exec", "command",
            "processbuilder", "runtime", "script", "html", "redirect", "url", "path", "xml",
            "deserialize", "cookie", "session", "csrf", "xss", "ssrf");
    }

    @Override
    public String id() {
        return "SECURITY";
    }

    @Override
    public String focus() {
        return """
            Security vulnerabilities: SQL injection, XSS, SSRF, command injection, hardcoded
            secrets or credentials, insecure deserialization, path traversal, XXE, open redirect,
            broken authentication, unsafe cryptography, and authorization bypasses.
            """;
    }

    @Override
    public String severityRules() {
        return "Report only exploitable vulnerabilities with direct impact as CRITICAL or HIGH.";
    }
}
