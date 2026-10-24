# Architecture Decision Records (ADRs)

## ADR 001 — Java 21, Spring Boot, PostgreSQL, Flyway

**Decision:** Java 21 records for DTOs, Spring Boot for request validation, web endpoints, Kafka client and metrics. PostgreSQL is the source of truth; migrations use SQL-first Flyway.

**Trade-off:** Java/Spring is a larger runtime than a minimal HTTP service but fits enterprise backend patterns. Raw JDBC keeps queries explicit and makes unique constraints / `ON CONFLICT DO NOTHING` easy to audit; a richer entity model could justify JPA later.

## ADR 002 — Idempotency for POST

**Decision:** Require `Idempotency-Key`. Hash a deterministic JSON serialization of the validated request. A DB unique key chooses one winner; an identical retry returns the same resource (`200`), a different payload produces `409`. Atomic DB inserts ensure two simultaneous requests do not each create a payment.

**Trade-off:** The idempotency key has no TTL, merchant scope or authentication in this demo. Request hashing alone is not cryptographic client authentication. Keys become permanent until DB rows are purged; do not implement automatic TTL without an explicit client-retry contract.

## ADR 003 — Transactional outbox instead of dual-write

**Decision:** Payment and event are inserted in one PostgreSQL transaction. Outbox pollers `SELECT ... FOR UPDATE SKIP LOCKED` to distribute rows across instances. Only after broker acknowledgement is the event marked published.

**Why:** Writing DB then publishing Kafka outside one local transaction can crash between operations and permanently lose an event. A local DB transaction avoids that window.

**Trade-off:** Broker ACK and DB commit are not atomic; crash after ACK may duplicate. A synchronous `.get(10s)` inside a DB transaction limits throughput and holds locks. CDC or a dedicated publisher with lease/backoff is appropriate at scale.

## ADR 004 — Kafka producer/consumer, retries, DLT

**Decision:** Kafka asynchronous command topic `payments.requested.v1`, 1 partition locally, `acks=all`, idempotent producer. `DefaultErrorHandler` applies 3 exponential retries (initial 500ms, max 3s), then `DeadLetterPublishingRecoverer` sends to the same-partition `.DLT` topic.

**Why:** Temporary DB/consumer issues should not force synchronous HTTP calls. Poison messages must not block the topic indefinitely.

**Trade-off:** The DLT does not guarantee business recovery; it requires ownership, retention, alerts and a safe manual replay mechanism. This demo creates topics automatically; most production environments manage topics with infrastructure and quotas.

## ADR 005 — Idempotent worker (inbox pattern)

**Decision:** Insert `eventId` into the `processed_message` table using a PK constraint and transition payment status **in the same DB transaction**. When the event repeats, the PK conflict skips the transition.

**Why:** Outbox and Kafka are at-least-once. An offset may be redelivered after crash. The inbox prevents duplicate local effects.

**Trade-off:** Correctness applies to database effects within this transaction. Adding an external gateway call requires its own idempotency/transaction strategy (e.g. provider idempotency token, state machine, reconciliation) because the inbox alone cannot roll back external side effects.

## ADR 006 — Real integration tests

**Decision:** Use Testcontainers for PostgreSQL and Kafka, Spring Boot API tests, JUnit/Mockito for isolated units. Run automatically in GitHub Actions.

**Trade-off:** Slower than mocks-only tests and requires Docker. Higher confidence in SQL constraints, schema compatibility and message flow.

## ADR 007 — Observability and deployment

**Decision:** Micrometer counters, Prometheus scrape, auto-provisioned Grafana dashboard; Docker Compose for local stack; local kind/minikube deployment; Terraform AWS resources for an optional ephemeral cloud exercise.

**Trade-off:** Demo dashboards focus on throughput and basic resource health. Missing distributed tracing, Kafka lag alerts, stale-outbox alerts and SLOs are explicitly future work. Terraform intentionally does not provision expensive MSK; ECS is optional and requires an external reachable Kafka service.
