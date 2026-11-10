package com.example.alerting;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;

/**
 * Walking-skeleton checks for milestone M1: the application starts on a developer machine with no
 * setup, Actuator is served on its own internal port and exposes only the intended endpoints, and
 * Flyway is wired to the migration folder.
 */
@IntegrationTest
class WalkingSkeletonTest {

    private static final String HEALTH = "/actuator/health";

    @Value("${local.server.port}")
    private int serverPort;

    @Value("${local.management.port}")
    private int managementPort;

    @Autowired
    private Environment environment;

    @Autowired
    private Flyway flyway;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void startsWithTheLocalProfile() {
        assertThat(environment.getActiveProfiles()).contains("local");
    }

    @Test
    void healthIsUpOnTheManagementPort() throws Exception {
        HttpResponse<String> response = get(managementPort, HEALTH);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        assertThat(get(managementPort, HEALTH + "/liveness").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, HEALTH + "/readiness").statusCode()).isEqualTo(200);
    }

    @Test
    void actuatorIsNotServedOnTheMainPort() throws Exception {
        assertThat(get(serverPort, HEALTH).statusCode()).isEqualTo(404);
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        assertThat(get(managementPort, "/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get(managementPort, "/actuator/beans").statusCode()).isEqualTo(404);
    }

    @Test
    void prometheusEndpointExposesJvmMetrics() throws Exception {
        HttpResponse<String> response = get(managementPort, "/actuator/prometheus");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void flywayIsWiredToTheMigrationFolder() {
        assertThat(flyway.getConfiguration().getLocations())
                .extracting(Object::toString)
                .contains("classpath:db/migration");
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
