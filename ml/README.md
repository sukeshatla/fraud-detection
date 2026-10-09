# ML: fraud model training

```bash
pip install -r ml/requirements.txt
python3 ml/train.py
```

This writes [`scoring-service/src/main/resources/ml/model-v1.json`](../scoring-service/src/main/resources/ml/model-v1.json), which the scoring service loads at startup.

| Step | What |
|------|------|
| Data | 60k synthetic transactions, ~5% fraud. Fraud follows the patterns the rules target (probes, bursts, night-time, foreign, high-risk MCC), with overlap and 1% label noise so it isn't trivially separable. |
| Features | 9 features, defined once in `extract()` and **mirrored exactly** by `FeatureExtractor.java` |
| Model | Logistic regression fitted with Newton's method (IRLS), with L2 regularisation and standardised inputs |
| Evaluation | 80/20 split: AUC, plus precision/recall at p ≥ 0.5 (stored in the model file) |
| Parity | `referenceCases` (inputs → expected features → expected probability) are replayed by `ModelParityTest` in Java to 1e-6. This catches training/serving skew. |

**Current model `lr-v1`:** AUC 0.913, precision 0.964, recall 0.716 (hold-out).

### Why logistic regression?
It's a strong, interpretable baseline. Each coefficient says how much a feature moves the log-odds, inference costs nanoseconds, and the model is portable as plain JSON. Gradient-boosted trees would likely score higher. They would be served through ONNX behind the same `MlScorer` port, and the parity-test approach stays the same.

### Retraining workflow
1. Change `train.py` (data, features, hyper-parameters), then bump `MODEL_VERSION`.
2. If you add or change a feature, update `FeatureExtractor.java` in the **same PR**. The parity test fails otherwise.
3. `./mvnw -pl scoring-service test` must pass (`ModelParityTest`).
