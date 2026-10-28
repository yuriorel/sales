package com.example.sales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/// Starts the full application and checks that `/actuator/health` reports `UP`
/// with the database and Redis components included.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HealthEndpointIT {

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIsUp() {
        var body = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve()
                .body(String.class);

        assertThat(body)
                .contains("\"status\":\"UP\"")
                .contains("\"db\"")
                .contains("\"redis\"");
    }
}
