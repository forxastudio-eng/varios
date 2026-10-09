"""Ficha técnica + patrones A4 1:1 + tutorial de las hombreras de Astrid (EVA 5 mm)."""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import comun  # noqa: E402
import geom as G  # noqa: E402
from reportlab.lib import colors  # noqa: E402
from reportlab.pdfgen import canvas  # noqa: E402
from reportlab.platypus import Paragraph  # noqa: E402
from shapely import affinity  # noqa: E402
from shapely.geometry import LineString, Point  # noqa: E402

# ------------------------------------------------------------------ tema (acero + bronce)
comun.BROWN = colors.HexColor("#34495E")
comun.ACCENT = colors.HexColor("#C9822F")
comun.CREAM = colors.HexColor("#F1F3F5")
comun.LINE = colors.HexColor("#CBD2D9")
comun.WARM = colors.HexColor("#FBF3E8")
for k in ("h2", "h3"):
    comun.ST[k].textColor = comun.BROWN
comun.FOOTER[0] = "Hombreras de Astrid · ficha técnica · goma EVA 5 mm · talla de referencia mujer 1,65 m"
from comun import (CM, DARK, GRAYT, GUIDE, PH, PW, ST, box, bullets, checklist, chrome, draw_line, draw_poly,  # noqa: E402
                   f1, para, path_pts, steps_block, table, text)

HEAD, ACC, CREAM, WARM = comun.BROWN, comun.ACCENT, comun.CREAM, comun.WARM
STEEL = [colors.HexColor(h) for h in ("#9AA3AB", "#8E979F", "#838C95", "#78818A", "#6E7780")]
STEEL_EDGE = colors.HexColor("#3B4249")
OUT = os.path.join(HERE, "Hombreras_Astrid_Ficha_y_Patrones.pdf")
OUT_PRINT = os.path.join(HERE, "Hombreras_Astrid_Guias_A4_imprimir.pdf")
TOTAL = 10


def sz(p):
    w, h = G.size(p)
    return f"{f1(w)} × {f1(h)}"


def lames(v="B"):
    return [G.lame(G.WIDTHS, i, G.TIP_B) for i in range(5)]


_SHEETS = {}


def sheets(k=1.0):
    if k not in _SHEETS:
        _SHEETS[k] = G.pack_sheets(G.all_pieces(k))
    return _SHEETS[k]


# ================================================================== dibujo 3D de la hombrera
def _view(az=48, el=18):
    a, e = math.radians(az), math.radians(el)
    V = (math.sin(a) * math.cos(e), math.cos(a) * math.cos(e), math.sin(e))       # hacia la cámara
    up0 = (0, 0, 1)
    r = (up0[1] * V[2] - up0[2] * V[1], up0[2] * V[0] - up0[0] * V[2], up0[0] * V[1] - up0[1] * V[0])
    n = math.sqrt(sum(x * x for x in r))
    right = tuple(x / n for x in r)
    up = (V[1] * right[2] - V[2] * right[1], V[2] * right[0] - V[0] * right[2], V[0] * right[1] - V[1] * right[0])
    return V, right, up


def draw_pauldron(c, ox, oy, sc, v="B", upto=5, hl=None, mirror=False, az=30, el=15, rivets=True, disc=True, ghost_rivets=False):
    """Hombrera izquierda vista desde fuera-delante. Las láminas se dibujan de abajo arriba (tejas)."""
    cfg = G.VERS[v]
    widths = cfg["widths"]
    V, right, up = _view(az, el)
    light = (0.45, 0.55, 0.7)

    def proj(p):
        x = sum(p[k] * right[k] for k in range(3))
        y = sum(p[k] * up[k] for k in range(3))
        if mirror:
            x = -x
        return (ox + x * sc, oy + y * sc)

    t0 = cfg["tip"] / G.R
    n = 40
    order = list(range(5))[::-1][:upto]       # de la inferior (4) a la superior (0)
    for i in order:
        p_up = G.PHI0 + sum(widths[:i])
        p_lo = p_up + widths[i]
        rr = G.R + 0.12 * (4 - i)             # las superiores quedan un poco por fuera
        ts = [t0 + (G.T_END - t0) * k / n for k in range(n + 1)]
        low = [G.sphere_pt(t, p_lo, rr) for t in ts]
        back = [G.sphere_pt(G.T_END, p_lo - (p_lo - p_up) * k / 8, rr) for k in range(9)]
        upp = [G.sphere_pt(t, p_up, rr) for t in reversed(ts)]
        pts3 = low + back + upp
        mid = G.sphere_pt((t0 + G.T_END) / 2, (p_up + p_lo) / 2, 1)
        shade = max(0.0, sum(mid[k] * light[k] for k in range(3)))
        base = 0.42 + 0.33 * shade
        fill = colors.Color(base, base * 1.02, base * 1.06)
        is_hl = (hl == i)
        path_pts(c, [proj(p) for p in pts3], fill, ACC if is_hl else STEEL_EDGE, 2.0 if is_hl else 0.7)
        # canto inferior (grosor)
        c.saveState()
        c.setStrokeColor(colors.HexColor("#2A2F34"))
        c.setLineWidth(1.6)
        pp = [proj(p) for p in low]
        pth = c.beginPath()
        pth.moveTo(pp[0][0] * CM, pp[0][1] * CM)
        for X, Y in pp[1:]:
            pth.lineTo(X * CM, Y * CM)
        c.drawPath(pth, stroke=1, fill=0)
        c.restoreState()
        if rivets:
            dphi = widths[i]
            tlist = [45, 85, 125] if dphi >= 16 else [60, 115]
            for td in tlist:
                t = math.radians(td)
                if t < t0 + 0.15:
                    continue
                pc = G.sphere_pt(t, p_up + dphi * 0.55, rr + 0.3)
                X, Y = proj(pc)
                rad = 0.34 * sc * 1.3
                c.saveState()
                if ghost_rivets:
                    c.setStrokeColor(GRAYT)
                    c.setDash(1.2, 1.2)
                    c.setLineWidth(0.6)
                    c.circle(X * CM, Y * CM, rad * CM, stroke=1, fill=0)
                else:
                    c.setFillColor(colors.HexColor("#B9C0C6"))
                    c.setStrokeColor(colors.HexColor("#3A4046"))
                    c.setLineWidth(0.5)
                    c.circle(X * CM, Y * CM, rad * CM, stroke=1, fill=1)
                    c.setFillColor(colors.HexColor("#E6EAED"))
                    c.circle((X - rad * 0.3) * CM, (Y + rad * 0.3) * CM, rad * 0.35 * CM, stroke=0, fill=1)
                c.restoreState()
    if v == "B" and disc and upto >= 5:
        for d, col in ((G.DISC_D, colors.HexColor("#7D868E")), (G.DISC2_D, colors.HexColor("#959EA6"))):
            pts = []
            for k in range(49):
                s = 2 * math.pi * k / 48
                p = (G.R + 1.2, d / 2 * math.cos(s), d / 2 * math.sin(s))
                pts.append(proj(p))
            path_pts(c, pts, col, ACC if hl == "disc" else STEEL_EDGE, 2.0 if hl == "disc" else 0.7)
    return proj


