"""Métricas para comparar una partitura leída con la original.

 · estricta : nota por nota con el mismo instante (±0,06 pulsos). Un solo compás de más o de menos desplaza todo lo demás.
 · contenido: se alinean las dos piezas acorde a acorde (subsecuencia común más larga) y se cuentan las notas de los
              acordes que coinciden. Mide si el alumno vería las notas correctas, en el orden correcto.
 · ritmo    : entre acordes alineados consecutivos, fracción cuya distancia en pulsos coincide con la original.
"""
from __future__ import annotations


def acordes(notas, tol=0.05):
    """notas: [(inicio, altura)] → lista de (inicio, frozenset(alturas)) agrupando por instante."""
    out = []
    for t, p in sorted(notas):
        if out and abs(t - out[-1][0]) <= tol:
            out[-1][1].add(p)
        else:
            out.append((t, {p}))
    return [(t, frozenset(s)) for t, s in out]


def _lcs(a, b):
    """Pares (i, j) de la subsecuencia común más larga, comparando los acordes completos."""
    n, m = len(a), len(b)
    if n == 0 or m == 0:
        return []
    prev = [0] * (m + 1)
    filas = [prev]
    for i in range(1, n + 1):
        cur = [0] * (m + 1)
        ai = a[i - 1]
        for j in range(1, m + 1):
            cur[j] = prev[j - 1] + 1 if ai == b[j - 1] else (prev[j] if prev[j] >= cur[j - 1] else cur[j - 1])
        filas.append(cur)
        prev = cur
    i, j, pares = n, m, []
    while i > 0 and j > 0:
        if a[i - 1] == b[j - 1]:
            pares.append((i - 1, j - 1)); i -= 1; j -= 1
        elif filas[i - 1][j] >= filas[i][j - 1]:
            i -= 1
        else:
            j -= 1
    return pares[::-1]


def comparar(verdad, leido):
    """verdad y leido: [(inicio, altura)]. Devuelve dict con las tres métricas."""
    va, le = acordes(verdad), acordes(leido)
    pares = _lcs([c for _, c in va], [c for _, c in le])
    ok = sum(len(va[i][1]) for i, _ in pares)
    n_ver = sum(len(c) for _, c in va); n_est = sum(len(c) for _, c in le)
    p = ok / n_est if n_est else 0.0; r = ok / n_ver if n_ver else 0.0
    f_cont = 2 * p * r / (p + r) if p + r else 0.0
    bien = 0
    for (i0, j0), (i1, j1) in zip(pares, pares[1:]):
        if abs((va[i1][0] - va[i0][0]) - (le[j1][0] - le[j0][0])) <= 0.06:
            bien += 1
    f_ritmo = bien / max(1, len(pares) - 1)
    # estricta
    usados, tp = set(), 0
    ev = sorted(verdad)
    for v in ev:
        mejor, md = None, 1e9
        for k, e in enumerate(leido):
            if k in usados or e[1] != v[1]:
                continue
            d = abs(e[0] - v[0])
            if d <= 0.06 and d < md:
                mejor, md = k, d
        if mejor is not None:
            usados.add(mejor); tp += 1
    pe = tp / len(leido) if leido else 0.0; re_ = tp / len(ev) if ev else 0.0
    f_est = 2 * pe * re_ / (pe + re_) if pe + re_ else 0.0
    return {"contenido": f_cont, "ritmo": f_ritmo, "estricta": f_est, "notas_verdad": n_ver, "notas_leidas": n_est}
