"""Geometría de las braceras de Hipo · versión completa (EVA 5 mm, hojas A4).

Cada bracera = 4 piezas principales:
  1. BASE      · capa oscura envolvente, con pico alto hacia el codo (lado posterior)
  2. PANEL     · panel frontal marrón con ranuras para las cinchas
  3. PUÑO      · muñequera oscura sobre el borde inferior de la base
  4. GUARDA    · guarda de mano (versión A: placa única · versión B: segmentada)
+ accesorios: cinchas (3 filas × 3 tramos) y daga (solo brazo izquierdo).

Unidades: cm.  Coordenadas de patrón: ápice del cono en el origen, eje y hacia arriba,
cara exterior del brazo en el centro (x = 0) y hueco de cierre en los bordes laterales.
Las piezas se dibujan para el BRAZO IZQUIERDO (el de la daga); el derecho es su espejo.
"""
import math
from shapely.geometry import Polygon, Point, box as sbox
from shapely import affinity
from shapely.ops import unary_union, transform

# ------------------------------------------------------------- medidas de partida
T = 0.5             # grosor EVA
EASE = 1.5          # holgura sobre el perímetro (manga + movimiento)
GAP = 3.0           # hueco de cierre en el lado interior (lo cierran las cinchas)


def skin(y):
    """Perímetro del antebrazo (cm) a y cm por encima del pliegue de la muñeca · hombre 1,75 m."""
    return 17.0 + 9.0 * (max(y, 0.0) / 21.5) ** 1.3


# alturas (cm sobre el pliegue de la muñeca)
BASE_Y0, BASE_Y1 = 3.0, 18.5             # base: borde inferior / borde superior (lado interior)
PEAK_H, PEAK_C, PEAK_W = 3.2, 1.6, 9.5   # pico: alto, centro (arco desde el eje, + = posterior), semiancho
CUFF_Y0, CUFF_Y1 = 2.0, 5.0              # puño: sobresale 1 cm por debajo de la base
PANEL_Y0, PANEL_Y1 = 5.3, 17.0           # panel frontal
PANEL_W = 8.6                            # ancho del panel a media altura (medido en plano)
PANEL_R = 1.1                            # radio de esquinas del panel
STRAP_W = 1.4
STRAP_Y = [14.9, 11.6, 8.0]              # filas de cinchas (centro), de arriba abajo
SLOT_IN = 0.9                            # distancia del centro de la ranura al borde del panel
SLOT_W, SLOT_L = 0.5, STRAP_W + 0.2      # ranura: ancho (en arco) × largo (vertical)
TUCK = 0.6                               # cuánto se mete la cincha en la ranura
TAIL = 2.0                               # punta libre de la cincha lateral (para hebilla)
DAGGER_X = -1.6                          # eje de la daga (arco desde el centro, − = anterior)
DAGGER_Y0 = 5.6                          # base del pomo

# ------------------------------------------------------------- cono base
C0 = skin(BASE_Y0) + EASE + math.pi * T      # perímetro de la fibra neutra
C1 = skin(BASE_Y1) + EASE + math.pi * T
H = BASE_Y1 - BASE_Y0
THETA = (C1 - C0) / H                          # ángulo del desarrollo completo
R0 = C0 / THETA                                # radio de desarrollo del borde inferior


def R_at(y, offset=0.0):
    """Radio de desarrollo a la altura y, sobre la superficie desplazada 'offset' cm hacia fuera."""
    return R0 + (y - BASE_Y0) + offset * 2 * math.pi / THETA


def circ_at(y, offset=0.0):
    return R_at(y, offset) * THETA


def pol(r, a):
    return (r * math.sin(a), r * math.cos(a))


