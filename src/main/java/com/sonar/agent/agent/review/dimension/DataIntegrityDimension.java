package com.sonar.agent.agent.review.dimension;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class DataIntegrityDimension extends AbstractKeywordDimension {

    public DataIntegrityDimension() {
        super("transaction", "commit", "rollback", "save", "delete", "update", "insert", "merge",
                "migration", "schema", "index", "id", "timestamp", "timezone", "round", "truncate",
                "serialize", "deserialize", "mapper");
    }

    @Override
    public String id() {
        return "DATA_INTEGRITY";
    }

    @Override
    public String focus() {
        return """
                Data integrity failures: silent data loss, unsafe type conversion, broken transaction
                boundaries, incorrect persistence semantics, off-by-one errors that drop or duplicate
                records, stale writes, and schema or migration mistakes.
                """;
    }

    @Override
    public String severityRules() {
        return "Report only issues that can corrupt, lose, duplicate, or persist incorrect data.";
    }
}
