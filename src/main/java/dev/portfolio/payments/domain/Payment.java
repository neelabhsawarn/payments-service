package dev.portfolio.payments.domain;

import java.time.Instant;
import java.util.UUID;

public record Payment(
        UUID id,
        String idempotencyKey,
        String requestHash,
        long amountMinor,
        String currency,
        String merchantReference,
        String status,
        Instant createdAt
) {}
