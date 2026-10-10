# Feature 014 — CI/CD pipeline

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 000 |
| Concepts | [CI/CD pipeline](../../docs/concepts/10-ci-cd-pipeline.md) |

## 1. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-014-01 | PR pipeline stages: build → unit/slice/arch tests → integration tests (Testcontainers) → coverage gate → dashboard lint/test/build. Each stage is a separate job with cached `~/.m2` and npm. |
| AC-014-02 | Path filters: dashboard-only changes skip the Java ITs. |
| AC-014-03 | Static analysis: **Error Prone** (compile-time bug patterns) plus **Checkstyle** with a curated rule set that targets defects, not formatting taste. |
| AC-014-04 | Supply chain: Dependabot, OWASP dependency-check or `trivy fs`, CodeQL. The build fails on HIGH/CRITICAL findings. |
| AC-014-05 | Images built once from the layered-jar Dockerfile, scanned with Trivy (fails on fixable HIGH/CRITICAL), a CycloneDX SBOM attached, and on `main` pushed to GHCR tagged with the commit SHA (semver tags come from releases, AC-014-06). |
| AC-014-06 | Release: Conventional Commits → automated changelog and semver tag (release-please). |
| AC-014-07 | CD: deploy to a kind cluster in CI with Kustomize manifests (on PRs touching deployable code, and on `main`), run a post-deploy smoke test through the gateway, and roll back (`kubectl rollout undo`) if it fails. The nightly workflow runs the Gatling smoke simulation against the full stack. |
| AC-014-08 | Branch protection documented: required checks, linear history, CODEOWNERS. |
