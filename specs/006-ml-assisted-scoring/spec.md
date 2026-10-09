# Feature 006 — ML-assisted scoring

| Field    | Value |
|----------|-------|
| Status   | Implemented |
| Depends  | 003 |
| Concepts | [Concurrency primitives — Semaphore bulkhead](../../docs/concepts/08-concurrency-primitives.md), [Resilience](../../docs/concepts/13-resilience-patterns.md) |

## 1. Problem / motivation
Rules catch known patterns. A model catches combinations no one wrote a rule for. Blending the two gives explainability plus recall.

## 2. Acceptance criteria
| ID        | Given / When / Then |
|-----------|---------------------|
| AC-006-01 | A `FeatureExtractor` turns a transaction + account activity into a numeric vector: log(1+amount USD), hour-of-day (cyclical sin/cos), high-risk-MCC flag, card-not-present flag, log(1+txn count 1h / 24h), country-changed flag, log(1+small txns 5 min). (A new-merchant flag needs merchant history per account, which is documented as a future feature.) |
| AC-006-02 | A `LogisticRegressionModel` loads weights from a versioned JSON file (`model-v1.json`) and returns a probability in [0, 1]. A reproducible training script `ml/train.py` (numpy, fixed seed) produces the file from a synthetic dataset and reports AUC / precision / recall. |
| AC-006-03 | Final score = `max(ruleScore, round(0.6 × ruleScore + 0.4 × mlProbability × 100))`. **ML can escalate, never dilute** a rule hit, so an explainable rule decision is never overridden by an opaque model. `mlProbability` and `modelVersion` are persisted and added to `FraudAlertEvent` as optional fields (additive v1 evolution). |
| AC-006-04 | Model inference is behind the `MlScorer` port, so it can be swapped for a remote model server (ONNX / Python) without touching the domain. |
| AC-006-05 | Concurrent inferences are bounded by a `Semaphore` (bulkhead, default 32 permits). If no permit is free within 20 ms, scoring **falls back to rules-only** and increments `ml_fallback_total`. |
| AC-006-06 | Unit tests verify the model reproduces the reference probabilities from the training script to 1e-6. |

## 3. Out of scope
Online learning, feature store, model monitoring and drift detection (documented only).
