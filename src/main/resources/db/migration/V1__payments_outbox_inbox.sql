CREATE TABLE payment (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL,
    merchant_reference VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING', 'PROCESSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL REFERENCES payment(id),
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_event_unpublished ON outbox_event(created_at)
    WHERE published_at IS NULL;

CREATE TABLE processed_message (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
