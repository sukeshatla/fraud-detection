#!/usr/bin/env python3
"""
Train the fraud model served by scoring-service.

    python3 ml/train.py            # writes scoring-service/src/main/resources/ml/model-v1.json

Pipeline: synthetic labelled data → feature extraction (MUST match the Java FeatureExtractor) →
standardisation → logistic regression fitted with Newton's method (IRLS, L2) → hold-out evaluation →
JSON artifact with weights, scaling, metrics and reference cases for the Java parity test.

Deterministic: fixed seed, so the committed model is reproducible byte-for-byte.
"""
import json
import math
from pathlib import Path

import numpy as np

SEED = 42
N_SAMPLES = 60_000
FRAUD_RATE = 0.05
L2 = 1e-3
MODEL_VERSION = "lr-v1"
OUT = Path(__file__).resolve().parent.parent / "scoring-service/src/main/resources/ml/model-v1.json"

FEATURES = [
    "log_amount_usd", "hour_sin", "hour_cos", "high_risk_mcc", "card_not_present",
    "log_tx_count_1h", "log_tx_count_24h", "country_changed", "log_small_tx_5m",
]


def extract(amount_usd, hour, high_risk_mcc, cnp, c1h, c24h, country_changed, small5m):
    """Feature definitions: the single source of truth mirrored by FeatureExtractor.java."""
    angle = 2 * math.pi * hour / 24
    return [
        math.log1p(amount_usd), math.sin(angle), math.cos(angle), float(high_risk_mcc), float(cnp),
        math.log1p(c1h), math.log1p(c24h), float(country_changed), math.log1p(small5m),
    ]


def synthesize(rng, n):
    """Raw inputs per transaction, with fraud following the patterns the rules target (and some they don't)."""
    y = (rng.random(n) < FRAUD_RATE).astype(float)
    fraud = y == 1
    probe = fraud & (rng.random(n) < 0.35)

    amount = rng.lognormal(math.log(60), 1.0, n)
    amount[fraud] = rng.lognormal(math.log(700), 1.1, fraud.sum())
    amount[probe] = rng.uniform(0.5, 2.0, probe.sum())

    hour = np.mod(rng.normal(14, 4, n), 24)
    night = fraud & (rng.random(n) < 0.55)
    hour[night] = np.mod(rng.normal(3, 2.5, night.sum()), 24)

    mcc = rng.random(n) < np.where(fraud, 0.30, 0.02)
    cnp = rng.random(n) < np.where(fraud, 0.80, 0.35)
    c1h = rng.poisson(np.where(fraud, 3.5, 0.4)) + 1
    c24h = c1h + rng.poisson(np.where(fraud, 6.0, 3.0))
    country = rng.random(n) < np.where(fraud, 0.30, 0.02)
    small = rng.poisson(np.where(probe, 2.5, np.where(fraud, 0.3, 0.05)))

    # Label noise: real fraud labels are imperfect (chargebacks missed, friendly fraud)
    flip = rng.random(n) < 0.01
    y[flip] = 1 - y[flip]

    X = np.array([extract(*row) for row in zip(amount, hour, mcc, cnp, c1h, c24h, country, small)])
    return X, y


def fit_irls(X, y, l2=L2, iters=25):
    """Logistic regression via Newton-Raphson (IRLS). X already standardised; bias handled separately."""
    Xb = np.hstack([X, np.ones((X.shape[0], 1))])
    w = np.zeros(Xb.shape[1])
    reg = l2 * np.eye(Xb.shape[1])
    reg[-1, -1] = 0  # don't regularise the intercept
    for _ in range(iters):
        p = 1 / (1 + np.exp(-Xb @ w))
        grad = Xb.T @ (p - y) + reg @ w
        hess = (Xb * (p * (1 - p))[:, None]).T @ Xb + reg
        step = np.linalg.solve(hess, grad)
        w -= step
        if np.abs(step).max() < 1e-10:
            break
    return w[:-1], w[-1]


def auc(scores, y):
    """Rank-based AUC (Mann-Whitney U)."""
    order = np.argsort(scores)
    ranks = np.empty(len(scores))
    ranks[order] = np.arange(1, len(scores) + 1)
    pos = y == 1
    n_pos, n_neg = pos.sum(), (~pos).sum()
    return (ranks[pos].sum() - n_pos * (n_pos + 1) / 2) / (n_pos * n_neg)


