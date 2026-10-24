# Failure Modes and Expected Behaviour

| Scenario | Expected behaviour | Why it works / limitation |
|---|---|---|
| Two requests with the same key arrive concurrently | Exactly one new `payment` and `outbox_event`; winner gets `201`, loser `200` | PostgreSQL unique constraint and `ON CONFLICT DO NOTHING` |
| Same key, modified amount | `409 Conflict`; no new event | Original stored SHA-256 request fingerprint is compared |
| Client times out after HTTP transaction commits | Retrying with same key returns same UUID | Idempotency row survives network timeout |
| App crashes before transaction commits | Neither payment nor event exists | Single DB transaction rolls back |
| Kafka down after new payment commits | Request is still `201`; payment remains `PENDING` until Kafka recovers | Outbox record remains available; schedule retries |
| Publisher receives ACK and dies before outbox DB commit | Event can be emitted twice | This is at-least-once publishing, not exactly-once |
| Two publisher instances poll at the same time | No two instances lock the same pending outbox row in the same instant | `FOR UPDATE SKIP LOCKED`; broker ACK/DB crash window remains |
| Kafka delivers an event twice | Only one `processed_message` row and payment transition | Inbox + payment transition are transactional |
| Worker DB transaction fails after inserting inbox row | Both inbox insert and payment change roll back; retry can succeed | Atomic rollback via `@Transactional` |
| Worker commits DB but crashes before offset commit | Kafka redelivers; inbox prevents duplicate local effect | Kafka offset and DB commit do not share a transaction |
| JSON cannot be deserialised / unexpected worker error | Bounded retries then send to `*.DLT` | Needs operational alert, investigation, manual replay policy |
| DB is permanently down | Ingestion fails; poller cannot run; consumer retries to DLT | No business guarantee; requires operational recovery and compensating processes |
| Kafka DLT itself cannot accept writes | Recovery may fail; consumer logs error and attempts recovery again | Do not silently drop poison messages |
| Local Kubernetes dependency pod restarts | Kafka/PostgreSQL data may disappear | `emptyDir` deliberately not durable; production requires storage/backups |
| ECS starts with no reachable Kafka broker | Worker/publisher cannot process events; service is not end-to-end operational | Terraform needs an external reachable Kafka cluster; no MSK in example |

## Test priorities before production

- Concurrent same-key POST stress test using independent database connections.
- True poisoned payload DLT end-to-end test with error headers and replay verification.
- Chaos testing at the Kafka acknowledgement / DB commit boundary.
- Observable stale-outbox age and consumer lag with actionable alerts.
- Credential rotation, TLS/SASL Kafka client auth and secret management.
- Payment state machine and external gateway idempotency / reconciliation.
