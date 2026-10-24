package dev.portfolio.payments.persistence;

import dev.portfolio.payments.domain.Payment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PaymentRepository {
    private final JdbcTemplate jdbc;

    private static final RowMapper<Payment> ROW_MAPPER = new RowMapper<>() {
        @Override
        public Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Payment((UUID) rs.getObject("id"), rs.getString("idempotency_key"),
                    rs.getString("request_hash"), rs.getLong("amount_minor"),
                    rs.getString("currency"), rs.getString("merchant_reference"),
                    rs.getString("status"), rs.getTimestamp("created_at").toInstant());
        }
    };

    public PaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean insertIfAbsent(Payment payment) {
        return jdbc.update("""
                INSERT INTO payment(id, idempotency_key, request_hash, amount_minor, currency,
                                    merchant_reference, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, payment.id(), payment.idempotencyKey(), payment.requestHash(),
                payment.amountMinor(), payment.currency(), payment.merchantReference(), payment.status()) == 1;
    }

    public Optional<Payment> findById(UUID id) {
        return jdbc.query("SELECT * FROM payment WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<Payment> findByIdempotencyKey(String key) {
        return jdbc.query("SELECT * FROM payment WHERE idempotency_key = ?", ROW_MAPPER, key)
                .stream().findFirst();
    }

    public void markProcessed(UUID paymentId) {
        int count = jdbc.update("""
                UPDATE payment SET status = 'PROCESSED', updated_at = NOW()
                WHERE id = ? AND status = 'PENDING'
                """, paymentId);
        if (count != 1) {
            throw new IllegalStateException("Expected exactly one PENDING payment: " + paymentId);
        }
    }
}
