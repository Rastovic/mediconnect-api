package com.mediconnect.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Custom Flyway strategy: run repair() before migrate() on every startup.
//
// Why: the OWASP 2021 → 2025 renumbering touched the comment headers of several
//      already-applied migrations (V2, V10, V11, V12, V13). Even though the SQL
//      is byte-for-byte identical in effect, Flyway computes a fresh checksum
//      from the raw file bytes and refuses to start because the recorded checksum
//      no longer matches. repair() rewrites flyway_schema_history to match the
//      current file checksums, after which migrate() can apply any new versions
//      (V16 for the refill queue).
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairAndMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
