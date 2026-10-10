# Architecture

This document describes the **target** architecture. Each feature spec in [`/specs`](../../specs) delivers one slice of it, and the status column in the [roadmap](../../specs/README.md) shows what exists today.

- [1. System context](#1-system-context)
- [2. Containers](#2-containers)
- [3. Event flow & Kafka topology](#3-event-flow--kafka-topology)
- [4. Scoring pipeline (sequence)](#4-scoring-pipeline-sequence)
- [5. Alert review with two-layer concurrency control (sequence)](#5-alert-review-with-two-layer-concurrency-control-sequence)
- [6. Hexagonal service layout](#6-hexagonal-service-layout)
- [7. Data model](#7-data-model)
- [8. Redis key space](#8-redis-key-space)
- [9. Deployment](#9-deployment)
- [10. Quality attributes](#10-quality-attributes)

---

## 1. System context

```mermaid
flowchart LR
    subgraph External
        PG[Payment Gateway /<br/>Card Network]
        AN[Fraud Analyst]
        OPS[SRE / On-call]
    end

    FP[[Real-Time Fraud Detection<br/>& Alerting Platform]]

    PG -- "card transactions (REST)" --> FP
    FP -- "accept / decision ack" --> PG
    AN -- "reviews alerts, confirms fraud (browser)" --> FP
    FP -- "live alerts (SSE)" --> AN
    OPS -- "dashboards, alerts" --> FP
```

## 2. Containers

```mermaid
flowchart TB
    client([Payment Gateway])
    browser([Analyst Browser])

    subgraph edge [Edge]
        lb[NGINX<br/>load balancer<br/>round-robin / least_conn]
    end

    subgraph services [Spring Boot 4 · Java 21 · virtual threads]
        ing1[ingestion-service #1]
        ing2[ingestion-service #2]
        sc[scoring-service<br/>consumer group: scoring]
        al[alert-service<br/>REST + SSE]
    end

    ui[dashboard<br/>React + Vite + TS]

    subgraph data [Data plane]
        k[(Apache Kafka<br/>KRaft)]
        r[(Redis<br/>rate limits · idempotency ·<br/>velocity · high-risk cache · locks)]
        db[(PostgreSQL<br/>transactions · scores · alerts)]
    end

    subgraph obs [Observability]
        prom[Prometheus]
        graf[Grafana]
    end

    client -->|HTTPS| lb
    browser --> ui
    ui -->|REST / SSE| lb
    lb --> ing1 & ing2
    lb --> al

    ing1 & ing2 -->|token bucket, idempotency| r
    ing1 & ing2 -->|transactions.received.v1| k
    k -->|transactions.received.v1| sc
    sc -->|velocity, risk cache| r
    sc -->|JDBC batch| db
    sc -->|fraud.alerts.v1| k
    k -->|fraud.alerts.v1| al
    al -->|JPA| db
    al -->|distributed lock, risk cache| r

    prom -.scrape /actuator/prometheus.-> ing1 & ing2 & sc & al
    graf -.-> prom
```

| Container | Responsibility | Scales by |
|-----------|----------------|-----------|
| **ingestion-service** | Validates transactions, enforces per-client rate limits and idempotency, publishes to Kafka. Stateless. | Instances behind the LB |
| **scoring-service** | Consumes transactions and runs **rule-based + ML** scoring. Persists the result with JDBC batching, emits alerts, and keeps the high-risk account cache up to date. | Kafka partitions (one consumer thread per partition) |
| **alert-service** | Consumes alerts and serves the analyst API: paginated queries, the alert lifecycle with concurrent-write protection, and an SSE push stream. | Instances behind the LB |
| **dashboard** | React SPA: live alert feed, alert queue, review actions, KPIs. | CDN / static hosting |

**Why separate services?** Ingestion has to stay fast and available even when scoring is slow, and Kafka between them absorbs bursts. The two sides also have different scaling dimensions (HTTP connections vs partitions). See [ADR-0003](../adr/0003-service-decomposition.md).

## 3. Event flow & Kafka topology

```mermaid
flowchart LR
    subgraph producers
        ING[ingestion-service]
        SC2[scoring-service]
    end
    subgraph kafka [Kafka cluster]
        T1["transactions.received.v1<br/>12 partitions · key = accountId<br/>retention 7d"]
        T2["fraud.alerts.v1<br/>6 partitions · key = accountId"]
        T3["transactions.scored.v1<br/>12 partitions · key = accountId"]
        DLT["transactions.received.v1.DLT"]
    end
    subgraph consumers
        SCG["scoring-service<br/>group: scoring"]
        ALG["alert-service<br/>group: alerts"]
        ANA["(future) analytics<br/>group: analytics"]
    end

    ING --> T1 --> SCG
    SCG -. poison pill after retries .-> DLT
    SC2 --> T2 --> ALG
    SC2 --> T3 --> ANA
```

**Key design choices**

| Choice | Reason |
|--------|--------|
| Partition key = `accountId` | All events for one account land on one partition, so they are processed **in order** by one consumer. Velocity rules depend on that ordering. |
| Producer `acks=all`, `enable.idempotence=true` | No message loss, and no duplicates caused by producer retries. |
| Versioned topic names (`.v1`) | Breaking schema changes go to a new topic, and consumers migrate on their own schedule. |
| JSON payloads with a `schemaVersion` field, and contracts in the shared `common` module | Simple and readable. A schema registry with Avro or Protobuf is the documented upgrade path ([ADR-0002](../adr/0002-kafka-as-event-backbone.md)). |
| Consumer is idempotent (dedupe on `eventId`) | Kafka gives **at-least-once** delivery. Combined with idempotent processing, the effect is exactly-once. |

## 4. Scoring pipeline (sequence)

```mermaid
sequenceDiagram
    autonumber
    participant GW as Payment Gateway
    participant LB as NGINX
    participant ING as ingestion-service
    participant R as Redis
    participant K as Kafka
    participant SC as scoring-service
    participant DB as PostgreSQL
    participant AL as alert-service
    participant UI as Dashboard

    GW->>LB: POST /api/v1/transactions (Idempotency-Key)
    LB->>ING: round-robin
    ING->>R: EVALSHA token_bucket.lua (clientId)
    alt bucket empty
        ING-->>GW: 429 Too Many Requests + Retry-After
    end
    ING->>R: SET idem:{key} NX EX 86400
    alt duplicate key
        ING-->>GW: 202 (original transactionId, no republish)
    end
    ING->>K: send(transactions.received.v1, key=accountId) acks=all
    K-->>ING: ack (partition, offset)
    ING-->>GW: 202 Accepted {transactionId}

    K->>SC: poll batch (max.poll.records=500)
    par per record on virtual threads
        SC->>R: GET risk:account:{id} / ZADD velocity:{id}
        SC->>SC: RuleEngine.evaluate() — rules in parallel
        SC->>SC: MlScorer.score() — Semaphore-bounded
    end
    SC->>DB: ONE tx: batch INSERT transactions + scores + outbox(alerts)
    SC->>R: SET risk:account:{id} for DECLINEs (TTL + jitter)
    SC->>K: commit offsets (after DB write)
    loop outbox relay (single active instance)
        SC->>DB: read outbox in order
        SC->>K: send(fraud.alerts.v1), await acks
        SC->>DB: delete relayed rows
    end

    K->>AL: fraud.alerts.v1
    AL->>DB: INSERT alert (ON CONFLICT DO NOTHING)
    AL-->>UI: SSE event: alert.created
```

## 5. Alert review with two-layer concurrency control (sequence)

Two analysts can open the same alert at the same moment. The platform must never lose an update, and it must never let both of them "win".

```mermaid
sequenceDiagram
    autonumber
    participant A1 as Analyst A
    participant A2 as Analyst B
    participant AL as alert-service (any instance)
    participant R as Redis
    participant DB as PostgreSQL

    A1->>AL: PATCH /alerts/42 {status: CONFIRMED_FRAUD, version: 3}
    A2->>AL: PATCH /alerts/42 {status: FALSE_POSITIVE, version: 3}

    Note over AL,R: Layer 1 — distributed lock (fast-fail, cross-instance)
    AL->>R: SET lock:alert:42 {token} NX PX 5000
    R-->>AL: OK (A1 wins)
    AL->>R: SET lock:alert:42 {token} NX PX 5000
    R-->>AL: nil (A2)
    AL-->>A2: 409 Conflict — alert is being updated

    Note over AL,DB: Layer 2 — optimistic locking (correctness guarantee)
    AL->>DB: UPDATE alert SET status=?, version=4 WHERE id=42 AND version=3
    DB-->>AL: 1 row updated
    AL->>R: EVAL unlock.lua (delete only if token matches)
    AL-->>A1: 200 OK {version: 4}
```

The Redis lock is an *optimisation*: it rejects contention cheaply and avoids wasted work. The `version` column is the *guarantee*: if the lock expires (GC pause, network partition), the `WHERE version = ?` predicate still rejects the stale write. Details are in [concept 05](../concepts/05-two-layer-concurrent-write-protection.md).

## 6. Hexagonal service layout

```mermaid
flowchart LR
    subgraph api [api — inbound adapters]
        C[REST Controller]
        EH[ProblemDetail<br/>ExceptionHandler]
        KL[Kafka Listener]
    end
    subgraph application [application — use cases]
        UC[Use case service]
        P1[[Port: EventPublisher]]
        P2[[Port: Repository]]
    end
    subgraph domain [domain — pure Java]
        M[Entities / Value objects<br/>Rules]
    end
    subgraph infrastructure [infrastructure — outbound adapters]
        KP[Kafka publisher]
        JR[JDBC / JPA repository]
        RC[Redis adapter]
    end

    C --> UC
    KL --> UC
    UC --> M
    UC --> P1 & P2
    KP -. implements .-> P1
    JR -. implements .-> P2
```

Dependencies point **inward**. The rules are enforced by ArchUnit tests in every service (`*ArchTest`).

## 7. Data model

```mermaid
erDiagram
    TRANSACTION ||--|| RISK_SCORE : "scored as"
    TRANSACTION ||--o{ RULE_HIT : "triggered"
    TRANSACTION ||--o| ALERT : "may raise"
    ALERT ||--o{ ALERT_EVENT : "audit trail"

    TRANSACTION {
        uuid id PK
        varchar transaction_id UK "client id, idempotency"
        varchar account_id "IDX (account_id, occurred_at DESC)"
        numeric amount "numeric(19,4)"
        char currency
        varchar merchant_id
        varchar mcc
        char country
        varchar channel
        timestamptz occurred_at
        timestamptz received_at
    }
    RISK_SCORE {
        uuid transaction_id PK,FK
        smallint rule_score "0-100"
        numeric ml_probability "0-1"
        smallint final_score "0-100"
        varchar decision "APPROVE | REVIEW | DECLINE"
        varchar model_version
    }
    RULE_HIT {
        bigint id PK
        uuid transaction_id FK "IDX"
        varchar rule_code
        smallint weight
        varchar reason
    }
    ALERT {
        uuid id PK
        uuid transaction_id UK,FK
        varchar account_id
        varchar status "IDX (status, severity, created_at DESC)"
        varchar severity
        varchar assignee
        bigint version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }
    ALERT_EVENT {
        bigint id PK
        uuid alert_id FK
        varchar from_status
        varchar to_status
        varchar actor
        timestamptz at
    }
```

**Indexing strategy** (detail in [concept 03](../concepts/03-n-plus-one-and-composite-indexing.md)):

| Query | Index | Why this column order |
|-------|-------|-----------------------|
| Recent transactions for account (velocity, history) | `(account_id, occurred_at DESC)` | Equality column first, then range/sort column. One index range scan, no sort step. |
| Analyst queue: open alerts by severity, newest first | `(status, severity, created_at DESC)` | Matches `WHERE status=? AND severity=? ORDER BY created_at DESC LIMIT ?` exactly |
| Partial index for the hot path | `(created_at DESC) WHERE status = 'OPEN'` | Small index, because closed alerts are ~95% of rows |

## 8. Redis key space

| Key pattern | Type | TTL | Owner | Purpose |
|-------------|------|-----|-------|---------|
| `rl:{clientId}` | Hash (`tokens`, `ts`) | auto | ingestion | Token-bucket rate limiter (Lua, atomic) |
| `idem:{idempotencyKey}` | String | 24h | ingestion | Idempotent POST |
| `velocity:{accountId}` | Sorted set (score = epoch ms) | 24h | scoring | Sliding-window transaction counts |
| `risk:account:{accountId}` | Hash | 1h ± jitter | scoring → alert | High-risk account cache |
| `lock:alert:{alertId}` | String (token) | 5s | alert | Distributed lock, layer 1 |
| `processed:{eventId}` | String | 7d | scoring | Consumer-side dedupe |

## 9. Deployment

```mermaid
flowchart TB
    subgraph host [docker compose / Kubernetes namespace]
        nginx[nginx :80]
        subgraph ingestion [Deployment: ingestion ×N]
            i1[pod]:::svc
            i2[pod]:::svc
        end
        subgraph scoring [Deployment: scoring ×P ≤ partitions]
            s1[pod]:::svc
        end
        subgraph alerts [Deployment: alert ×N]
            a1[pod]:::svc
        end
        kafka[(kafka)]
        redis[(redis)]
        pg[(postgres)]
        prom[prometheus]
        graf[grafana]
    end
    nginx --> i1 & i2
    nginx --> a1
    classDef svc fill:#e8f1ff,stroke:#3b6fd8
```

- Local: `docker compose -f infra/docker-compose.yml up -d` (infrastructure) or `infra/smoke-test.sh` (the full replicated stack behind NGINX on :8080).
- Health: `/actuator/health/liveness` and `/actuator/health/readiness` back the LB and Kubernetes probes.
- Graceful shutdown: `server.shutdown=graceful`, so in-flight requests finish and Kafka offsets are committed before exit.

## 10. Quality attributes

| Attribute | Target | Mechanism |
|-----------|--------|-----------|
| Ingestion latency | p99 < 50 ms at 2k TPS per instance | Virtual threads, async-ack Kafka send, Redis Lua (1 RTT) |
| End-to-end detection latency | p99 < 1 s ingest→alert | Kafka, in-process ML, batch DB writes |
| Durability | No accepted transaction lost | `acks=all`, `min.insync.replicas=2`, commit offsets after persistence |
| Correctness under concurrency | No lost updates on alerts | Redis lock + optimistic locking |
| Availability | Ingestion stays up if scoring is down | Kafka buffers. Services are decoupled. |
| Abuse protection | Per-client quotas | Distributed token bucket |
| Scalability | Linear on ingestion; up to partition count on scoring | Stateless services, partitioned topics |