# ================================================================== diagrama de gajos (naranja)
def draw_gores_diagram(c, cx, cy, rr):
    """Esquema: esfera con el polo P y los meridianos que limitan las láminas."""
    V, right, up = _view(70, 22)

    def proj(p):
        return (cx + sum(p[k] * right[k] for k in range(3)) / G.R * rr, cy + sum(p[k] * up[k] for k in range(3)) / G.R * rr)
    c.saveState()
    c.setStrokeColor(colors.HexColor("#B9C0C6"))
    c.setFillColor(colors.HexColor("#F4F6F8"))
    c.circle(cx * CM, cy * CM, rr * CM, stroke=1, fill=1)
    c.restoreState()
    widths = G.VERS["B"]["widths"]
    phis = [G.PHI0 + sum(widths[:k]) for k in range(6)]
    for i in range(5):
        pts = []
        for k in range(31):
            t = 0.02 + (G.T_END - 0.02) * k / 30
            pts.append(G.sphere_pt(t, phis[i + 1]))
        back = [G.sphere_pt(G.T_END, phis[i + 1] - (phis[i + 1] - phis[i]) * k / 6) for k in range(7)]
        upp = [G.sphere_pt(0.02 + (G.T_END - 0.02) * (30 - k) / 30, phis[i]) for k in range(31)]
        path_pts(c, [proj(p) for p in pts + back + upp], STEEL[i], STEEL_EDGE, 0.6, alpha=0.85)
    P = proj(G.sphere_pt(0, 0))
    c.setFillColor(ACC)
    c.circle(P[0] * CM, P[1] * CM, 0.12 * CM, stroke=0, fill=1)
    text(c, "P (polo = disco)", P[0] - 0.2, P[1] - 0.5, 7.5, "LS-B", ACC, "c")
    return proj



# ================================================================== PÁG 1 · FICHA
MOD_NAMES = ["gajo superior (cuello)", "gajo", "gajo", "banda media", "banda inferior (brazo)"]


def page_ficha(c):
    c.setFillColor(HEAD)
    c.rect(0, (PH - 6.2) * CM, PW * CM, 6.2 * CM, stroke=0, fill=1)
    c.setFillColor(ACC)
    c.rect(0, (PH - 6.35) * CM, PW * CM, 0.15 * CM, stroke=0, fill=1)
    text(c, "FICHA TÉCNICA DE COSPLAY · PATRONES + TUTORIAL", 1.8, PH - 1.8, 9.5, "LS-B", colors.HexColor("#E8D3B8"))
    text(c, "HOMBRERAS DE ASTRID", 1.8, PH - 3.4, 32, "LS-B", colors.white)
    text(c, "Cómo entrenar a tu dragón 2", 1.8, PH - 4.35, 14, "LS-I", colors.HexColor("#D5DEE6"))
    text(c, "1/4 de esfera · 5 módulos + disco · goma EVA 5 mm · patrones 1:1 en A4 · talla de referencia mujer 1,65 m", 1.8, PH - 5.3, 9.5, "LS", colors.white)
    draw_pauldron(c, 5.6, PH - 12.3, 0.36, mirror=True)
    draw_pauldron(c, 15.4, PH - 12.3, 0.36)
    text(c, "Par de hombreras (izquierda y derecha) · vista exterior", PW / 2, PH - 15.6, 7.5, "LS-I", GRAYT, "c")
    y = PH - 16.1
    big = lames()[4]
    data = [["Dato", "Valor"],
            ["Pieza", "Par de hombreras en 1/4 de esfera: 5 módulos solapados en teja que convergen en un disco frontal"],
            ["Módulos por hombrera", "3 gajos estrechos arriba + 2 bandas anchas abajo (como las refs. 2 y 3) + disco de 2 capas"],
            ["Módulo mayor", f"M5 · banda inferior · {sz(big)} cm"],
            ["Tamaño montada", f"≈ {f1(G.R * G.T_END + G.DISC_D / 2)} cm de delante a atrás · {f1(G.R * math.pi / 2)} cm de alto en el lateral"],
            ["Goma EVA", f"{len(sheets())} hojas A4 de 5 mm para el par (+1 de repuesto)"],
            ["Técnicas", "Termoformado en cúpula sobre un balón, montaje en tejas, textura martillada y pintura metálica"],
            ["Dificultad · tiempo", "Media · 7–9 h el par (sin contar secados)"],
            ["Fuera de la ficha", "Remaches, correas y hebillas de sujeción, calaveras de la falda (3.ª foto)"]]
    y = table(c, data, 1.6, y, [4.2, 13.6], pad=2.6)
    y -= 0.35
    box(c, 1.6, y - 2.0, 17.8, 2.0, fill=CREAM)
    text(c, "Variante sin disco (ref. 1)", 1.9, y - 0.6, 9, "LS-B", HEAD)
    para(c, "Se usan los mismos 5 módulos. En lugar del disco, recorta las puntas para que acaben en una sola punta limpia y pon remaches más grandes.",
         1.9, y - 0.85, 17.2, "s")


