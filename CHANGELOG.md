# Changelog

## 0.1.0 (2026-10-10)


### Features

* **alerts:** alert management API with N+1-free queries and two-layer concurrency control (feature 007) ([#6](https://github.com/sukeshatla/fraud-detection/issues/6)) ([74ce9ba](https://github.com/sukeshatla/fraud-detection/commit/74ce9bac18f6ec6a28adfbfab2d4a23e9101b17c))
* CI/CD pipeline: staged CI, Error Prone and Checkstyle, CodeQL/Trivy/gitleaks, kind deploy with smoke test and rollback, GHCR images, release-please (feature 014) ([c3bca35](https://github.com/sukeshatla/fraud-detection/commit/c3bca35e50168daed15aed27fdbab4bed091639d))
* container images, NGINX load balancing, replicated compose stack, smoke test (feature 011) ([#10](https://github.com/sukeshatla/fraud-detection/issues/10)) ([1eb181d](https://github.com/sukeshatla/fraud-detection/commit/1eb181dd69b8f7eade820b48fedc52fcc7c754b8))
* **dashboard:** real-time analyst dashboard with SSE, keyset pagination and optimistic review actions (feature 008) ([a04db62](https://github.com/sukeshatla/fraud-detection/commit/a04db6261b149e3edf887facd8553b037fe20972))
* Gatling load tests, failover before/after, nightly load smoke (feature 013) ([#12](https://github.com/sukeshatla/fraud-detection/issues/12)) ([2934da8](https://github.com/sukeshatla/fraud-detection/commit/2934da869f1bd8cfc4b86ec4607aa3b778d102d4))
* **ingestion:** distributed rate limiting (Redis token bucket) and idempotency keys (feature 002) ([20303e7](https://github.com/sukeshatla/fraud-detection/commit/20303e781fa941fa3a0916a503a5f12f4ec0b5fa))
* metrics, distributed tracing across batch + outbox, correlation ids, Prometheus/Grafana/Jaeger (feature 012) ([#11](https://github.com/sukeshatla/fraud-detection/issues/11)) ([6a48c5a](https://github.com/sukeshatla/fraud-detection/commit/6a48c5af205e11151a5426b62e02491acfa0940b))
* OAuth2/JWT security with Keycloak, role-based access, PII masking and OIDC dashboard login (feature 015) ([0c07d8f](https://github.com/sukeshatla/fraud-detection/commit/0c07d8f230ba405251f9bdbb6d352d6b2b4ee028))
* parallel per-account scoring and concurrency lab (feature 009) ([#8](https://github.com/sukeshatla/fraud-detection/issues/8)) ([d41b1d8](https://github.com/sukeshatla/fraud-detection/commit/d41b1d8c362a783ef445c491afb8b95871476bdb))
* project foundation and transaction ingestion API (features 000, 001) ([3ce6d77](https://github.com/sukeshatla/fraud-detection/commit/3ce6d7759eb193f592c2fff0ce7654be66a01c08))
* **scoring:** high-risk account cache with stampede protection (feature 005) ([#4](https://github.com/sukeshatla/fraud-detection/issues/4)) ([2fa817a](https://github.com/sukeshatla/fraud-detection/commit/2fa817a7691f259b68d1d21fc19d357b6cc985f9))
* **scoring:** JDBC batch persistence, deterministic UUIDv7 keys, composite index (feature 004) ([#3](https://github.com/sukeshatla/fraud-detection/issues/3)) ([b2de7d5](https://github.com/sukeshatla/fraud-detection/commit/b2de7d5173ed0ba81df24fdecc6460b49b8d656c))
* **scoring:** ML-assisted scoring with score blending, circuit breaker and semaphore bulkhead (feature 006) ([89a2bf9](https://github.com/sukeshatla/fraud-detection/commit/89a2bf9fc1cc22c12d8faebeed419a9ae413a926))
* **scoring:** rule-based scoring engine with Redis sliding windows and DLT (feature 003) ([#2](https://github.com/sukeshatla/fraud-detection/issues/2)) ([cb57ebd](https://github.com/sukeshatla/fraud-detection/commit/cb57ebd4f97ec27742fa2b9bf5ab9185d3d6c9ca))
* transactional outbox, jittered retries, DLT replay, circuit breaker, explicit timeouts (feature 010) ([#9](https://github.com/sukeshatla/fraud-detection/issues/9)) ([98f973c](https://github.com/sukeshatla/fraud-detection/commit/98f973c7461db18251d56eac5b83355f2eb7ac73))


### Bug Fixes

* **build:** run integration tests against compiled classes, not repackaged jar ([c9e5cac](https://github.com/sukeshatla/fraud-detection/commit/c9e5cacfeaa5f17f0f961311339038a96e686151))


### Miscellaneous Chores

* release 0.1.0 ([92c9745](https://github.com/sukeshatla/fraud-detection/commit/92c9745e975ad6a14c082469aaba8d4d3ea53e73))
