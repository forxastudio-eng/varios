"""Geometría de las braceras de Hipo (EVA 5 mm, hojas A4).

Todas las medidas en centímetros. Convención: y hacia arriba.
Talla de referencia: hombre 1,75 m, complexión normal.
"""
import math
from shapely.geometry import Polygon, Point, box
from shapely import affinity

# ---------------------------------------------------------------- medidas base
H = 17.5            # largo del cuerpo del brazal (sobre la generatriz del cono)
TOP_SKIN = 26.0     # perímetro del antebrazo en el borde superior (a 21,5 cm de la muñeca)
BOT_SKIN = 18.0     # perímetro del antebrazo en el borde inferior (a 4 cm de la muñeca)
EASE = 1.5          # holgura para manga
GAP = 2.0           # hueco de cierre entre bordes
T = 0.5             # grosor EVA
CUFF_H = 3.0        # alto del puño
RIM_W = 1.0         # ancho de los ribetes
STRAP_W = 1.5       # ancho de las cinchas
STRAP_S = [3.0, 8.75, 14.5]   # distancia (desde el borde superior) al centro de cada cincha
STRAP_OVER = 1.0    # la cincha es 1 cm más larga que el arco (0,5 de margen + 1,5 que sobresale... ver tutorial)
PLATE_L = 11.5
PLATE_TAB = 1.5


def cone(top_skin=TOP_SKIN, bot_skin=BOT_SKIN, h=H, ease=EASE, gap=GAP, t=T):
    L1 = top_skin + ease + math.pi * t - gap
    L2 = bot_skin + ease + math.pi * t - gap
    theta = (L1 - L2) / h
    R2 = L2 / theta
    R1 = R2 + h
    w = 2 * R1 * math.sin(theta / 2)
    hh = R1 - R2 * math.cos(theta / 2)
    return dict(L1=L1, L2=L2, theta=theta, R1=R1, R2=R2, bw=w, bh=hh)


C = cone()
R1, R2, THETA = C["R1"], C["R2"], C["theta"]


def arc_pts(r, a0, a1, n=90):
    """Puntos de un arco de radio r centrado en el origen; ángulo medido desde la vertical (y+)."""
    return [(r * math.sin(a0 + (a1 - a0) * i / n), r * math.cos(a0 + (a1 - a0) * i / n)) for i in range(n + 1)]


def annular_sector(r_out, r_in, theta=THETA):
    outer = arc_pts(r_out, -theta / 2, theta / 2)
    inner = arc_pts(r_in, theta / 2, -theta / 2)
    return Polygon(outer + inner)


def body():
    return annular_sector(R1, R2)


def rim_top():
    return annular_sector(R1, R1 - RIM_W)


def rim_bottom():
    return annular_sector(R2 + RIM_W, R2)


def cuff():
    return annular_sector(R2, R2 - CUFF_H)


def strap(s_center):
    arc = (R1 - s_center) * THETA
    length = round(arc + STRAP_OVER, 1)
    w = STRAP_W
    tip = 0.9
    p = Polygon([(0, 0), (length - tip, 0), (length, w / 2), (length - tip, w), (0, w)])
    return p, length


def _mirror(half):
    """half: lista de (semiancho, y) de arriba a abajo del lado derecho."""
    right = [(x, y) for x, y in half]
    left = [(-x, y) for x, y in reversed(half)]
    return right + left


def _chaikin(pts, n=3):
    for _ in range(n):
        out = []
        for i in range(len(pts)):
            p, q = pts[i], pts[(i + 1) % len(pts)]
            out.append((0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]))
            out.append((0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]))
        pts = out
    return pts


def plate():
    """Placa de dorso de mano (contorno suavizado). Eje largo vertical; y=0 arriba (muñeca), y=-PLATE_L abajo (nudillos)."""
    half = [(3.6, 0.0), (3.9, -1.5), (4.6, -3.4), (4.85, -5.2), (4.7, -7.2), (4.0, -9.0), (2.7, -10.4), (1.1, -11.3)]
    pts = half + [(0.0, -11.5)] + [(-x, y) for x, y in reversed(half)]
    pts = [(x, y) for x, y in pts]
    # esquinas superiores marcadas con puntos dobles para que el suavizado las conserve casi rectas
    pts = [(3.6, 0.0), (3.6, 0.0)] + pts[1:-1] + [(-3.6, 0.0), (-3.6, 0.0)]
    poly = Polygon(_chaikin(pts, 2))
    minx, miny, maxx, maxy = poly.bounds
    # normaliza para que el largo sea exactamente PLATE_L
    return affinity.scale(poly, 1.0, PLATE_L / (maxy - miny), origin=(0, maxy))


def sheath_base():
    prof = [(1.2, 0.0), (1.3, 1.8), (1.0, 5.5), (0.6, 7.0)]
    pts = prof + [(0.0, 7.6)] + [(-x, y) for x, y in reversed(prof)]
    return Polygon(pts).buffer(-0.25).buffer(0.25)


def sheath_cover():
    return sheath_base().buffer(-0.3)


def grip():
    prof = [(0.55, 0.0), (0.7, 1.2), (0.62, 3.0), (0.72, 4.4), (0.58, 4.6)]
    pts = prof + [(-x, y) for x, y in reversed(prof)]
    return Polygon(pts)


