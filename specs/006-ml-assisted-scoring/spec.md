# Feature 006 — ML-assisted scoring

| Field    | Value |
|----------|-------|
| Status   | Spec |
| Depends  | 003 |
| Concepts | [Concurrency primitives — Semaphore bulkhead](../../docs/concepts/08-concurrency-primitives.md), [Resilience](../../docs/concepts/13-resilience-patterns.md) |

## 1. Problem / motivation
Rules catch known patterns. A model catches combinations no one wrote a rule for. Blending the two gives explainability plus recall.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-006-01 | A `FeatureExtractor` turns a transaction + context into a numeric vector: log(amount), hour-of-day (cyclical sin/cos), MCC risk, txn count in the last 1h/24h, distance-from-home flag, new-merchant flag. |
| AC-006-02 | A `LogisticRegressionModel` loads weights from a versioned JSON file (`model-v1.json`) and returns a probability in [0, 1]. A training notebook/script under `ml/` produces the file from a synthetic dataset. |
| AC-006-03 | Final score = `round(0.6 × ruleScore + 0.4 × mlProbability × 100)`. Both components and `modelVersion` are persisted. |
| AC-006-04 | Model inference is behind the `MlScorer` port, so it can be swapped for a remote model server (ONNX / Python) without touching the domain. |
| AC-006-05 | Concurrent inferences are bounded by a `Semaphore` (bulkhead, default 32 permits). If no permit is free within 20 ms, scoring **falls back to rules-only** and increments `ml_fallback_total`. |
| AC-006-06 | Unit tests verify the model reproduces the reference probabilities from the training script to 1e-6. |

## 3. Out of scope
Online learning, feature store, model monitoring and drift detection (documented only).