def main():
    rng = np.random.default_rng(SEED)
    X, y = synthesize(rng, N_SAMPLES)
    split = int(0.8 * len(y))
    Xtr, ytr, Xte, yte = X[:split], y[:split], X[split:], y[split:]

    mean = Xtr.mean(axis=0)
    std = Xtr.std(axis=0)
    std[std == 0] = 1.0
    w, b = fit_irls((Xtr - mean) / std, ytr)

    def predict(raw):
        z = ((np.asarray(raw) - mean) / std) @ w + b
        return 1 / (1 + np.exp(-z))

    p_te = predict(Xte)
    pred = p_te >= 0.5
    tp = float((pred & (yte == 1)).sum())
    metrics = {
        "auc": round(float(auc(p_te, yte)), 4),
        "precision_at_0_5": round(tp / max(1.0, float(pred.sum())), 4),
        "recall_at_0_5": round(tp / max(1.0, float((yte == 1).sum())), 4),
        "train_size": int(split),
        "test_size": int(len(yte)),
        "fraud_rate": round(float(y.mean()), 4),
    }

    # Reference inputs → expected features and probability: the Java parity test replays these.
    reference_inputs = [
        dict(amountUsd=42.00, hourUtc=14.0, highRiskMcc=False, cardNotPresent=False, txCount1h=1, txCount24h=3, countryChanged=False, smallTx5m=0),
        dict(amountUsd=1.00, hourUtc=3.0, highRiskMcc=False, cardNotPresent=True, txCount1h=5, txCount24h=7, countryChanged=False, smallTx5m=3),
        dict(amountUsd=7500.00, hourUtc=2.5, highRiskMcc=True, cardNotPresent=True, txCount1h=2, txCount24h=4, countryChanged=True, smallTx5m=0),
        dict(amountUsd=250.00, hourUtc=20.0, highRiskMcc=False, cardNotPresent=True, txCount1h=1, txCount24h=1, countryChanged=False, smallTx5m=0),
        dict(amountUsd=9000.00, hourUtc=11.0, highRiskMcc=True, cardNotPresent=False, txCount1h=8, txCount24h=20, countryChanged=True, smallTx5m=4),
        dict(amountUsd=0.75, hourUtc=23.75, highRiskMcc=False, cardNotPresent=True, txCount1h=3, txCount24h=3, countryChanged=False, smallTx5m=2),
    ]
    references = []
    for r in reference_inputs:
        feats = extract(r["amountUsd"], r["hourUtc"], r["highRiskMcc"], r["cardNotPresent"],
                        r["txCount1h"], r["txCount24h"], r["countryChanged"], r["smallTx5m"])
        references.append({**r, "expectedFeatures": feats, "expectedProbability": float(predict(feats))})

    model = {
        "modelVersion": MODEL_VERSION,
        "algorithm": "logistic-regression (IRLS, L2=%g)" % L2,
        "features": FEATURES,
        "means": mean.tolist(),
        "stds": std.tolist(),
        "weights": w.tolist(),
        "intercept": float(b),
        "metrics": metrics,
        "referenceCases": references,
    }
    params = np.concatenate([w, mean, std, [b]])
    if not np.all(np.isfinite(params)):
        raise SystemExit("training produced non-finite parameters")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(model, indent=2) + "\n")

    print(f"wrote {OUT}")
    print("metrics:", json.dumps(metrics))
    print("coefficients (standardised):")
    for name, coef in sorted(zip(FEATURES, w), key=lambda t: -abs(t[1])):
        print(f"  {name:18s} {coef:+.3f}")
    for r in references:
        print(f"  ref amount={r['amountUsd']:>8} → p={r['expectedProbability']:.4f}")


if __name__ == "__main__":
    # numpy 2.x on Apple's Accelerate BLAS raises spurious FP warnings inside matmul even for finite
    # results (verified against a pure-Python recomputation). Silence them here, and guard with an
    # explicit finiteness check so a real numerical problem still fails loudly.
    with np.errstate(over="ignore", divide="ignore", invalid="ignore"):
        main()
