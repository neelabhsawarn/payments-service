package dev.portfolio.payments.domain;

public class InvalidIdempotencyKeyException extends RuntimeException {
    public InvalidIdempotencyKeyException() {
        super("Idempotency-Key must contain 1–128 characters: A-Z, a-z, 0-9, dot, underscore, colon or hyphen");
    }
}
