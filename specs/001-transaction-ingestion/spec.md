# Feature 001 — Transaction ingestion API

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 000 |
| Concepts | [Event-driven decoupling with Kafka](../../docs/concepts/01-event-driven-kafka.md), [Virtual threads](../../docs/concepts/07-virtual-threads.md) |

## 1. Problem / motivation
Payment gateways need to hand transactions to the fraud platform quickly and reliably. Scoring is slower and more variable than ingestion, so the two must be **decoupled**. Ingestion's job is to validate, make the transaction durable in Kafka, and acknowledge it, and nothing more.

## 2. User stories
- **US-1** As a payment gateway, I want to submit a transaction and get an acknowledgement once it is durably accepted, so that I know it will be scored.
- **US-2** As a payment gateway, I want precise validation errors, so that I can fix bad payloads without contacting support.
- **US-3** As the scoring service, I want every transaction for an account delivered in order, so that velocity rules are correct.

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-001-01 | **Given** a valid transaction, **when** it is POSTed to `/api/v1/transactions`, **then** the response is `202 Accepted` with `transactionId`, a generated `eventId`, `status=ACCEPTED` and `receivedAt`. |
| AC-001-02 | **Given** a valid transaction, **when** it is accepted, **then** a `TransactionReceivedEvent` (schemaVersion 1) is published to `transactions.received.v1` with record key = `accountId` and headers `event-type` and `schema-version`. |
| AC-001-03 | **Given** a payload with missing or invalid fields, **when** it is POSTed, **then** the response is `400` `application/problem+json` listing **every** invalid field, and nothing is published. |
| AC-001-04 | **Given** malformed JSON, **when** it is POSTed, **then** the response is `400` ProblemDetail and nothing is published. |
| AC-001-05 | **Given** `occurredAt` is more than 5 minutes in the future (clock skew tolerance), **when** it is POSTed, **then** the response is `400` and nothing is published. |
| AC-001-06 | **Given** Kafka does not acknowledge within the publish timeout, **when** a valid transaction is POSTed, **then** the response is `503` ProblemDetail with a `Retry-After` header. The client must never see `202` for a transaction that was not persisted. |
| AC-001-07 | **Given** an unknown ISO-4217 currency or ISO-3166 country code, **when** it is POSTed, **then** the response is `400` naming the field. |
| AC-001-08 | **Given** several transactions for the same account, **when** they are published, **then** they all land on the same partition (ordering guarantee). |

### Request
```json
POST /api/v1/transactions
{
  "transactionId": "txn-7f3a9c",
  "accountId": "acc-1001",
  "amount": 249.99,
  "currency": "USD",
  "merchantId": "m-5541",
  "merchantCategoryCode": "5732",
  "country": "US",
  "channel": "CARD_NOT_PRESENT",
  "occurredAt": "2026-10-09T18:15:30Z"
}
```

| Field | Rule |
|-------|------|
| transactionId | required, 1–64 chars, `[A-Za-z0-9_-]` |
| accountId | required, 1–64 chars, `[A-Za-z0-9_-]` |
| amount | required, > 0, ≤ 1,000,000, max 2 fraction digits |
| currency | required, valid ISO-4217 code |
| merchantId | required, 1–64 chars |
| merchantCategoryCode | required, exactly 4 digits |
| country | required, valid ISO-3166 alpha-2 |
| channel | required, one of `CARD_PRESENT`, `CARD_NOT_PRESENT`, `CONTACTLESS`, `ATM` |
| occurredAt | required, ISO-8601 instant, not more than 5 min in the future |

### Response `202`
```json
{ "transactionId": "txn-7f3a9c", "eventId": "0b8e…", "status": "ACCEPTED", "receivedAt": "2026-10-09T18:15:30.120Z" }
```

## 4. Non-functional requirements
| Category | Requirement |
|----------|-------------|
| Durability | `acks=all`, idempotent producer. `202` only after the broker acknowledges. |
| Latency | p99 < 50 ms at steady state (verified in Feature 013) |
| Concurrency | Request handling on virtual threads, so blocking on the Kafka ack is cheap |
| Security / PII | No PAN accepted or logged. Only opaque account and merchant IDs. |
| Observability | Actuator health (liveness/readiness) |

## 5. Out of scope
- Rate limiting and idempotency keys (Feature 002)
- Authentication (Feature 015)
- Synchronous fraud decision in the response