def _gap_cut(poly):
    """Recorta el hueco de cierre: bandas paralelas a la generatriz de costura (lado interior)."""
    big = 200.0
    a = THETA / 2
    out = poly
    for sgn in (1, -1):
        d = (sgn * math.sin(a), math.cos(a))            # generatriz de costura
        n = (-sgn * math.cos(a), sgn * math.sin(a))     # normal hacia el interior de la pieza
        o = (n[0] * GAP / 2, n[1] * GAP / 2)
        p1 = (o[0] - d[0] * big, o[1] - d[1] * big)
        p2 = (o[0] + d[0] * big, o[1] + d[1] * big)
        hp = Polygon([p1, p2, (p2[0] + n[0] * big, p2[1] + n[1] * big), (p1[0] + n[0] * big, p1[1] + n[1] * big)])
        out = out.intersection(hp)
    return out


def ring_piece(y0, y1, offset=0.0, top_fn=None, n=240):
    """Banda cónica completa entre y0 e y1 (con hueco de cierre). top_fn(arco) -> subida extra del borde superior."""
    r0, r1 = R_at(y0, offset), R_at(y1, offset)
    a = THETA / 2 + 0.05
    top = []
    for i in range(n + 1):
        ang = -a + 2 * a * i / n
        extra = top_fn(ang * r1) if top_fn else 0.0
        top.append(pol(r1 + extra, ang))
    bot = [pol(r0, a - 2 * a * i / n) for i in range(n + 1)]
    return _gap_cut(Polygon(top + bot))


def peak(s):
    u = (s - PEAK_C) / PEAK_W
    if abs(u) >= 1:
        return 0.0
    return PEAK_H * (0.5 * (1 + math.cos(math.pi * u))) ** 1.15


def base():
    return ring_piece(BASE_Y0, BASE_Y1, 0.0, peak)


def cuff():
    return ring_piece(CUFF_Y0, CUFF_Y1, T)


def panel_angle():
    ym = (PANEL_Y0 + PANEL_Y1) / 2
    return PANEL_W / R_at(ym, T)


def panel_outline():
    phi = panel_angle()
    r0, r1 = R_at(PANEL_Y0, T), R_at(PANEL_Y1, T)
    n = 80
    top = [pol(r1, -phi / 2 + phi * i / n) for i in range(n + 1)]
    bot = [pol(r0, phi / 2 - phi * i / n) for i in range(n + 1)]
    return Polygon(top + bot).buffer(-PANEL_R, join_style=1).buffer(PANEL_R, join_style=1)


def slots():
    """Ranuras del panel (rectángulos radiales) en coordenadas de panel."""
    phi = panel_angle()
    out = []
    for y in STRAP_Y:
        r = R_at(y, T)
        half = r * phi / 2
        for sgn in (-1, 1):
            ang = sgn * (half - SLOT_IN) / r
            rect = sbox(-SLOT_W / 2, -SLOT_L / 2, SLOT_W / 2, SLOT_L / 2)
            rect = affinity.rotate(rect, -math.degrees(ang), origin=(0, 0))
            cx, cy = pol(r, ang)
            out.append(affinity.translate(rect, cx, cy))
    return out


def panel():
    return panel_outline().difference(unary_union(slots()))


def to_base(p, offset=T):
    """Proyecta una figura dibujada sobre la superficie 'offset' al patrón de la base."""
    dr = offset * 2 * math.pi / THETA

    def f(xs, ys, zs=None):
        out_x, out_y = [], []
        for x, y in zip(xs, ys):
            r = math.hypot(x, y)
            a = math.atan2(x, y)
            px, py = pol(r - dr, a)
            out_x.append(px)
            out_y.append(py)
        return out_x, out_y
    return transform(f, p)


def arc_xy(s, y, offset=0.0):
    """Punto de patrón a 's' cm de arco desde el eje, a la altura y, en la superficie 'offset'."""
    r = R_at(y, offset)
    return pol(r, s / r)


