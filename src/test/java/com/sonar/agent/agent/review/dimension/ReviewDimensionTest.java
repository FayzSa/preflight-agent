package com.sonar.agent.agent.review.dimension;

import com.sonar.agent.agent.models.DiffResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDimensionTest {

    @Test
    void securityDimension_runsForCredentialAndSqlDiffs() {
        SecurityDimension dimension = new SecurityDimension();
        DiffResult diff = new DiffResult(
                "+String query = \"select * from users where password = \" + password;",
                List.of(new DiffResult.FileDiff("UserService.java", "+String query = ..."))
        );

        assertThat(dimension.shouldRun(diff)).isTrue();
    }

    @Test
    void performanceDimension_skipsUnrelatedTextDiff() {
        PerformanceDimension dimension = new PerformanceDimension();
        DiffResult diff = new DiffResult(
                "+String title = \"Hello\";",
                List.of(new DiffResult.FileDiff("Message.java", "+String title = \"Hello\";"))
        );

        assertThat(dimension.shouldRun(diff)).isFalse();
    }
}
