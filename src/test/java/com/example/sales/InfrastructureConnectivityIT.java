package com.example.sales;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Verifies that the development infrastructure is reachable:
/// PostgreSQL with the `vector` extension, Redis and Kafka.
@SpringBootTest
@ActiveProfiles("test")
class InfrastructureConnectivityIT {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    KafkaAdmin kafkaAdmin;

    @Test
    void postgresHasVectorExtension() {
        var version = jdbc.sql("select extversion from pg_extension where extname = 'vector'")
                .query(String.class)
                .optional();

        assertThat(version).isPresent();
    }

    @Test
    void redisAnswersPing() {
        try (var connection = redis.getRequiredConnectionFactory().getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

    @Test
    void kafkaClusterIsReachable() throws Exception {
        try (var admin = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            var nodes = admin.describeCluster().nodes().get(10, TimeUnit.SECONDS);

            assertThat(nodes).isNotEmpty();
        }
    }
}
