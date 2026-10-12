#!/usr/bin/env python3
"""Entrena el filtro de calidad de notas (regresión logística) a partir de los CSV que escribe la evaluación
(EVAL_FEATS). Valida con «dejar una canción fuera» y comprueba la transferencia entre variantes de audio.
Uso: train_note_filter.py /tmp/feats [--write ruta/NoteQualityWeights.kt]
"""
import csv, sys, numpy as np

def load(path):
    rows = list(csv.reader(open(path)))
    head, data = rows[0], rows[1:]
    songs = np.array([r[0] for r in data]); y = np.array([float(r[1]) for r in data])
    X = np.array([[float(v) for v in r[2:]] for r in data])
    return songs, y, X

def fit(X, y, l2=1.0, iters=60):
    mu, sd = X.mean(0), X.std(0) + 1e-6
    Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
    w = np.zeros(Z.shape[1])
    for _ in range(iters):                       # Newton–Raphson (IRLS)
        p = 1 / (1 + np.exp(-Z @ w))
        g = Z.T @ (p - y) + l2 * np.r_[w[:-1], 0]
        H = (Z.T * (p * (1 - p))) @ Z + l2 * np.diag(np.r_[np.ones(Z.shape[1] - 1), 0]) + 1e-6 * np.eye(Z.shape[1])
        w -= np.linalg.solve(H, g)
    return w, mu, sd

def predict(X, w, mu, sd):
    Z = np.hstack([(X - mu) / sd, np.ones((len(X), 1))])
    return 1 / (1 + np.exp(-Z @ w))

def f1_at(p, y, T, thr):
    keep = p >= thr
    tp = y[keep].sum(); n = keep.sum()
    return (2 * tp / (n + T)) if (n + T) else 0, (tp / n if n else 0), tp / T

base = sys.argv[1]
T = 3209  # notas reales en las 17 canciones
data = {t: load(f"{base}_{t}.csv") for t in ("seco", "reverb", "mp3")}
for t, (s, y, X) in data.items():
    f, p, r = f1_at(np.ones(len(y)), y, T, 0.5)
    print(f"{t:7s} sin filtro: n={len(y)}  P={p:.3f} R={r:.3f} F1={f:.3f}")

# validación cruzada (dejar una canción fuera) entrenando con las tres variantes salvo la canción de prueba
songs_all = sorted(set(data["seco"][0]))
for name in ("seco", "reverb", "mp3"):
    pred = []; lab = []
    s, y, X = data[name]
    for held in songs_all:
        Xtr = np.vstack([data[t][2][data[t][0] != held] for t in data]); ytr = np.concatenate([data[t][1][data[t][0] != held] for t in data])
        w, mu, sd = fit(Xtr, ytr)
        m = s == held
        pred.append(predict(X[m], w, mu, sd)); lab.append(y[m])
    pred = np.concatenate(pred); lab = np.concatenate(lab)
    best = max(((f1_at(pred, lab, T, th), th) for th in np.arange(0.2, 0.9, 0.05)), key=lambda x: x[0][0])
    print(f"CV {name:7s}: mejor umbral {best[1]:.2f} → F1={best[0][0]:.3f} (P={best[0][1]:.3f} R={best[0][2]:.3f})")
    for th in (0.3, 0.4, 0.5, 0.6):
        f, p, r = f1_at(pred, lab, T, th); print(f"          umbral {th:.1f}: F1={f:.3f} P={p:.3f} R={r:.3f}")

# transferencia: entrenado SOLO con audio seco, probado en reverb y mp3
s0, y0, X0 = data["seco"]
w, mu, sd = fit(X0, y0)
for name in ("reverb", "mp3"):
    s, y, X = data[name]; p = predict(X, w, mu, sd)
    f = max((f1_at(p, y, T, th)[0], th) for th in np.arange(0.2, 0.9, 0.05))
    print(f"transferencia seco→{name}: mejor F1={f[0]:.3f} con umbral {f[1]:.2f}")

# modelo final con todo
Xa = np.vstack([data[t][2] for t in data]); ya = np.concatenate([data[t][1] for t in data])
w, mu, sd = fit(Xa, ya)
names = ["amp", "onset", "logDur", "amp/p90", "hayOctava", "ampRatio", "onsetRatio", "hay12", "hayArriba12", "onsetVecinas", "polifonia", "nacidaDeAtaque"]
print("pesos:", {n: round(float(v), 2) for n, v in zip(names, w[:-1])}, "sesgo", round(float(w[-1]), 2))
if "--write" in sys.argv:
    out = sys.argv[sys.argv.index("--write") + 1]
    fl = lambda a: ", ".join(f"{float(v):.5f}f" for v in a)
    open(out, "w").write(f"""package com.forxa.pianocascada.transcribe

// Generado por tools/train_note_filter.py — no editar a mano.
object NoteQualityWeights {{
    fun install() {{
        NoteQuality.weights = floatArrayOf({fl(w[:-1])})
        NoteQuality.bias = {float(w[-1]):.5f}f
        NoteQuality.mean = floatArrayOf({fl(mu)})
        NoteQuality.scale = floatArrayOf({fl(sd)})
        NoteQuality.trained = true
    }}
}}
""")
    print("escrito", out)