# ================================================================== PÁG 2 · ANÁLISIS
def callout(c, n, p_from, p_to, color=None):
    color = color or ACC
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(0.8)
    c.line(p_from[0] * CM, p_from[1] * CM, p_to[0] * CM, p_to[1] * CM)
    c.setFillColor(color)
    c.circle(p_from[0] * CM, p_from[1] * CM, 0.07 * CM, stroke=0, fill=1)
    c.circle(p_to[0] * CM, p_to[1] * CM, 0.3 * CM, stroke=0, fill=1)
    c.setFillColor(colors.white)
    c.setFont("LS-B", 9)
    c.drawCentredString(p_to[0] * CM, p_to[1] * CM - 3.1, str(n))
    c.restoreState()


def page_analysis(c):
    chrome(c, 2, TOTAL, "ANÁLISIS", "Análisis de las referencias")
    y = para(c, "Las fotos de abajo (refs. 2 y 3) muestran el modelo final: un <b>cuarto de esfera</b> que cubre el hombro por arriba y por fuera, "
                "formado por <b>5 módulos</b> que nacen de un disco delantero y se abren hacia atrás como un abanico. Arriba hay tres gajos estrechos "
                "y abajo dos bandas anchas. La inferior tiene la curva del borde, que rodea el brazo en horizontal. Cada módulo pisa al de debajo como una teja.",
             1.6, PH - 2.2, 17.8, "b")
    sc = 0.5
    ox, oy = 8.0, y - 6.3
    proj = draw_pauldron(c, ox, oy, sc)
    w = G.WIDTHS

    def on(i, td, frac=0.5, dr=0.3):
        p_up = G.PHI0 + sum(w[:i])
        return proj(G.sphere_pt(math.radians(td), p_up + w[i] * frac, G.R + dr))
    callout(c, 1, on(4, 80, 0.75), (ox + 8.6, oy - 3.4))
    callout(c, 2, on(3, 110, 0.6), (ox + 8.6, oy - 1.0))
    callout(c, 3, on(1, 70, 0.5, 0.4), (ox + 8.6, oy + 3.2))
    callout(c, 4, proj((G.R + 1.2, 0, 0)), (ox - 6.0, oy - 2.6))
    callout(c, 5, on(2, 85, 0.6, 0.6), (ox + 8.6, oy + 1.1))
    callout(c, 6, on(4, 140, 0.98), (ox + 8.6, oy + 5.2))
    data = [["", "Qué se ve", "Cómo lo resolvemos"],
            ["1", "Banda inferior ancha; su canto rodea el brazo con una curva horizontal.", "Módulo M5 (27°); su borde inferior es el «ecuador» de la esfera."],
            ["2", "Segunda banda, algo más estrecha.", "Módulo M4 (21°)."],
            ["3", "Tres gajos estrechos que suben al hombro y convergen en el disco.", "Módulos M1–M3 (14° cada uno)."],
            ["4", "Disco doble que tapa la convergencia.", "D1 Ø 6,6 + D2 Ø 5,0 en dos capas."],
            ["5", "Remaches grandes y abombados, 2–3 por módulo.", "Fuera de la ficha: las hojas marcan dónde van."],
            ["6", "Borde trasero: los módulos acaban abiertos, sin cerrar la esfera.", "1/4 de esfera adaptado: corte trasero a 145° del polo."],
            ["—", "Textura martillada y arañazos.", "Acabado: bola de aluminio con calor + cortes."],
            ["—", "Calaveras pequeñas (ref. 3).", "Son de la falda de Astrid; no forman parte de las hombreras."]]
    yt = table(c, data, 1.6, oy - 5.0, [0.6, 8.6, 8.6], pad=2.3)
    yb = para(c, "Claves de construcción", 1.6, yt - 0.35, 17.8, "h2")
    yb = bullets(c, [
        "<b>Un cuarto de esfera:</b> los meridianos van de arriba del hombro (0°) hasta la horizontal (90°). Las 5 piezas reparten esos 90°: 14 + 14 + 14 + 21 + 27.",
        "<b>Un solo polo:</b> todos los módulos nacen delante del hombro, bajo el disco. Detrás se cortan a 145° para dejar libre el brazo.",
        "<b>Izquierda y derecha:</b> las piezas son las mismas. La segunda hombrera se monta con los módulos por la otra cara (en espejo).",
    ], 1.6, yb, 17.8, "bul", 0.1)
    assert yb > 1.7, yb