# ------------------------------------------------------------- cinchas
def strap_lengths(y):
    base_half = (circ_at(y, T) - GAP) / 2              # superficie donde apoyan los tramos laterales
    panel_half_t = R_at(y, T) * panel_angle() / 2      # semiancho del panel
    panel_half_c = R_at(y, 2 * T) * panel_angle() / 2  # semiancho sobre la cara del panel
    medial = base_half - panel_half_t
    lateral = medial + TAIL
    central = 2 * (panel_half_c - SLOT_IN) + 2 * TUCK
    return dict(medial=medial, lateral=lateral, central=central, central_daga=central + 2.2)


def strap(length, tip=False, w=STRAP_W):
    if tip:
        return Polygon([(0, 0), (length - 0.8, 0), (length, w / 2), (length - 0.8, w), (0, w)])
    return Polygon([(0, 0), (length, 0), (length, w), (0, w)])


# ------------------------------------------------------------- guarda de mano
def _chaikin(pts, n=3):
    for _ in range(n):
        out = []
        for i in range(len(pts)):
            p, q = pts[i], pts[(i + 1) % len(pts)]
            out.append((0.75 * p[0] + 0.25 * q[0], 0.75 * p[1] + 0.25 * q[1]))
            out.append((0.25 * p[0] + 0.75 * q[0], 0.25 * p[1] + 0.75 * q[1]))
        pts = out
    return pts


GUARD_TOP = 2.8      # y del borde superior (queda bajo el vuelo del puño)
GUARD_TIP = -10.3    # punta (nudillos)


def guard_plate():
    """Versión A · placa única (película). Coordenadas de mano: y = cm sobre el pliegue de la muñeca."""
    half = [(3.5, GUARD_TOP), (3.6, 1.0), (4.2, -1.5), (4.5, -4.0), (4.3, -6.5), (3.7, -8.4), (2.5, -9.5), (1.1, -10.1)]
    pts = half + [(0.0, GUARD_TIP)] + [(-x, y) for x, y in reversed(half)]
    pts = [half[0]] + pts + [(-half[0][0], half[0][1])]
    return Polygon(_chaikin(pts, 2))


STRIP_TOP = 0.9      # y donde empiezan las láminas (bajo la banda)
BAND_Y0 = 0.4        # banda de muñeca de la guarda segmentada: de BAND_Y0 a GUARD_TOP
STRIPS = [           # (x arriba, x abajo, y punta)
    (-2.85, -3.35, -8.9),
    (-0.95, -1.12, -9.9),
    (0.95, 1.12, -10.1),
    (2.85, 3.35, -9.3),
]
STRIP_W = 1.8


def guard_strip(i):
    xt, xb, yb = STRIPS[i]
    w = STRIP_W / 2
    yc = yb + w
    pts = [(xt - w, STRIP_TOP), (xt + w, STRIP_TOP)]
    n = 16
    for k in range(n + 1):
        a = -math.pi * k / n
        pts.append((xb + w * math.cos(a), yc + w * math.sin(a)))
    return Polygon(pts)


def guard_band():
    return Polygon([(-3.85, GUARD_TOP), (3.85, GUARD_TOP), (4.05, BAND_Y0), (-4.05, BAND_Y0)]).buffer(-0.2, join_style=1).buffer(0.2, join_style=1)


def guard_seg_base():
    parts = [guard_strip(i).buffer(0.35, join_style=1) for i in range(4)]
    parts.append(Polygon([(-3.6, GUARD_TOP), (3.6, GUARD_TOP), (4.15, 0.0), (-4.15, 0.0)]))
    return unary_union(parts).buffer(0.4, join_style=1).buffer(-0.4, join_style=1)


# ------------------------------------------------------------- daga
SHEATH_L = 8.4


def sheath_base():
    prof = [(1.25, 0.0), (1.32, 1.8), (1.05, 5.8), (0.6, 7.6)]
    pts = prof + [(0.0, SHEATH_L)] + [(-x, y) for x, y in reversed(prof)]
    return Polygon(pts).buffer(-0.25).buffer(0.25)


def sheath_cover():
    return sheath_base().buffer(-0.3)


