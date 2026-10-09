"""Geometría de las hombreras de Astrid (Cómo entrenar a tu dragón 2) · EVA 5 mm · hojas A4.

Modelo: la hombrera es un casquete esférico. Todas las láminas convergen en un polo P
(el disco frontal) y son «gajos» de esfera entre dos meridianos, solapados como tejas.
Unidades: cm. Patrón plano de cada lámina: eje x = arco desde el polo, eje y = ancho.
"""
import math
from shapely.geometry import Polygon, Point, box as sbox
from shapely import affinity

# ---------------------------------------------------------------- medidas base (mujer 1,65 m)
R = 12.5                 # radio de la cúpula (fibra neutra)
T_END = math.radians(100)  # extensión desde el polo (frontal) hasta el borde trasero
OV = 1.0                 # solape de teja (la lámina inferior se mete 1 cm bajo la superior)
T = 0.5                  # grosor EVA
TIP_A = 0.9              # versión A: recorte de la punta (cm de arco desde el polo)
TIP_B = 2.0              # versión B: las láminas empiezan bajo el disco
DISC_D, DISC2_D = 6.6, 5.0

# reparto de meridianos (grados), de arriba (cuello) hacia abajo (brazo)
PHI0 = 15.0
VERS = {
    "A": dict(name="5 láminas (ref. 1)", widths=[15, 15, 15, 15, 15], tip=TIP_A,
              ids=["A1", "A2", "A3", "A4", "A5"]),
    "B": dict(name="abanico con disco (ref. 2)", widths=[13, 13, 13, 16, 20], tip=TIP_B,
              ids=["B1", "B2", "B3", "B4", "B5"]),
}


def lame(widths, i, tip, r=None, n=90):
    """Patrón plano de la lámina i (0 = superior). Lado y>0 = borde superior (lleva el solape)."""
    r = r or R
    dphi = math.radians(widths[i])
    t0 = tip / r
    ts = [t0 + (T_END - t0) * k / n for k in range(n + 1)]
    low, up = [], []
    for t in ts:
        h = r * math.sin(t) * dphi / 2
        ov = 0.0 if i == 0 else OV * min(1.0, 2 * h / 2.4)
        low.append((r * t, -h))
        up.append((r * t, h + ov))
    p = Polygon(low + list(reversed(up)))
    return p.buffer(-0.3, join_style=1).buffer(0.3, join_style=1)


def rivets(widths, i, tip, r=None, version="A"):
    """Posiciones de remache (guía opcional) sobre el eje visible de la lámina."""
    r = r or R
    dphi = math.radians(widths[i])
    ts = [40, 68, 94] if (version == "A" or widths[i] >= 16) else [55, 90]
    out = []
    for td in ts:
        t = math.radians(td)
        out.append((r * t, -0.1 * r * math.sin(t) * dphi))
    return out


def disc(d):
    return Point(0, 0).buffer(d / 2, 64)


def sphere_pt(t, phi_deg, r=None):
    """Punto 3D: polo en +X (frente), φ desde arriba (+Z) hacia el exterior (+Y)."""
    r = r or R
    ph = math.radians(phi_deg)
    return (r * math.cos(t), r * math.sin(t) * math.sin(ph), r * math.sin(t) * math.cos(ph))


# ---------------------------------------------------------------- maquetación A4
PAGE_W, PAGE_H = 29.7, 21.0
MARGIN = 0.6
SAFE = sbox(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN)
CONTENT_TOP = 19.65
GAP = 0.35


def size(p):
    a, b, c, d = p.bounds
    return c - a, d - b


def at(p, x, y):
    a, b, _, _ = p.bounds
    return affinity.translate(p, x - a, y - b)


def drop_pack(pieces, x_cands="ends", rots=(0, 180), y_top=CONTENT_TOP, blocked=()):
    """Coloca piezas por «gravedad» (estilo tetris). pieces: lista de (clave, polígono).
    Devuelve (colocadas{clave: (poly, rot)}, sobrantes[claves])."""
    placed, left = {}, []
    occupied = list(blocked)
    for key, poly in pieces:
        best = None
        for rot in rots:
            pr = affinity.rotate(poly, rot, origin="centroid") if rot else poly
            w, h = size(pr)
            if x_cands == "grid":
                xs = [MARGIN + 0.3 + k * 0.5 for k in range(int((PAGE_W - 2 * MARGIN - 0.6 - w) / 0.5) + 1)]
            elif x_cands == "left":
                xs = [MARGIN + 0.3]
            else:
                xs = [MARGIN + 0.3, PAGE_W - MARGIN - 0.3 - w]
            for x in xs:
                if x + w > PAGE_W - MARGIN - 0.05:
                    continue
                y = y_top - h
                cand = at(pr, x, y)
                while any(cand.distance(o) < GAP for o in occupied) and y > MARGIN + 0.05:
                    y -= 0.2
                    cand = at(pr, x, y)
                if y <= MARGIN + 0.05 or any(cand.distance(o) < GAP for o in occupied):
                    continue
                step = 0.05
                while y - step >= MARGIN + 0.05:
                    c2 = at(pr, x, y - step)
                    if any(c2.distance(o) < GAP for o in occupied):
                        break
                    y -= step
                    cand = c2
                score = (round(y + h, 2), x)
                if best is None or score < best[0]:
                    best = (score, cand, rot)
        if best is None:
            left.append(key)
        else:
            placed[key] = (best[1], best[2])
            occupied.append(best[1])
    return placed, left


if __name__ == "__main__":
    for v, cfg in VERS.items():
        print(v, cfg["name"])
        for i, iid in enumerate(cfg["ids"]):
            p = lame(cfg["widths"], i, cfg["tip"])
            print("  ", iid, [round(x, 2) for x in size(p)])