# ================================================================== PÁG 3 · GEOMETRÍA Y MEDIDAS
def page_geometry(c):
    chrome(c, 3, TOTAL, "MEDIDAS", "Geometría y medidas")
    y = para(c, f"La hombrera es <b>1/4 de esfera</b> de radio <b>R = {f1(G.R)} cm</b> (fibra neutra): una cuña de 90° entre la vertical del hombro "
                f"y la horizontal del brazo. Cada módulo es un <b>gajo</b> entre dos meridianos que salen del polo P, igual que la piel de una naranja. "
                f"Va de delante hacia atrás hasta {round(math.degrees(G.T_END))}° del polo.", 1.6, PH - 2.2, 17.8, "b")
    draw_gores_diagram(c, 5.3, y - 4.4, 3.6)
    text(c, "Los 5 módulos sobre la esfera", 5.3, y - 8.6, 7.5, "LS-I", GRAYT, "c")
    X0 = 10.2
    yy = para(c, "Desarrollo en plano de un módulo", X0, y - 0.1, 9.2, "h3")
    gl = lames()[3]
    w, h = G.size(gl)
    sc = 9.0 / w
    draw_poly(c, G.at(gl, 0, 0), fill=colors.HexColor("#C9CFD5"), stroke=DARK, lw=0.8, ox=X0, oy=yy - 0.4 - h * sc, sc=sc)
    para(c, "x = R · t (arco desde el polo)<br/>ancho(t) = R · sen t · Δφ<br/>+ 1 cm de solape en el borde superior (salvo M1)<br/>"
            "El ancho máximo cae a 90° del polo (el lateral del hombro).", X0, yy - 0.6 - h * sc, 9.2, "s")
    y = y - 9.2
    data = [["Cód.", "Medida (mujer 1,65 m, complexión normal)", "Valor"],
            ["A", "Arco del hombro de delante a atrás, pasando por encima, a la altura del deltoides y sobre la ropa", f"≈ {f1(G.R * G.T_END + G.DISC_D / 2)} cm"],
            ["B", "Altura en el lateral: de lo alto del hombro a la mitad del brazo", f"{f1(G.R * math.pi / 2)} cm"],
            ["C", "Perímetro del brazo en el deltoides (correa de sujeción)", "≈ 29 cm"],
            ["R", "Radio de la cúpula: R = (A − 3,3) / 2,53", f"{f1(G.R)} cm"],
            ["Δφ", "Reparto de los 90°: M1–M3 14° · M4 21° · M5 27°", "90°"],
            ["OV", "Solape de teja (borde superior de cada módulo, salvo M1)", f"{f1(G.OV)} cm"]]
    y = table(c, data, 1.6, y, [1.3, 13.6, 2.9], pad=2.4)
    y = para(c, "Los 5 módulos", 1.6, y - 0.4, 17.8, "h3") - 0.1
    rows = [["Módulo", "Posición", "Ángulo", "Medida en plano (cm)", "Hoja"]]
    where = {}
    for n, sh in enumerate(sheets(), 1):
        for k in sh:
            where.setdefault(k.split(".")[0], set()).add(f"H{n}")
    for i, p in enumerate(lames()):
        rows.append([G.IDS[i], MOD_NAMES[i], f"{G.WIDTHS[i]}°", sz(p), " · ".join(sorted(where[G.IDS[i]]))])
    rows.append(["D1 / D2", "disco (dos capas)", "—", f"Ø {f1(G.DISC_D)} / Ø {f1(G.DISC2_D)}", " · ".join(sorted(where["D1"] | where["D2"]))])
    y = table(c, rows, 1.6, y, [2.0, 5.6, 1.8, 4.6, 3.8], pad=2.3)
    y -= 0.35
    box(c, 1.6, y - 3.6, 17.8, 3.6, fill=WARM, stroke=ACC)
    text(c, "Cómo tomar las medidas", 1.95, y - 0.65, 10, "LS-B", ACC)
    bullets(c, ["Con la ropa del traje puesta, mide con cinta flexible desde el pliegue delantero de la axila, por encima del hombro, "
                "hasta el pliegue trasero (<b>A</b>), unos 4 cm por debajo del hueso del hombro.",
                "Mide en el lateral desde lo alto del hombro hasta la mitad del brazo (<b>B</b>). Debería salir R · 1,57.",
                "Si tus medidas cambian más de 1 cm, recalcula R o usa la tabla de tallas (pág. 10)."],
            1.95, y - 1.0, 17.1, "buls", 0.1)
    assert y - 3.6 > 1.7, y


