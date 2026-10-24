package dev.portfolio.payments.api;

import dev.portfolio.payments.messaging.PaymentRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentsIntegrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payments").withUsername("test").withPassword("test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("app.outbox.poll-delay-ms", () -> "150");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, String> kafka;

    private String url() {
        return "http://localhost:" + port + "/api/v1/payments";
    }

    private ResponseEntity<PaymentResponse> post(String key, long amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Idempotency-Key", key);
        return http.exchange(url(), HttpMethod.POST,
                new HttpEntity<>(new CreatePaymentRequest(amount, "GBP", "order-123"), headers),
                PaymentResponse.class);
    }

    @Test
    void persistsAtomicallyPublishesAndConsumesIdempotently() throws Exception {
        String key = "integration-" + UUID.randomUUID();
        var first = post(key, 1999);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertNotNull(first.getBody());
        UUID paymentId = first.getBody().id();

        var repeated = post(key, 1999);
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertNotNull(repeated.getBody());
        assertEquals(paymentId, repeated.getBody().id());
        assertEquals(HttpStatus.CONFLICT, post(key, 2000).getStatusCode());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var current = http.getForEntity(url() + "/" + paymentId, PaymentResponse.class);
            assertEquals("PROCESSED", current.getBody().status());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=? AND published_at IS NOT NULL", Long.class, paymentId));
        });

        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_event WHERE aggregate_id=?", String.class, paymentId);
        UUID eventId = UUID.fromString(jdbc.queryForObject(
                "SELECT event_id::text FROM outbox_event WHERE aggregate_id=?", String.class, paymentId));
        kafka.send("payments.requested.v1", paymentId.toString(), payload).get(10, TimeUnit.SECONDS);

        // Unique inbox key blocks duplicate side effects, including offset replay.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM processed_message WHERE event_id=?", Long.class, eventId)));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE id=?", Long.class, paymentId));
    }

    @Test
    void invalidPayloadReturnsBadRequest() {
        String key = "invalid-" + UUID.randomUUID();
        assertEquals(HttpStatus.BAD_REQUEST, post(key, 0).getStatusCode());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE idempotency_key=?", Long.class, key));
    }
}
