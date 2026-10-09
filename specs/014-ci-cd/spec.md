# Feature 014 — CI/CD pipeline

| Field    | Value |
|----------|-------|
| Status   | Spec (basic CI delivered in 000) |
| Depends  | 000 |
| Concepts | [CI/CD pipeline](../../docs/concepts/10-ci-cd-pipeline.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-014-01 | PR pipeline stages: build → unit/slice/arch tests → integration tests (Testcontainers) → coverage gate → dashboard lint/test/build. Each stage is a separate job with cached `~/.m2` and npm. |
| AC-014-02 | Path filters: dashboard-only changes skip the Java ITs. |
| AC-014-03 | Static analysis: SpotBugs or Error Prone, plus Checkstyle (Google style). |
| AC-014-04 | Supply chain: Dependabot, OWASP dependency-check or `trivy fs`, CodeQL. The build fails on HIGH/CRITICAL findings. |
| AC-014-05 | On `main`: OCI images built with Spring Boot buildpacks (`spring-boot:build-image`) or Jib, tagged `sha` + semver, pushed to GHCR, and scanned with Trivy. An SBOM (CycloneDX) is attached. |
| AC-014-06 | Release: Conventional Commits → automated changelog and semver tag (release-please). |
| AC-014-07 | CD: deploy to a kind/k3d cluster in CI using Helm or Kustomize manifests, then run the smoke Gatling simulation as a post-deploy check. A failure triggers rollback. |
| AC-014-08 | Branch protection documented: required checks, linear history, CODEOWNERS. |