# ================================================================== PÁG 4 · DESPIECE Y MONTAJE
def page_parts(c):
    chrome(c, 4, TOTAL, "DESPIECE", "Los 5 módulos y orden de montaje")
    y = para(c, "Cada hombrera: M1 arriba, junto al cuello, hasta M5 abajo, sobre el brazo, más el disco. Se cortan dos juegos iguales, uno por hombrera.",
             1.6, PH - 2.2, 17.8, "b")
    box(c, 1.6, y - 9.3, 17.8, 9.0)
    yy = y - 0.8
    sc = 0.38
    for i, p in enumerate(lames()):
        q = G.at(p, 0, 0)
        w, h = G.size(q)
        draw_poly(c, q, fill=STEEL[i], stroke=DARK, lw=0.5, ox=2.0, oy=yy - h * sc, sc=sc)
        text(c, f"{G.IDS[i]} · {MOD_NAMES[i]} · {G.WIDTHS[i]}° · {sz(p)} cm", 2.3 + w * sc, yy - h * sc / 2 - 0.1, 7.2, "LS-B", DARK)
        yy -= h * sc + 0.3
    for k, d in enumerate((G.DISC_D, G.DISC2_D)):
        cx, cyy = (17.5, 5.4) if k == 0 else (18.3, 7.85)
        c.setFillColor(colors.HexColor("#9AA3AB") if k == 0 else colors.HexColor("#B4BBC2"))
        c.setStrokeColor(DARK)
        c.circle(cx * CM, (y - cyy) * CM, d / 2 * sc * CM, stroke=1, fill=1)
        text(c, f"D{k + 1}", cx, y - cyy - 0.1, 7, "LS-B", DARK, "c")
        text(c, f"Ø {f1(d)}", cx, y - cyy - 0.45, 6, "LS", DARK, "c")
    y -= 9.7
    y = para(c, "Orden de montaje (de abajo arriba, como tejas)", 1.6, y, 17.8, "h2")
    cards = [("1 · M5", 1, "La banda inferior, formada sobre el balón, es la base."),
             ("2 · M4", 2, "Su borde inferior pisa la franja naranja de M5."),
             ("3 · M3", 3, "Primer gajo; sigue el mismo polo."),
             ("4 · M2 y M1", 5, "Los gajos cierran el cuarto de esfera hacia el cuello."),
             ("5 · Disco", 6, "D1 + D2 sobre el polo tapan las puntas.")]
    cw = 3.4
    for k, (title, upto, desc) in enumerate(cards):
        x = 1.6 + k * (cw + 0.2)
        box(c, x, y - 6.0, cw, 5.8)
        text(c, title, x + 0.2, y - 0.6, 8, "LS-B", HEAD)
        draw_pauldron(c, x + cw / 2 + 0.2, y - 3.3, 0.14, upto=min(upto, 5), hl=("disc" if upto == 6 else 5 - min(upto, 5)), rivets=False,
                      disc=(upto == 6))
        p = Paragraph(desc, ST["xs"])
        _, h = p.wrap((cw - 0.4) * CM, 1000)
        p.drawOn(c, (x + 0.2) * CM, (y - 5.8) * CM)
    y -= 6.4
    y = para(c, "Hojas de goma EVA", 1.6, y, 17.8, "h3") - 0.1
    rows = [["Hoja", "Piezas", "Cortar"]]
    for n, sh in enumerate(sheets(), 1):
        rows.append([f"H{n}", ", ".join(sorted(sh.keys())), "1 hoja de EVA 5 mm"])
    y = table(c, rows, 1.6, y, [1.6, 12.2, 4.0], pad=2.4)
    y = para(c, "El sufijo .1 / .2 indica hombrera izquierda / derecha. Las piezas son iguales; la derecha se monta por la otra cara.", 1.6, y - 0.1, 17.8, "xs")
    assert y > 1.7, y


# ================================================================== HOJAS DE PATRONES
def ruler(c, x0=18.7, y=20.0):
    c.saveState()
    c.setStrokeColor(DARK)
    c.setLineWidth(0.8)
    c.line(x0 * CM, y * CM, (x0 + 10) * CM, y * CM)
    for i in range(11):
        h = 0.3 if i % 5 == 0 else 0.17
        c.line((x0 + i) * CM, y * CM, (x0 + i) * CM, (y + h) * CM)
    c.restoreState()
    text(c, "0", x0, y - 0.3, 6.5, "LS", DARK, "c")
    text(c, "5", x0 + 5, y - 0.3, 6.5, "LS", DARK, "c")
    text(c, "10 cm", x0 + 10, y - 0.3, 6.5, "LS-B", DARK, "c")
    text(c, "Regla de control: debe medir 10,0 cm", x0 - 0.3, y + 0.05, 7, "LS", DARK, "r")


def pattern_page(c, n):
    sh = sheets()[n - 1]
    text(c, f"HOJA H{n} de {len(sheets())} · HOMBRERAS DE ASTRID · " + ", ".join(sorted(sh.keys())), 0.8, 20.05, 9.2, "LS-B", HEAD)
    text(c, "Imprimir al 100 % · A4 horizontal · negro = corte · naranja = borde del módulo superior (solape) · azul = remaches (opcional) · "
            "gris = borde del disco · .1 izquierda / .2 derecha", 0.8, 19.72, 6.3, "LS", GRAYT)
    ruler(c)
    for key, (P, rot) in sh.items():
        name = key.split(".")[0]
        draw_poly(c, P, stroke=DARK, lw=1.4)
        if name.startswith("D"):
            if name == "D1":
                draw_poly(c, Point(P.centroid.x, P.centroid.y).buffer(G.DISC2_D / 2), stroke=GUIDE, lw=0.6, dash=(2, 2))
            text(c, key, P.centroid.x, P.centroid.y - 0.12, 9, "LS-B", HEAD, "c")
            text(c, f"Ø {f1(G.DISC_D if name == 'D1' else G.DISC2_D)}", P.centroid.x, P.centroid.y - 0.55, 6.3, "LS", GRAYT, "c")
            continue
        i = G.IDS.index(name)
        base = G.lame(G.WIDTHS, i, G.TIP_B)
        cen = base.centroid
        rb = affinity.rotate(base, rot, origin=cen) if rot else base
        dx, dy = P.bounds[0] - rb.bounds[0], P.bounds[1] - rb.bounds[1]

        def tf(x, y, rot=rot, cen=cen, dx=dx, dy=dy):
            if rot:
                x, y = 2 * cen.x - x, 2 * cen.y - y
            return (x + dx, y + dy)
        dphi = math.radians(G.WIDTHS[i])
        t0 = G.TIP_B / G.R
        ts = [t0 + (G.T_END - t0) * k / 80 for k in range(81)]
        if i > 0:
            pts = [tf(G.R * t, G.R * math.sin(t) * dphi / 2) for t in ts[4:]]
            ln = LineString(pts).intersection(P.buffer(-0.15))
            for g in getattr(ln, "geoms", [ln]):
                if not g.is_empty:
                    draw_line(c, list(g.coords), ACC, 0.9, (4, 2))
        draw_line(c, [tf(G.R * t0 + 0.6, 0), tf(G.R * G.T_END - 0.6, 0)], colors.HexColor("#B5B5B5"), 0.5, (6, 3, 1, 3))
        for x, y in G.rivets(G.WIDTHS, i, G.TIP_B):
            X, Y = tf(x, y)
            c.saveState()
            c.setStrokeColor(GUIDE)
            c.setLineWidth(0.6)
            c.setDash(1.5, 1.5)
            c.circle(X * CM, Y * CM, 0.42 * CM, stroke=1, fill=0)
            c.restoreState()
        r = G.DISC_D / 2
        ln = LineString([tf(r, -3 + 6 * k / 10) for k in range(11)]).intersection(P)
        if not ln.is_empty:
            for g in getattr(ln, "geoms", [ln]):
                draw_line(c, list(g.coords), colors.HexColor("#7A7A7A"), 0.7, (2, 2))
        mx, my = tf(G.R * 1.05, 0)
        text(c, key, mx, my - 0.13, 10, "LS-B", HEAD, "c")
        ex, ey = tf(G.R * 1.75, 0)
        text(c, f"{MOD_NAMES[i]} · {sz(base)} cm", ex, ey - 0.1, 6.5, "LS", DARK, "c")
        tx, ty = tf(G.R * t0 + 2.4, 0)
        text(c, "polo", tx, ty - 0.1, 6.3, "LS-I", GRAYT, "c")
        bx, by = tf(G.R * G.T_END - 1.2, 0)
        text(c, "atrás", bx, by - 0.1, 6.3, "LS-I", GRAYT, "c")


