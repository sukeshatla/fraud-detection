# Engineering Constitution

These are the rules every change in this repository follows. A pull request that breaks one of them needs an ADR explaining why.

## 1. Spec-driven development (SDD)

No production code exists without a spec that asks for it.

```
spec.md  ──►  plan.md  ──►  tasks.md  ──►  failing tests  ──►  code  ──►  refactor  ──►  docs
 (what/why)    (how)        (ordered       (red)              (green)                   (concepts,
                             steps)                                                      diagrams)
```

| Artifact   | Answers                          | Owner of truth for                             |
|------------|----------------------------------|------------------------------------------------|
| `spec.md`  | *What* and *why*, from the user's side | User stories, acceptance criteria (Given/When/Then), non-functional requirements, out-of-scope |
| `plan.md`  | *How*, technically               | Components, contracts, data model, diagrams, trade-offs |
| `tasks.md` | *In what order*                  | Small, test-first steps, each traceable to an acceptance criterion |

Each acceptance criterion gets an ID (`AC-001-03` = feature 001, criterion 3). Tests reference that ID in their `@DisplayName`, so every criterion traces to the tests that prove it.

## 2. Test-driven development (TDD)

1. **Red:** write the smallest failing test for the next acceptance criterion.
2. **Green:** write the simplest code that makes it pass.
3. **Refactor:** clean up while the tests stay green.

### Test pyramid

| Layer        | Tooling                               | Runs in           | Naming           |
|--------------|---------------------------------------|-------------------|------------------|
| Unit         | JUnit 5, AssertJ, Mockito             | `mvn test`        | `*Test.java`     |
| Slice        | `@WebMvcTest`, `@DataJpaTest`         | `mvn test`        | `*Test.java`     |
| Architecture | ArchUnit                              | `mvn test`        | `*ArchTest.java` |
| Integration  | Testcontainers (Kafka, Postgres, Redis) | `mvn verify`    | `*IT.java`       |
| Contract     | Shared event contracts + JSON round-trip tests | `mvn test` | `*ContractTest.java` |
| Load         | Gatling                               | on demand / nightly | `*Simulation`  |
| UI           | Vitest + React Testing Library        | `npm test`        | `*.test.tsx`     |

Integration tests use real infrastructure through Testcontainers, never in-memory fakes such as H2 or embedded Kafka. The behaviour we care about (isolation levels, partitioning, Lua atomicity) only exists in the real thing.

## 3. Architecture rules (enforced by ArchUnit)

Every service uses a **hexagonal (ports and adapters)** layout:

```
api/             ← inbound adapters (REST controllers, DTOs, exception handlers)
application/     ← use cases + ports (interfaces), no framework I/O
domain/          ← pure business model, no Spring, no Jackson, no JPA
infrastructure/  ← outbound adapters (Kafka, Redis, JDBC), config
```

- `domain` depends on nothing else.
- `application` depends only on `domain`.
- `api` and `infrastructure` depend on `application`/`domain`, never on each other.

## 4. Code standards

- Java 21: records for DTOs and events, sealed types for closed hierarchies, pattern matching, virtual threads.
- Immutability by default. Constructor injection only (no field `@Autowired`).
- Money is `BigDecimal` plus an ISO-4217 currency, never `double`.
- Time is `Instant` (UTC) on the wire and in the DB. `Clock` is injected so tests are deterministic.
- Errors are returned as RFC 9457 `ProblemDetail`.
- Logs are structured and never contain PAN or other PII.
- Configuration is externalised. Secrets come from the environment, never from the repo.

## 5. Definition of Done (per feature)

- [ ] `spec.md`, `plan.md` and `tasks.md` are merged, and the spec status is set to `Implemented`
- [ ] Every acceptance criterion has at least one test referencing its ID
- [ ] `./mvnw verify` is green (unit, slice, ArchUnit, integration)
- [ ] Line coverage is ≥ 80% on `application` and `domain` (JaCoCo gate)
- [ ] Architecture docs and diagrams are updated if a component or flow changed
- [ ] The concept docs in `docs/concepts/` are updated if the feature demonstrates a concept
- [ ] An ADR is written for any significant or irreversible decision
- [ ] CI is green

## 6. Git workflow

- Trunk-based: short-lived branches named `feat/NNN-short-name`, squash-merged.
- [Conventional Commits](https://www.conventionalcommits.org/): `feat(ingestion): ...`, `test(scoring): ...`, `docs(adr): ...`.
- Each commit leaves the build green.
