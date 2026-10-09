# Plan — Feature 000 Project foundation

## 1. Approach
Maven multi-module monorepo. The parent POM imports `spring-boot-dependencies` as a BOM rather than using it as the parent, so the project controls its own plugin configuration. Surefire runs `*Test` and Failsafe runs `*IT`. JaCoCo enforces a coverage floor scoped to `application`/`domain`. ArchUnit tests live in each service.

## 2. Components
```
fraud-detection/
├── pom.xml                      parent: BOM, plugin management, JaCoCo gate
├── mvnw, .mvn/wrapper           pinned Maven 3.9.x
├── common/                      shared event contracts (records only)
├── ingestion-service/           Feature 001
├── infra/docker-compose.yml     kafka (KRaft) · postgres · redis
├── .github/workflows/ci.yml     build + test + reports
├── docs/                        constitution, architecture, ADRs, concepts
└── specs/                       this folder
```

## 3. Key decisions
| Decision | Alternatives | Why |
|----------|--------------|-----|
| Import BOM rather than inherit `spring-boot-starter-parent` | Inherit parent | `common` is not a Boot app. Explicit plugin config is easier to reason about. |
| ArchUnit per service | Shared rule library | Each service owns its rules. Duplication is three tiny files. |
| Kafka in KRaft mode | ZooKeeper | ZooKeeper was removed in Kafka 4.0 |

## 4. Test strategy
- AC-000-02/03 → `HexagonalArchTest` in each service.
- AC-000-01/05/06 → proven by CI running `./mvnw verify`.
- AC-000-04 → compose healthchecks.