def page_h1(c):
    pattern_page(c, 1)


def page_h2(c):
    pattern_page(c, 2)


def page_h3(c):
    pattern_page(c, 3)


def _fig_box(c, x, yt, title, w=5.7, h=4.3):
    box(c, x, yt - h, w, h, fill=colors.white)
    text(c, title, x + w / 2, yt - 0.5, 8, "LS-B", HEAD, "c")


def fig_ball(c, x, yt):
    _fig_box(c, x, yt, "Formado sobre un balón")
    cx, cy, r = x + 2.85, yt - 2.4, 1.2
    c.saveState()
    c.setFillColor(colors.HexColor("#E3A35C"))
    c.setStrokeColor(colors.HexColor("#9C5D1E"))
    c.circle(cx * CM, cy * CM, r * CM, stroke=1, fill=1)
    c.restoreState()
    for k, col in enumerate(STEEL[:3]):
        rr = r + 0.12 + 0.08 * k
        a0, a1 = math.radians(25 + k * 22), math.radians(105 + k * 18)
        pts = [(cx + rr * math.cos(a0 + (a1 - a0) * j / 20), cy + rr * math.sin(a0 + (a1 - a0) * j / 20)) for j in range(21)]
        c.saveState()
        c.setStrokeColor(col)
        c.setLineWidth(4.5)
        p = c.beginPath()
        p.moveTo(pts[0][0] * CM, pts[0][1] * CM)
        for X, Y in pts[1:]:
            p.lineTo(X * CM, Y * CM)
        c.drawPath(p, stroke=1, fill=0)
        c.restoreState()
    text(c, "balón (R ≈ 10 cm) cubierto con film", x + 2.85, yt - 3.95, 6.6, "LS", DARK, "c")


def fig_shingle(c, x, yt):
    _fig_box(c, x, yt, "Solape de teja (corte)")
    y0 = yt - 2.8
    for k in range(3):
        xa = x + 0.6 + k * 0.9
        path_pts(c, [(xa, y0 + k * 0.42), (xa + 2.4, y0 + k * 0.42 + 0.85), (xa + 2.4, y0 + k * 0.42 + 1.25), (xa, y0 + k * 0.42 + 0.4)],
                 STEEL[k], STEEL_EDGE, 0.6)
    c.saveState()
    c.setStrokeColor(ACC)
    c.setLineWidth(0.8)
    c.line((x + 1.5) * CM, (y0 - 0.3) * CM, (x + 2.4) * CM, (y0 - 0.3) * CM)
    c.restoreState()
    text(c, "solape 1 cm", x + 1.95, y0 - 0.65, 6.6, "LS-B", ACC, "c")
    text(c, "la de arriba pisa a la de abajo", x + 2.85, yt - 3.95, 6.6, "LS", DARK, "c")


def fig_tips(c, x, yt):
    _fig_box(c, x, yt, "Puntas bajo el disco (B)")
    cx, cy = x + 1.5, yt - 2.35
    for k, ang in enumerate((-24, -12, 0, 12, 24)):
        a = math.radians(ang)
        path_pts(c, [(cx + 0.6 * math.cos(a - 0.08), cy + 0.6 * math.sin(a - 0.08)), (cx + 3.6 * math.cos(a - 0.05), cy + 3.6 * math.sin(a - 0.05)),
                     (cx + 3.6 * math.cos(a + 0.05), cy + 3.6 * math.sin(a + 0.05)), (cx + 0.6 * math.cos(a + 0.08), cy + 0.6 * math.sin(a + 0.08))],
                 STEEL[k], STEEL_EDGE, 0.5)
    c.saveState()
    c.setFillColor(colors.HexColor("#7D868E"))
    c.setStrokeColor(STEEL_EDGE)
    c.circle(cx * CM, cy * CM, 0.95 * CM, stroke=1, fill=1)
    c.setFillColor(colors.HexColor("#959EA6"))
    c.circle(cx * CM, cy * CM, 0.72 * CM, stroke=1, fill=1)
    c.restoreState()
    text(c, "el disco tapa la convergencia", x + 2.85, yt - 3.95, 6.6, "LS", DARK, "c")



