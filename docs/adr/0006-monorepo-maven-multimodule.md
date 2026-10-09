# ADR-0006: Monorepo with Maven multi-module

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
Services share event contracts and build conventions. Reviewers want to clone one repo and run everything.

## Decision
- One Git repo. A Maven parent POM manages versions (Spring Boot BOM) and plugins (Surefire, Failsafe, JaCoCo).
- Modules: `common`, `ingestion-service`, `scoring-service`, `alert-service`, `load-tests`. The `dashboard/` frontend uses npm alongside them.
- The Maven Wrapper (`./mvnw`) pins the Maven version. There is no global install.

## Consequences
- ✅ Atomic cross-service changes, single CI pipeline with path filters.
- ❌ Services share a release cadence by default. Independent versioning is possible later.

## Alternatives considered
- **Gradle:** equally valid. Maven was chosen for its ubiquity in enterprise Java.
- **Polyrepo:** contract changes need coordinated releases across repos.
