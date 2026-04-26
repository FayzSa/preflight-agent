package com.preflight.agent.review.prompt;

import com.preflight.agent.models.AnalysisResponse;

public final class OutputSchema {

    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";

    private OutputSchema() {
    }

    public static Class<AnalysisResponse> responseType() {
        return AnalysisResponse.class;
    }

    public static String schemaText() {
        return """
            Respond ONLY with valid JSON in this exact structure. Do not include markdown or prose.
            If no severe issues are found, return {"issues": []}.
            
            {
              "issues": [
                {
                  "filename": "path/to/file.ext",
                  "original_snippet": "exact problematic code from the diff",
                  "fixed_snippet": "corrected replacement code",
                  "issue_description": "precise explanation of the vulnerability, bug, regression, performance issue, or design risk",
                  "severity": "CRITICAL or HIGH",
                  "category": "SECURITY or CRITICAL_BUG or DATA_INTEGRITY or REGRESSION or PERFORMANCE or SOLID"
                }
              ]
            }
            """;
    }
}