# ================================================================== TUTORIAL
def page_tut1(c):
    chrome(c, 8, TOTAL, "TUTORIAL 1/2", "Corte, termoformado y montaje en tejas")
    steps = [
        (1, "Imprime y comprueba", "Imprime las hojas H1–H3 al 100 % (tamaño real) en horizontal y mide la regla de 10 cm. Recorta las plantillas por la línea gruesa."),
        (2, "Prueba en papel", "Antes de cortar goma, pega con cinta los 5 módulos de papel de una hombrera sobre el hombro, con la ropa del traje puesta y siguiendo los solapes. "
            "El polo va delante del hombro, M1 sobre lo alto y M5 rodeando el brazo. Si queda pequeña o grande, recalcula R (pág. 10)."),
        (3, "Traza y corta", "Traza cada hoja de patrón sobre una hoja de EVA y corta con cuchilla nueva a 90°, en varias pasadas. Marca con punzón la línea de solape y los remaches."),
        (4, "Bisela", "Rebaja un poco a 45° el canto inferior de cada módulo, que es el que se ve, y lija las puntas que van bajo el disco para que no abulten."),
        (5, "Termoforma en cúpula", "Calienta cada módulo con la pistola, siempre en movimiento, y estíralo sobre un <b>balón</b> de radio parecido a R "
            f"(R = {f1(G.R)} cm: un balón de fútbol talla 5 o uno de baloncesto pequeño) cubierto con film. Presiona con las palmas y deja enfriar 1–2 min."),
        (6, "Monta en tejas, de abajo arriba", "Con M5 sobre el balón, pega M4 de modo que su canto inferior pise la franja naranja de M5. Sigue con M3, M2 y M1. "
            "Todas las puntas apuntan al mismo polo. M5 marca la curva del borde: no la deformes al pegar las demás."),
        (7, "Disco", "Pega D2 centrado sobre D1, forma el conjunto un poco abombado y pégalo sobre el polo, tapando las puntas."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    for k, fn in enumerate((fig_ball, fig_shingle, fig_tips)):
        fn(c, 1.6 + k * 6.05, y - 0.05)
    y -= 4.75
    box(c, 1.6, y - 3.9, 17.8, 3.9, fill=WARM, stroke=ACC)
    text(c, "Consejos de profesional", 1.95, y - 0.65, 10, "LS-B", ACC)
    bullets(c, ["Forma <b>todos</b> los módulos antes de pegar: un módulo pegado en plano tira del solape y se despega.",
                "Marca el polo en el balón con cinta y alinea el eje (punto-raya) de cada módulo hacia esa marca.",
                "Si el conjunto se abre al enfriar, recalienta la hombrera ya pegada sobre el balón y sujétala con cinta.",
                "Monta la derecha como espejo de la izquierda: polo delante, M5 sobre el brazo."],
            1.95, y - 1.0, 17.1, "buls", 0.08)
    assert y - 3.9 > 1.7, y


def page_tut2(c):
    chrome(c, 9, TOTAL, "TUTORIAL 2/2", "Detalles, acabado metálico y sujeción")
    steps = [
        (8, "Textura martillada", "Haz una bola de papel de aluminio bien apretada, calienta la superficie 5 s y presiona la bola dando golpecitos. "
            "Para un martillado más marcado, usa una punta de bola en el pirograbador."),
        (9, "Arañazos y golpes", "Haz cortes cortos y en diagonal con el cúter (1–2 mm) y ábrelos con un golpe de calor. Pocos y bien colocados, como en la ref. 3."),
        (10, "Remaches (fuera de la ficha)", "Van en las marcas azules: medias bolas de madera o plástico, foam clay o tachuelas de tapicería."),
        (11, "Sella", "Lija suave y da 3 capas finas de Plasti Dip o de vinílica/PVA diluida 1:1, dejando secar 20–30 min entre capas."),
        (12, "Pinta de metal", "Imprimación negra y plata en <b>dry brush</b> (pincel casi seco) sobre las zonas altas. Lavado de negro diluido en la textura y las juntas, "
             "y barniz mate o satinado."),
        (13, "Sujeción (fuera de la ficha)", "Correa o elástico por dentro del borde de M5 que abrace el brazo (medida C), y otra correa o broche hacia el peto. "
             "Refuerza por dentro con una tira de EVA donde se fijen las correas."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = table(c, [["Zona", "Color", "Código", "Técnica"],
                  ["Base de todas las piezas", "Negro mate", "#1E1E1E", "Imprimación en spray"],
                  ["Módulos y disco", "Plata / acero", "#A9B0B6", "Dry brush en capas, de menos a más"],
                  ["Remaches y cantos", "Plata clara", "#D3D8DC", "Toques en los puntos de luz"],
                  ["Huecos y juntas", "Lavado negro-marrón", "#2B2620", "Diluido 1:4, retirar el exceso"]],
              1.6, y - 0.1, [5.2, 3.6, 2.3, 6.7], pad=2.2)
    y = para(c, "Problemas frecuentes", 1.6, y - 0.4, 17.8, "h3") - 0.1
    y = table(c, [["Problema", "Causa", "Solución"],
                  ["Los módulos no convergen en un punto", "Pegados sin seguir el eje", "Marca el polo en el balón y alinea cada eje hacia él"],
                  ["El borde inferior pierde la curva", "M5 deformado al montar", "Recalienta M5 sobre el balón antes de pegar M4"],
                  ["Queda corta por detrás", "R pequeño para tu hombro", "Aumenta R con la fórmula y reimprime a escala"],
                  ["Se ven huecos entre módulos", "Solape sin pegar en los extremos", "Pega toda la franja naranja"]],
              1.6, y, [5.6, 5.0, 7.2], pad=2.1)
    y = checklist(c, 1.6, y - 0.35, 17.8, "Lista de control final",
                  ["2 juegos de 5 módulos + 2 discos", "Todos formados sobre el balón", "Solapes pegados en toda su franja",
                   "Curva de M5 intacta", "Textura, sellado y pintura", "Correas y remaches añadidos"], cols=2)
    assert y > 1.7, y


# ================================================================== PÁG 10 · TALLAS
def page_sizes(c):
    chrome(c, 10, TOTAL, "ANEXO", "Adaptar a otra talla")
    y = para(c, "Todas las piezas escalan con el radio de la cúpula. Mide <b>A</b> (arco del hombro de delante a atrás) y calcula "
                f"<b>R = (A − 3,3) / 2,53</b>. El factor de escala es <b>R / {f1(G.R)}</b>: imprime las hojas con ese porcentaje "
                "y comprueba la regla, que medirá 10 cm × el factor.", 1.6, PH - 2.2, 17.8, "b")
    rows = [["Talla orientativa", "A (cm)", "R (cm)", "Escala de impresión", "Módulo mayor (cm)", "Hojas de EVA (par)"]]
    big = lames()[4]
    w, h = G.size(big)
    for name, r in (("Mujer 1,55–1,60", 9.4), (f"Mujer 1,65 (esta ficha)", G.R), ("Mujer 1,72 / hombre delgado", 11.0), ("Hombre 1,75 normal", 11.5)):
        k = r / G.R
        fits = (w * k) <= 28.3
        rows.append([name, f1(r * G.T_END + 3.3), f1(r), f"{round(k * 100)} %", f"{f1(w * k)} × {f1(h * k)}",
                     str(len(sheets(round(k, 3)))) if fits else "No cabe: divide M5"])
    y = table(c, rows, 1.6, y - 0.3, [4.6, 1.8, 1.6, 2.9, 3.2, 3.7], pad=2.6)
    y = para(c, "Al escalar, el reparto en hojas cambia: con escala mayor de 100 % imprime las hojas a esa escala y, si alguna pieza no cabe, "
                "imprime esa hoja dos veces y reparte las piezas.", 1.6, y - 0.25, 17.8, "s")
    y -= 0.4
    y = para(c, "Ajustes finos", 1.6, y, 17.8, "h2")
    y = bullets(c, ["<b>Más larga por detrás:</b> sube el corte trasero de 145° a 155° (todos los módulos crecen por ese extremo).",
                    "<b>Más alta o más baja en el lateral:</b> cambia el radio R, no el reparto de 90°: así se conserva la forma de cuarto de esfera.",
                    "<b>Más ceñida:</b> reduce R 0,5–1 cm y forma sobre un balón algo más pequeño."], 1.6, y, 17.8, "bul", 0.1)
    y -= 0.3
    y = para(c, "Fórmula de cada módulo (para dibujarlo a mano)", 1.6, y, 17.8, "h2")
    y = para(c, "Dibuja un eje recto. Desde el polo, a cada distancia x = R · t del eje, marca a cada lado medio ancho <b>R · sen(t) · Δφ / 2</b> "
                "(Δφ en radianes) y suma 1 cm al lado superior (salvo en M1). Une los puntos con una curva suave y corta el extremo trasero en recto a "
                f"x = R · {f1(G.T_END)}.", 1.6, y - 0.1, 17.8, "b")
    degs = (15, 30, 60, 90, 120, 145)
    rows = [["t (grados)"] + [str(d) for d in degs],
            ["x desde el polo (cm)"] + [f1(G.R * math.radians(d)) for d in degs],
            ["Medio ancho, módulo de 14° (cm)"] + [f1(G.R * math.sin(math.radians(d)) * math.radians(14) / 2) for d in degs],
            ["Medio ancho, módulo de 21° (cm)"] + [f1(G.R * math.sin(math.radians(d)) * math.radians(21) / 2) for d in degs],
            ["Medio ancho, módulo de 27° (cm)"] + [f1(G.R * math.sin(math.radians(d)) * math.radians(27) / 2) for d in degs]]
    y = table(c, rows, 1.6, y - 0.25, [5.0] + [2.13] * 6, pad=2.4, zebra=False)
    assert y > 1.7, y


# ================================================================== main
def build():
    c = canvas.Canvas(OUT, pagesize=(PW * CM, PH * CM))
    c.setTitle("Hombreras de Astrid · Ficha técnica, patrones EVA 5 mm y tutorial")
    for fn in (page_ficha, page_analysis, page_geometry, page_parts):
        c.setPageSize((PW * CM, PH * CM))
        fn(c)
        c.showPage()
    for fn in (page_h1, page_h2, page_h3):
        c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
        fn(c)
        c.showPage()
    for fn in (page_tut1, page_tut2, page_sizes):
        c.setPageSize((PW * CM, PH * CM))
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT)
    c = canvas.Canvas(OUT_PRINT, pagesize=(G.PAGE_W * CM, G.PAGE_H * CM))
    c.setTitle("Hombreras de Astrid · Guías A4 para imprimir (escala 1:1)")
    for fn in (page_h1, page_h2, page_h3):
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT_PRINT)


if __name__ == "__main__":
    build()
