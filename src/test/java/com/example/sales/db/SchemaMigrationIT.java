package com.example.sales.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/// Drops everything in the test schema and applies all migrations from scratch.
@SpringBootTest
@ActiveProfiles("test")
class SchemaMigrationIT {

    @Autowired
    Flyway flyway;

    @Test
    void migrationsApplyCleanlyOnEmptySchema() {
        flyway.clean();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.schemaName).isEqualTo("sales_online_test");
        assertThat(result.migrationsExecuted).isEqualTo(3);
        assertThat(flyway.info().pending()).isEmpty();
    }
}