def grip():
    prof = [(0.52, 0.0), (0.66, 1.1), (0.58, 2.6), (0.68, 4.2), (0.55, 4.4)]
    return Polygon(prof + [(-x, y) for x, y in reversed(prof)])


def crossguard():
    return Polygon([(1.5, 0.1), (1.4, 0.7), (0.6, 0.8), (-0.6, 0.8), (-1.4, 0.7), (-1.5, 0.1), (-0.6, 0.0), (0.6, 0.0)])


def pommel():
    return Point(0, 0).buffer(0.65, 32)


def dagger_parts():
    """Daga montada (local: x=0 eje, y=0 base del pomo)."""
    return dict(
        pommel=affinity.translate(pommel(), 0, 0.65),
        grip=affinity.translate(grip(), 0, 1.3),
        guard=affinity.translate(crossguard(), 0, 1.3 + 4.4),
        sheath=affinity.translate(sheath_base(), 0, 1.3 + 4.4 + 0.8),
    )


DAGGER_LEN = 1.3 + 4.4 + 0.8 + SHEATH_L

# ------------------------------------------------------------- maquetación
PAGE_W, PAGE_H = 29.7, 21.0
MARGIN = 0.6
SAFE = sbox(MARGIN, MARGIN, PAGE_W - MARGIN, PAGE_H - MARGIN)
CONTENT_TOP = 19.65
MIN_GAP = 0.3


def at(poly, x, y):
    minx, miny, _, _ = poly.bounds
    return affinity.translate(poly, x - minx, y - miny)


def size(poly):
    minx, miny, maxx, maxy = poly.bounds
    return maxx - minx, maxy - miny


def mirror(p):
    return affinity.scale(p, -1, 1, origin=(0, 0))


def check(items, name, top_limit=CONTENT_TOP, boxes=()):
    ok = True
    keys = list(items)
    for k in keys:
        if not SAFE.contains(items[k]):
            print(f"  [!] {name}:{k} fuera de zona segura {tuple(round(v, 2) for v in items[k].bounds)}")
            ok = False
        if top_limit and items[k].bounds[3] > top_limit:
            print(f"  [!] {name}:{k} invade la cabecera ({items[k].bounds[3]:.2f})")
            ok = False
    for i in range(len(keys)):
        for j in range(i + 1, len(keys)):
            d = items[keys[i]].distance(items[keys[j]])
            if d < MIN_GAP:
                print(f"  [!] {name}: {keys[i]} / {keys[j]} a {d:.2f} cm")
                ok = False
    for bn, b in boxes:
        for kk in keys:
            if items[kk].intersects(b):
                print(f"  [!] {name}: {kk} pisa la caja {bn}")
                ok = False
    return ok


if __name__ == "__main__":
    print(f"C0={C0:.2f} C1={C1:.2f} H={H} THETA={THETA:.4f} ({math.degrees(THETA):.1f}°) R0={R0:.2f}")
    b = base()
    print("base", [round(v, 2) for v in size(b)])
    print("cuff", [round(v, 2) for v in size(cuff())])
    print("panel", [round(v, 2) for v in size(panel())], "ancho arriba/abajo",
          round(R_at(PANEL_Y1, T) * panel_angle(), 2), round(R_at(PANEL_Y0, T) * panel_angle(), 2))
    for y in STRAP_Y:
        print("cinchas y=", y, {k: round(v, 2) for k, v in strap_lengths(y).items()})
    print("guarda A", [round(v, 2) for v in size(guard_plate())])
    print("guarda B base", [round(v, 2) for v in size(guard_seg_base())], "banda", [round(v, 2) for v in size(guard_band())])
    for i in range(4):
        print(" lámina", i + 1, [round(v, 2) for v in size(guard_strip(i))])
    print("cima de la base sobre la daga:", round(BASE_Y1 + peak(DAGGER_X), 2),
          "· punta de la funda:", round(DAGGER_Y0 + DAGGER_LEN, 2))