def guard():
    prof = [(1.5, 0.1), (1.4, 0.7), (0.6, 0.8), (0.0, 0.8)]
    pts = [(1.5, 0.1), (1.4, 0.7), (0.6, 0.8), (-0.6, 0.8), (-1.4, 0.7), (-1.5, 0.1), (-0.6, 0.0), (0.6, 0.0)]
    return Polygon(pts)


def pommel():
    return Point(0, 0).buffer(0.75, 32)


# ------------------------------------------------------------------ utilidades
def at(poly, x, y):
    """Traslada la pieza para que su esquina inferior izquierda del bbox quede en (x,y)."""
    minx, miny, _, _ = poly.bounds
    return affinity.translate(poly, x - minx, y - miny)


def size(poly):
    minx, miny, maxx, maxy = poly.bounds
    return maxx - minx, maxy - miny


# --------------------------------------------------------------------- páginas
PAGE_W, PAGE_H = 29.7, 21.0
SAFE = box(0.8, 0.8, 28.9, 20.2)
CONTENT_TOP = 19.4      # por encima: cabecera de la hoja (P2-P4)
MIN_GAP = 0.35


def layout_p1():
    b = body()
    w, h = size(b)
    return {"A": at(b, (PAGE_W - w) / 2, 1.0)}


def layout_p2():
    items = {}
    y = 0.9
    # ribetes superiores x2 (anidados)
    r = rim_top()
    rw, rh = size(r)
    r1 = at(r, 1.2, y)
    shift = RIM_W + 0.45
    r2 = affinity.translate(r1, 0, shift)
    items["C1"] = r1
    items["C2"] = r2
    y = r2.bounds[3] + 0.6
    # cinchas: 3 largos x2, de la más corta a la más larga hacia arriba
    for k in (2, 1, 0):
        for rep in (2, 1):
            p, ln = strap(STRAP_S[k])
            items[f"B{k + 1}.{rep}"] = at(p, 1.2, y)
            y += STRAP_W + 0.5
    return items


def layout_p3():
    items = {}
    pl = affinity.rotate(plate(), 90, origin=(0, 0))
    pw, ph = size(pl)
    y = 0.9
    c = cuff()
    cw, ch = size(c)
    c1 = at(c, 1.2, y)
    shift = CUFF_H + 0.45
    c2 = affinity.translate(c1, 0, shift)
    items["E1"] = c1
    items["E2"] = c2
    y = c2.bounds[3] + 0.7
    items["F1"] = at(pl, 1.2, y)
    items["F2"] = at(pl, 1.2 + pw + 0.8, y)
    return items


def layout_p4():
    items = {}
    y = 0.9
    r = rim_bottom()
    r1 = at(r, 1.2, y)
    r2 = affinity.translate(r1, 0, RIM_W + 0.45)
    items["D1"] = r1
    items["D2"] = r2
    y = r2.bounds[3] + 1.0
    x = 1.2
    sb, sc = sheath_base(), sheath_cover()
    items["G1"] = at(sb, x, y)
    x = items["G1"].bounds[2] + 0.7
    items["G2"] = at(sc, x, y)
    x = items["G2"].bounds[2] + 1.2
    # empuñaduras (2) en vertical
    g = grip()
    for rep in (1, 2):
        items[f"H1.{rep}"] = at(g, x, y)
        x = items[f"H1.{rep}"].bounds[2] + 0.6
    x += 0.4
    gd = guard()
    for rep in (1, 2):
        items[f"H2.{rep}"] = at(gd, x, y + 0.2 + (rep - 1) * 1.4)
    x = items["H2.1"].bounds[2] + 0.9
    pm = pommel()
    for rep in (1, 2):
        items[f"H3.{rep}"] = at(pm, x, y + 0.2 + (rep - 1) * 1.9)
    return items


def check(items, name, top_limit=None):
    keys = list(items)
    ok = True
    for k in keys:
        if not SAFE.contains(items[k]):
            print(f"  [!] {name}:{k} fuera de la zona segura", items[k].bounds)
            ok = False
        if top_limit and items[k].bounds[3] > top_limit:
            print(f"  [!] {name}:{k} invade la cabecera (y max {items[k].bounds[3]:.2f})")
            ok = False
    for i in range(len(keys)):
        for j in range(i + 1, len(keys)):
            d = items[keys[i]].distance(items[keys[j]])
            if d < MIN_GAP:
                print(f"  [!] {name}: {keys[i]} y {keys[j]} separados {d:.2f} cm (< {MIN_GAP})")
                ok = False
    return ok


if __name__ == "__main__":
    print({k: round(v, 3) for k, v in C.items()})
    for n, f in (("P1", layout_p1), ("P2", layout_p2), ("P3", layout_p3), ("P4", layout_p4)):
        it = f()
        print(n, "OK" if check(it, n, None if n == "P1" else CONTENT_TOP) else "REVISAR",
              "ymax", round(max(p.bounds[3] for p in it.values()), 2))
    for s in STRAP_S:
        print("cincha s=", s, strap(s)[1])
    print("plate", size(plate()), "sheath", size(sheath_base()), "cover", size(sheath_cover()))
