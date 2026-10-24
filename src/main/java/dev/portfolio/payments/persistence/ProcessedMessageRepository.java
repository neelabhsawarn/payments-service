package dev.portfolio.payments.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class ProcessedMessageRepository {
    private final JdbcTemplate jdbc;

    public ProcessedMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean recordIfFirst(UUID eventId) {
        return jdbc.update("""
                INSERT INTO processed_message(event_id) VALUES (?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId) == 1;
    }
}
