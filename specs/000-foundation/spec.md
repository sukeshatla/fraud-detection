# Feature 000 — Project foundation

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | — |
| Concepts | [Hexagonal architecture](../../docs/architecture/README.md#6-hexagonal-service-layout), [CI/CD](../../docs/concepts/10-ci-cd-pipeline.md) |

## 1. Problem / motivation
Every later feature needs a repeatable build, shared conventions, local infrastructure, and an automated quality gate. Without them, every feature would reinvent its own setup and the architecture would drift.

## 2. User stories
- **US-1** As a contributor, I want to clone the repo and run the whole build with one command, so that I don't need any global tools except a JDK and Docker.
- **US-2** As a reviewer, I want architecture rules enforced automatically, so that layering violations fail the build instead of relying on code review.
- **US-3** As a maintainer, I want every push and pull request built and tested in CI, so that `main` is always green.

## 3. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-000-01 | **Given** only a JDK 21 is installed, **when** I run `./mvnw verify`, **then** all modules compile and all tests run. |
| AC-000-02 | **Given** a service module, **when** a class in `domain` imports Spring, Jackson or anything from `api`/`infrastructure`, **then** the ArchUnit test fails. |
| AC-000-03 | **Given** a class in `api`, **when** it depends on `infrastructure`, **then** the ArchUnit test fails (and vice versa). |
| AC-000-04 | **Given** Docker, **when** I run `docker compose -f infra/docker-compose.yml up -d`, **then** Kafka, PostgreSQL and Redis become healthy. |
| AC-000-05 | **Given** a push or PR to `main`, **when** CI runs, **then** it executes `./mvnw verify` and publishes the test reports. |
| AC-000-06 | **Given** unit tests, **when** coverage of `application`+`domain` packages is below 80%, **then** `verify` fails. |

## 4. Non-functional requirements
| Category | Requirement |
|----------|-------------|
| Build time | A clean build without integration tests finishes in under 60 s on a laptop |
| Reproducibility | Maven version pinned via wrapper; dependency versions via the Spring Boot BOM |

## 5. Out of scope
Container image publishing and deployment (Feature 014).
