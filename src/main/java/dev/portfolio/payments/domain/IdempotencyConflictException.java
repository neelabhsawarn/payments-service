package dev.portfolio.payments.domain;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("This Idempotency-Key was already used with a different request payload");
    }
}
