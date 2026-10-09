# Tasks — Feature 006 ML-assisted scoring

- [x] T1 — `ml/train.py`: synthetic data, IRLS logistic regression, metrics, `model-v1.json` with reference cases (AC-006-02)
- [x] T2 — Domain `FeatureExtractor` + `FeatureVector` (AC-006-01)
- [x] T3 — Domain `LogisticRegressionModel` + `ModelParityTest` (AC-006-02, 06)
- [x] T4 — `MlScorer` port, `MlPrediction`; blending in `RiskAssessment` (AC-006-03, 04)
- [x] T5 — `SemaphoreBulkheadMlScorer` + fallback metric (AC-006-05)
- [x] T6 — Persist ML columns; add optional ML fields to `FraudAlertEvent` (additive)
- [x] T7 — Wire it up; ITs; docs (concept 08 bulkhead, ML note)
