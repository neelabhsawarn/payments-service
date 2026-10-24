package dev.portfolio.payments.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;

    public record Unpublished(UUID eventId, UUID aggregateId, String payload) {}

    public OutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(UUID eventId, UUID paymentId, String payload) {
        jdbc.update("""
                INSERT INTO outbox_event(event_id, aggregate_id, event_type, payload)
                VALUES (?, ?, 'PaymentRequested', CAST(? AS jsonb))
                """, eventId, paymentId, payload);
    }

    // Invoked inside a transaction. SKIP LOCKED permits multiple publisher replicas.
    public List<Unpublished> lockPending(int limit) {
        return jdbc.query("""
                SELECT event_id, aggregate_id, payload::text AS payload
                FROM outbox_event WHERE published_at IS NULL
                ORDER BY created_at, event_id
                LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Unpublished((UUID) rs.getObject("event_id"),
                (UUID) rs.getObject("aggregate_id"), rs.getString("payload")), limit);
    }

    public void markPublished(UUID eventId) {
        jdbc.update("UPDATE outbox_event SET published_at = NOW() WHERE event_id = ?", eventId);
    }
}
