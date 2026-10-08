"""Utilidades de dibujo (reportlab) para el PDF de las braceras de Hipo."""
import math

import geom as G
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph, Table, TableStyle
from shapely.geometry import Polygon, MultiPolygon

# ------------------------------------------------------------------ fuentes y colores
FD = "/usr/share/fonts/truetype/liberation/"
pdfmetrics.registerFont(TTFont("LS", FD + "LiberationSans-Regular.ttf"))
pdfmetrics.registerFont(TTFont("LS-B", FD + "LiberationSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("LS-I", FD + "LiberationSans-Italic.ttf"))
pdfmetrics.registerFont(TTFont("LS-BI", FD + "LiberationSans-BoldItalic.ttf"))
pdfmetrics.registerFontFamily("LS", normal="LS", bold="LS-B", italic="LS-I", boldItalic="LS-BI")

CM = 28.3465
BROWN = colors.HexColor("#7B4A2A")
LEATHER = colors.HexColor("#A9744A")
DARK = colors.HexColor("#2B2B2B")
ACCENT = colors.HexColor("#C8782E")
CREAM = colors.HexColor("#F6EFE6")
LINE = colors.HexColor("#D9CBB8")
GRAYT = colors.HexColor("#6B6B6B")
GUIDE = colors.HexColor("#3A78B5")
STEEL = colors.HexColor("#B8B8BC")
WARM = colors.HexColor("#FFF6E8")

COL = dict(base=colors.HexColor("#3A3836"), base_edge=colors.HexColor("#1C1B1A"), panel=colors.HexColor("#8A5530"),
           cuff=colors.HexColor("#2A2928"), strap=colors.HexColor("#1A1A1A"), guard=colors.HexColor("#8F5A33"),
           guard_base=colors.HexColor("#3A3836"), sheath=colors.HexColor("#4A4A4D"), grip=colors.HexColor("#5A3820"),
           metal=STEEL, skin=colors.HexColor("#EBCBA8"), skin_edge=colors.HexColor("#B88B62"))

PW, PH = 21.0, 29.7   # retrato (cm)


def f1(v):
    return f"{v:.1f}".replace(".", ",")


def f2(v):
    return f"{v:.2f}".replace(".", ",")


# ------------------------------------------------------------------ estilos de texto
ST = {
    "b": ParagraphStyle("b", fontName="LS", fontSize=9.4, leading=13, textColor=DARK),
    "s": ParagraphStyle("s", fontName="LS", fontSize=8.3, leading=11, textColor=DARK),
    "xs": ParagraphStyle("xs", fontName="LS", fontSize=7.3, leading=9.4, textColor=GRAYT),
    "h2": ParagraphStyle("h2", fontName="LS-B", fontSize=12.5, leading=16, textColor=BROWN),
    "h3": ParagraphStyle("h3", fontName="LS-B", fontSize=10, leading=13, textColor=BROWN),
    "cell": ParagraphStyle("cell", fontName="LS", fontSize=8, leading=10.2, textColor=DARK),
    "cellb": ParagraphStyle("cellb", fontName="LS-B", fontSize=8, leading=10.2, textColor=DARK),
    "th": ParagraphStyle("th", fontName="LS-B", fontSize=8, leading=10, textColor=colors.white),
    "bul": ParagraphStyle("bul", fontName="LS", fontSize=9.2, leading=12.6, textColor=DARK, leftIndent=11, bulletIndent=1),
    "buls": ParagraphStyle("buls", fontName="LS", fontSize=8.3, leading=11, textColor=DARK, leftIndent=10, bulletIndent=1),
    "step": ParagraphStyle("step", fontName="LS", fontSize=9.6, leading=13.6, textColor=DARK),
}


def para(c, txt, x, ytop, w, style="b"):
    p = Paragraph(txt, ST[style] if isinstance(style, str) else style)
    _, h = p.wrap(w * CM, 2000)
    p.drawOn(c, x * CM, ytop * CM - h)
    return ytop - h / CM


def bullets(c, items, x, ytop, w, style="bul", gap=0.12, mark="•"):
    y = ytop
    for it in items:
        p = Paragraph(it, ST[style], bulletText=mark)
        _, h = p.wrap(w * CM, 2000)
        p.drawOn(c, x * CM, y * CM - h)
        y -= h / CM + gap
    return y


def table(c, data, x, ytop, colw, header=True, zebra=True, pad=3, first_bold=True):
    rows = []
    for i, r in enumerate(data):
        row = []
        for j, cell in enumerate(r):
            if isinstance(cell, str):
                st = "th" if (header and i == 0) else ("cellb" if (j == 0 and first_bold) else "cell")
                row.append(Paragraph(cell, ST[st]))
            else:
                row.append(cell)
        rows.append(row)
    t = Table(rows, colWidths=[w * CM for w in colw])
    style = [("VALIGN", (0, 0), (-1, -1), "TOP"), ("GRID", (0, 0), (-1, -1), 0.4, LINE),
             ("TOPPADDING", (0, 0), (-1, -1), pad), ("BOTTOMPADDING", (0, 0), (-1, -1), pad),
             ("LEFTPADDING", (0, 0), (-1, -1), 4), ("RIGHTPADDING", (0, 0), (-1, -1), 4)]
    if header:
        style.append(("BACKGROUND", (0, 0), (-1, 0), BROWN))
    if zebra:
        for i in range(1 if header else 0, len(rows)):
            if i % 2 == 0:
                style.append(("BACKGROUND", (0, i), (-1, i), CREAM))
    t.setStyle(TableStyle(style))
    _, h = t.wrap(sum(colw) * CM, 2000)
    t.drawOn(c, x * CM, ytop * CM - h)
    return ytop - h / CM


def box(c, x, y, w, h, fill=CREAM, stroke=LINE, r=0.2, lw=0.6):
    c.saveState()
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(lw)
    c.roundRect(x * CM, y * CM, w * CM, h * CM, r * CM, stroke=1, fill=1)
    c.restoreState()


def text(c, s, x, y, size=8, font="LS", color=DARK, align="l", rot=0):
    c.saveState()
    c.setFillColor(color)
    c.setFont(font, size)
    c.translate(x * CM, y * CM)
    if rot:
        c.rotate(rot)
    {"c": c.drawCentredString, "r": c.drawRightString}.get(align, c.drawString)(0, 0, s)
    c.restoreState()


def chrome(c, n, total, part, title):
    c.setFillColor(BROWN)
    c.rect(0, (PH - 1.35) * CM, PW * CM, 1.35 * CM, stroke=0, fill=1)
    c.setFillColor(ACCENT)
    c.rect(0, (PH - 1.45) * CM, PW * CM, 0.1 * CM, stroke=0, fill=1)
    text(c, title, 1.6, PH - 0.88, 13, "LS-B", colors.white)
    text(c, part, PW - 1.6, PH - 0.88, 8.5, "LS", colors.HexColor("#F0DCC4"), "r")
    c.setStrokeColor(LINE)
    c.setLineWidth(0.5)
    c.line(1.6 * CM, 1.35 * CM, (PW - 1.6) * CM, 1.35 * CM)
    text(c, "Braceras de Hipo · versión completa · goma EVA 5 mm · talla de referencia 1,75 m", 1.6, 0.85, 7.5, "LS", GRAYT)
    text(c, f"{n} / {total}", PW - 1.6, 0.85, 7.5, "LS-B", GRAYT, "r")


# ------------------------------------------------------------------ geometría → PDF
def _rings(geom_):
    if isinstance(geom_, MultiPolygon):
        for g in geom_.geoms:
            yield from _rings(g)
    elif isinstance(geom_, Polygon):
        yield list(geom_.exterior.coords)
        for i in geom_.interiors:
            yield list(i.coords)


def draw_poly(c, poly, fill=None, stroke=DARK, lw=1.0, dash=None, ox=0.0, oy=0.0, sc=1.0):
    p = c.beginPath()
    for ring in _rings(poly):
        p.moveTo((ring[0][0] * sc + ox) * CM, (ring[0][1] * sc + oy) * CM)
        for x, y in ring[1:]:
            p.lineTo((x * sc + ox) * CM, (y * sc + oy) * CM)
        p.close()
    c.saveState()
    c.setLineWidth(lw)
    c.setLineJoin(1)
    if dash:
        c.setDash(list(dash))
    if fill is not None:
        c.setFillColor(fill)
    if stroke is not None:
        c.setStrokeColor(stroke)
    c.drawPath(p, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0, fillMode=0)
    c.restoreState()


def draw_line(c, pts, color=GUIDE, lw=0.8, dash=(3, 2), ox=0.0, oy=0.0):
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(lw)
    if dash:
        c.setDash(list(dash))
    p = c.beginPath()
    p.moveTo((pts[0][0] + ox) * CM, (pts[0][1] + oy) * CM)
    for x, y in pts[1:]:
        p.lineTo((x + ox) * CM, (y + oy) * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.restoreState()


def path_pts(c, pts, fill, stroke=DARK, lw=0.6, close=True, alpha=1.0, dash=None):
    p = c.beginPath()
    for i, (X, Y) in enumerate(pts):
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    if close:
        p.close()
    c.saveState()
    if alpha < 1:
        c.setFillAlpha(alpha)
    if dash:
        c.setDash(list(dash))
    c.setLineJoin(1)
    if fill is not None:
        c.setFillColor(fill)
    if stroke is not None:
        c.setStrokeColor(stroke)
        c.setLineWidth(lw)
    c.drawPath(p, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0)
    c.restoreState()


# ================================================================== vista frontal (proyección del cono)
LAYERS = ["base", "cuff", "panel", "straps", "dagger", "guard"]


def rw(y, off=0.0):
    """Radio real (vista) de la superficie a la altura y."""
    return G.circ_at(y, off) / (2 * math.pi)


def view_of_dev(x, y, off):
    r = math.hypot(x, y)
    a = math.atan2(x, y)
    yw = r - off * 2 * math.pi / G.THETA - G.R0 + G.BASE_Y0
    psi = 2 * math.pi * a / G.THETA
    rr = r * G.THETA / (2 * math.pi)
    return rr * math.sin(psi), yw


def view_s(s, y, off):
    """Punto de vista para un arco s (cm) desde el eje, a la altura y."""
    r = rw(y, off)
    return r * math.sin(s / r), y


def draw_bracer(c, ox, oy, sc, upto=6, hl=None, ghost=False, variant="A", dagger=True, labels=None):
    """Vista exterior del brazal izquierdo. (ox, oy) = punto del eje a la altura y = 0 (pliegue de la muñeca)."""
    def P(x, y):
        return (ox + x * sc, oy + y * sc)

    def shape(pts, fill, layer, stroke=DARK, lw=0.5):
        h = (hl == layer)
        path_pts(c, [P(x, y) for x, y in pts], fill, ACCENT if h else stroke, 2.0 if h else lw)

    def on(layer):
        return LAYERS.index(layer) < upto

    # 6 · guarda (debajo del puño)
    if on("guard"):
        if variant == "A":
            shape([(x * 0.93, y) for x, y in G.guard_plate().exterior.coords], COL["guard"], "guard")
            inner = G.guard_plate().buffer(-0.6)
            path_pts(c, [P(x * 0.93, y) for x, y in inner.exterior.coords], None, colors.HexColor("#C79A6B"), 0.5, dash=(1.5, 1.5))
        else:
            shape([(x * 0.93, y) for x, y in G.guard_seg_base().exterior.coords], COL["guard_base"], "guard")
            for i in range(4):
                shape([(x * 0.93, y) for x, y in G.guard_strip(i).exterior.coords], COL["guard"], "guard")
            shape([(x * 0.93, y) for x, y in G.guard_band().exterior.coords], COL["guard"], "guard")
    # 1 · base
    if on("base"):
        n = 60
        left = [(-rw(y, 0) - 0.25, y) for y in [G.BASE_Y0 + (G.BASE_Y1 - G.BASE_Y0) * i / n for i in range(n + 1)]]
        top = []
        rt = rw(G.BASE_Y1, 0)
        for i in range(n + 1):
            psi = -math.pi / 2 + math.pi * i / n
            s = psi * rt
            top.append(((rt + 0.25) * math.sin(psi), G.BASE_Y1 + G.peak(s)))
        right = [(-x, y) for x, y in reversed(left)]
        shape(left + top + right, COL["base"], "base", stroke=COL["base_edge"])
    # 3 · panel
    if on("panel"):
        shape([view_of_dev(x, y, G.T) for x, y in G.panel_outline().exterior.coords], COL["panel"], "panel")
        st = G.panel_outline().buffer(-0.45)
        path_pts(c, [P(*view_of_dev(x, y, G.T)) for x, y in st.exterior.coords], None, colors.HexColor("#C79A6B"), 0.45, dash=(1.2, 1.2))
        for sl in G.slots():
            path_pts(c, [P(*view_of_dev(x, y, G.T)) for x, y in sl.exterior.coords], colors.HexColor("#141414"), None)
    # 4 · cinchas laterales
    phi = G.panel_angle()
    if on("straps"):
        for y in G.STRAP_Y:
            half = G.R_at(y, G.T) * phi / 2
            xl = view_s(-half, y, G.T)[0]
            xr = view_s(half, y, G.T)[0]
            r = rw(y, G.T) + 0.3
            w = G.STRAP_W / 2
            shape([(-r, y - w), (xl, y - w), (xl, y + w), (-r, y + w)], COL["strap"], "straps", stroke=colors.HexColor("#555555"))
            shape([(xr, y - w), (r + 0.9, y - w), (r + 1.6, y), (r + 0.9, y + w), (xr, y + w)], COL["strap"], "straps",
                  stroke=colors.HexColor("#555555"))

    def central(y):
        half = G.R_at(y, G.T) * phi / 2 - G.SLOT_IN
        xa = view_s(-half, y, 2 * G.T)[0]
        xb = view_s(half, y, 2 * G.T)[0]
        w = G.STRAP_W / 2
        shape([(xa, y - w), (xb, y - w), (xb, y + w), (xa, y + w)], COL["strap"], "straps", stroke=colors.HexColor("#555555"))

    if on("straps"):
        central(G.STRAP_Y[2])
    # 5 · daga
    if on("dagger") and dagger:
        parts = G.dagger_parts()

        def dshape(poly, fill):
            pts = [view_s(G.DAGGER_X + x, G.DAGGER_Y0 + y, 3 * G.T) for x, y in poly.exterior.coords]
            shape(pts, fill, "dagger")
        dshape(parts["sheath"], COL["sheath"])
        cv = affinity_translate(G.sheath_cover().buffer(-0.12), 0, 1.3 + 4.4 + 0.8)
        path_pts(c, [P(*view_s(G.DAGGER_X + x, G.DAGGER_Y0 + y, 3 * G.T)) for x, y in cv.exterior.coords], None,
                 colors.HexColor("#8A8A90"), 0.4, dash=(1.2, 1.2))
        dshape(parts["grip"], COL["grip"])
        dshape(parts["guard"], COL["metal"])
        dshape(parts["pommel"], COL["metal"])
    if on("straps"):
        central(G.STRAP_Y[0])
        central(G.STRAP_Y[1])
    # 2 · puño (encima de base y guarda)
    if on("cuff"):
        n = 20
        left = [(-rw(y, G.T) - 0.3, y) for y in [G.CUFF_Y0 + (G.CUFF_Y1 - G.CUFF_Y0) * i / n for i in range(n + 1)]]
        right = [(-x, y) for x, y in reversed(left)]
        shape(left + right, COL["cuff"], "cuff", stroke=colors.HexColor("#111111"))
    if ghost:
        c.saveState()
        c.setStrokeColor(GRAYT)
        c.setDash(1.5, 1.5)
        c.setLineWidth(0.7)
        for y in G.STRAP_Y:   # hebillas (lado anterior) y anillas (puntas)
            r = rw(y, G.T) + 0.3
            X, Y = P(-r - 0.25, y - 0.55)
            c.rect(X * CM, Y * CM, 0.5 * sc * CM, 1.1 * sc * CM, stroke=1, fill=0)
        X, Y = P(1.6, G.BASE_Y1 + G.PEAK_H - 0.9)
        c.circle(X * CM, Y * CM, 0.25 * sc * CM, stroke=1, fill=0)
        for x, y in ((-2.2, -8.3), (2.2, -8.3), (0, -9.4)):
            X, Y = P(x, y)
            c.circle(X * CM, Y * CM, 0.22 * sc * CM, stroke=1, fill=0)
        c.restoreState()
    return P


def affinity_translate(p, dx, dy):
    from shapely import affinity
    return affinity.translate(p, dx, dy)


def callout(c, n, p_from, p_to, r=0.3, color=ACCENT):
    c.saveState()
    c.setStrokeColor(color)
    c.setLineWidth(0.8)
    c.line(p_from[0] * CM, p_from[1] * CM, p_to[0] * CM, p_to[1] * CM)
    c.setFillColor(color)
    c.circle(p_to[0] * CM, p_to[1] * CM, r * CM, stroke=0, fill=1)
    c.circle(p_from[0] * CM, p_from[1] * CM, 0.07 * CM, stroke=0, fill=1)
    c.setFillColor(colors.white)
    c.setFont("LS-B", 9 if len(str(n)) < 2 else 7.5)
    c.drawCentredString(p_to[0] * CM, p_to[1] * CM - 3.1, str(n))
    c.restoreState()


# ================================================================== antebrazo con medidas
def draw_arm(c, ox, oy, sc):
    def P(x, y):
        return (ox + x * sc, oy + y * sc)

    def hw(y):
        return G.skin(y) / math.pi / 2 * 1.04 + 0.05

    def poly(pts, fill, stroke=COL["skin_edge"], lw=0.8, alpha=1.0):
        path_pts(c, [P(x, y) for x, y in pts], fill, stroke, lw, alpha=alpha)

    ys = [25.5 * i / 30 for i in range(31)]
    poly([(-hw(y), y) for y in ys] + [(hw(y), y) for y in reversed(ys)], COL["skin"])
    poly([(-2.9, 0), (2.9, 0), (4.3, -10), (-4.3, -10)], COL["skin"])
    for i in range(4):
        x0 = -4.3 + i * 2.15
        ln = [7.6, 8.6, 8.2, 6.6][i]
        poly([(x0 + 0.05, -10), (x0 + 2.1, -10), (x0 + 2.0, -10 - ln), (x0 + 0.15, -10 - ln)], COL["skin"])
    # piezas translúcidas
    yb = [G.BASE_Y0 + (G.BASE_Y1 - G.BASE_Y0) * i / 20 for i in range(21)]
    top = [(x, G.BASE_Y1 + G.peak(x * 1.25)) for x in [-hw(G.BASE_Y1) - 0.4 + (2 * hw(G.BASE_Y1) + 0.8) * i / 30 for i in range(31)]]
    poly([(-hw(y) - 0.4, y) for y in yb] + top + [(hw(y) + 0.4, y) for y in reversed(yb)], COL["base"], DARK, 0.6, 0.5)
    poly([(-hw(G.CUFF_Y0) - 0.6, G.CUFF_Y0), (hw(G.CUFF_Y0) + 0.6, G.CUFF_Y0), (hw(G.CUFF_Y1) + 0.6, G.CUFF_Y1),
          (-hw(G.CUFF_Y1) - 0.6, G.CUFF_Y1)], COL["cuff"], DARK, 0.6, 0.65)
    poly([(-2.7, G.PANEL_Y0), (2.7, G.PANEL_Y0), (3.1, G.PANEL_Y1), (-3.1, G.PANEL_Y1)], COL["panel"], DARK, 0.6, 0.6)
    poly([(x * 0.93, y) for x, y in G.guard_plate().exterior.coords], COL["guard"], DARK, 0.6, 0.55)

    def dim_h(y, x0, x1, label, extra=0.0):
        X0, Y = P(x0, y)
        X1, _ = P(x1, y)
        c.saveState()
        c.setStrokeColor(ACCENT)
        c.setLineWidth(0.8)
        c.line(X0 * CM, Y * CM, X1 * CM, Y * CM)
        for X in (X0, X1):
            c.line(X * CM, (Y - 0.15) * CM, X * CM, (Y + 0.15) * CM)
        c.restoreState()
        text(c, label, X1 + 0.2 + extra * sc, Y - 0.1, 7.5, "LS-B", ACCENT)

    def dim_v(x, y0, y1, label):
        X, Y0 = P(x, y0)
        _, Y1 = P(x, y1)
        c.saveState()
        c.setStrokeColor(ACCENT)
        c.setLineWidth(0.8)
        c.line(X * CM, Y0 * CM, X * CM, Y1 * CM)
        for Y in (Y0, Y1):
            c.line((X - 0.15) * CM, Y * CM, (X + 0.15) * CM, Y * CM)
        c.restoreState()
        text(c, label, X - 0.2, (Y0 + Y1) / 2, 7.3, "LS-B", ACCENT, "c", 90)

    dim_h(G.BASE_Y1, -hw(G.BASE_Y1) - 0.4, hw(G.BASE_Y1) + 0.4, f"M2 · {f1(G.skin(G.BASE_Y1))}")
    dim_h(G.BASE_Y0, -hw(G.BASE_Y0) - 0.4, hw(G.BASE_Y0) + 0.4, f"M3 · {f1(G.skin(G.BASE_Y0))}", extra=0.6)
    dim_h(0.0, -hw(0) - 0.2, hw(0) + 0.2, "M4 · 17,0", extra=1.8)
    dim_h(-10.0, -4.3, 4.3, "M8 · 8,6", extra=0.2)
    dim_v(-hw(G.BASE_Y1) - 1.1, G.BASE_Y0, G.BASE_Y1, f"M5 · {f1(G.H)} (base)")
    dim_v(-5.2, 0, -10, "M7 · 10,0")
    text(c, "codo ↑", ox, oy + 26.2 * sc, 7.5, "LS-I", GRAYT, "c")
    text(c, f"pico +{f1(G.PEAK_H)}", ox + 3.4 * sc, oy + (G.BASE_Y1 + G.PEAK_H + 0.4) * sc, 7, "LS-BI", GRAYT, "l")


# ================================================================== sección transversal
def draw_section(c, cx, cy, sc):
    """Corte transversal por la fila central de cinchas (vista desde la muñeca)."""
    y = G.STRAP_Y[1]
    r_in = (G.skin(y) + G.EASE) / (2 * math.pi)
    t = G.T

    def arc_band(r0, r1, a0, a1, fill, stroke=DARK, n=60):
        pts = [(cx + r1 * sc * math.sin(a0 + (a1 - a0) * i / n), cy + r1 * sc * math.cos(a0 + (a1 - a0) * i / n)) for i in range(n + 1)]
        pts += [(cx + r0 * sc * math.sin(a1 - (a1 - a0) * i / n), cy + r0 * sc * math.cos(a1 - (a1 - a0) * i / n)) for i in range(n + 1)]
        path_pts(c, pts, fill, stroke, 0.5)

    # brazo
    c.saveState()
    c.setFillColor(COL["skin"])
    c.setStrokeColor(COL["skin_edge"])
    c.circle(cx * CM, cy * CM, (G.skin(y) / (2 * math.pi)) * sc * CM, stroke=1, fill=1)
    c.restoreState()
    text(c, "antebrazo", cx, cy - 0.1, 7, "LS-I", GRAYT, "c")
    # base: hueco abajo (lado interior)
    gap_a = G.GAP / (2 * r_in) * 1.0
    arc_band(r_in, r_in + t, -math.pi + gap_a, math.pi - gap_a, COL["base"])
    # panel arriba
    ap = math.pi * G.panel_angle() / G.THETA
    arc_band(r_in + t, r_in + 2 * t, -ap, ap, COL["panel"])
    # cinchas laterales (sobre la base) y central (sobre el panel)
    arc_band(r_in + t, r_in + 2 * t, -math.pi + gap_a, -ap - 0.03, COL["strap"], colors.HexColor("#666666"))
    arc_band(r_in + t, r_in + 2 * t, ap + 0.03, math.pi - gap_a + 0.0, COL["strap"], colors.HexColor("#666666"))
    a_slot = ap - G.SLOT_IN / (r_in + 1.5 * t)
    arc_band(r_in + 2 * t, r_in + 3 * t, -a_slot, a_slot, COL["strap"], colors.HexColor("#666666"))
    # punta libre
    a_end = math.pi - gap_a
    x0, y0 = cx + (r_in + t) * sc * math.sin(a_end), cy + (r_in + t) * sc * math.cos(a_end)
    path_pts(c, [(x0, y0), (x0 - 1.4 * sc, y0 - 0.15 * sc), (x0 - 1.4 * sc, y0 - 0.15 * sc - t * sc), (x0, y0 - t * sc)], COL["strap"], colors.HexColor("#666666"), 0.5)
    return r_in


# ================================================================== figuras pequeñas del tutorial
def figbox(c, x, yt, w, h, title):
    box(c, x, yt - h, w, h, fill=colors.white)
    text(c, title, x + w / 2, yt - 0.5, 8, "LS-B", BROWN, "c")


def fig_cut(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Corte: cuchilla a 90°")
    y0 = yt - 3.0
    path_pts(c, [(x + 0.7, y0), (x + 5.0, y0), (x + 5.0, y0 + 0.9), (x + 0.7, y0 + 0.9)], LEATHER)
    cx = x + 2.9
    path_pts(c, [(cx - 0.18, y0 + 1.9), (cx + 0.18, y0 + 1.9), (cx + 0.02, y0 + 0.9), (cx - 0.02, y0 + 0.9)], STEEL)
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(0.8)
    c.line((cx + 0.35) * CM, y0 * CM, (cx + 0.35) * CM, (y0 + 0.9) * CM)
    c.line((cx + 0.35) * CM, y0 * CM, (cx + 0.85) * CM, y0 * CM)
    c.restoreState()
    text(c, "90°", cx + 0.55, y0 + 0.2, 7, "LS-B", ACCENT)
    text(c, "2–3 pasadas suaves, nunca una fuerte", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_slot(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Ranura del panel (corte)")
    y0 = yt - 2.9
    path_pts(c, [(x + 0.3, y0 - 0.5), (x + 5.4, y0 - 0.5), (x + 5.4, y0), (x + 0.3, y0)], COL["base"])
    path_pts(c, [(x + 0.3, y0), (x + 2.3, y0), (x + 2.3, y0 + 0.5), (x + 0.3, y0 + 0.5)], COL["panel"])
    path_pts(c, [(x + 2.8, y0), (x + 5.4, y0), (x + 5.4, y0 + 0.5), (x + 2.8, y0 + 0.5)], COL["panel"])
    path_pts(c, [(x + 2.35, y0), (x + 2.75, y0 + 0.0), (x + 3.0, y0 + 0.5), (x + 4.9, y0 + 0.5), (x + 4.9, y0 + 1.0), (x + 2.9, y0 + 1.0)],
             COL["strap"], colors.HexColor("#666666"))
    text(c, "base", x + 0.5, y0 - 0.38, 6.3, "LS-B", colors.white)
    text(c, "panel", x + 0.5, y0 + 0.13, 6.3, "LS-B", colors.white)
    text(c, "cincha central", x + 3.9, y0 + 1.2, 6.5, "LS-B", DARK, "c")
    text(c, "ranura 0,5 × 1,6", x + 1.4, y0 + 0.75, 6.5, "LS-B", ACCENT, "c")
    text(c, "la punta biselada entra 0,6 cm", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_bevel(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Puño sobre la base (lateral)")
    y0 = yt - 2.5
    path_pts(c, [(x + 2.2, y0), (x + 5.4, y0), (x + 5.4, y0 + 0.5), (x + 2.2, y0 + 0.5)], COL["base"])
    path_pts(c, [(x + 0.6, y0 + 0.5), (x + 3.8, y0 + 0.5), (x + 3.8, y0 + 1.0), (x + 0.6, y0 + 1.0)], COL["cuff"])
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(0.7)
    c.line((x + 2.2) * CM, (y0 + 1.3) * CM, (x + 3.8) * CM, (y0 + 1.3) * CM)
    c.line((x + 0.6) * CM, (y0 - 0.3) * CM, (x + 2.2) * CM, (y0 - 0.3) * CM)
    c.restoreState()
    text(c, "2 cm pegados", x + 3.0, y0 + 1.42, 6.5, "LS-B", ACCENT, "c")
    text(c, "1 cm de vuelo", x + 1.4, y0 - 0.65, 6.5, "LS-B", ACCENT, "c")
    text(c, "base", x + 4.7, y0 + 0.13, 6.3, "LS-B", colors.white, "c")
    text(c, "puño", x + 1.4, y0 + 0.63, 6.3, "LS-B", colors.white, "c")
    text(c, "← muñeca      codo →", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_heat(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Termoformado")
    cy = yt - 1.9
    path_pts(c, [(x + 0.4, cy - 0.3), (x + 1.5, cy - 0.3), (x + 1.5, cy + 0.3), (x + 0.4, cy + 0.3)], colors.HexColor("#555555"))
    path_pts(c, [(x + 1.5, cy - 0.15), (x + 2.0, cy - 0.12), (x + 2.0, cy + 0.12), (x + 1.5, cy + 0.15)], colors.HexColor("#888888"))
    path_pts(c, [(x + 2.0, cy - 0.1), (x + 3.7, cy - 0.7), (x + 3.7, cy + 0.7), (x + 2.0, cy + 0.1)], colors.HexColor("#FBD9B5"), ACCENT, 0.4)
    pts = [(x + 3.85 + 0.85 * math.cos(-0.8 + 1.6 * i / 40), cy + 1.0 * math.sin(-0.8 + 1.6 * i / 40)) for i in range(41)]
    c.saveState(); c.setStrokeColor(COL["base"]); c.setLineWidth(5); c.setLineCap(1)
    p = c.beginPath()
    for i, (X, Y) in enumerate(pts):
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.setStrokeColor(ACCENT); c.setLineWidth(0.7)
    c.line((x + 2.0) * CM, (cy - 0.95) * CM, (x + 3.8) * CM, (cy - 0.95) * CM)
    c.restoreState()
    text(c, "15–20 cm", x + 2.9, cy - 1.3, 6.8, "LS-B", ACCENT, "c")
    text(c, "mover siempre · sujetar hasta enfriar", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_mold(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "El panel se forma sobre la base")
    cx, cy = x + w / 2, yt - 3.2
    c.saveState()
    c.setStrokeColor(COL["base"]); c.setLineWidth(5)
    p = c.beginPath()
    for i in range(41):
        a = -1.25 + 2.5 * i / 40
        X, Y = cx + 1.75 * math.sin(a), cy + 1.75 * math.cos(a)
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.setStrokeColor(COL["panel"]); c.setLineWidth(5)
    p = c.beginPath()
    for i in range(21):
        a = -0.62 + 1.24 * i / 20
        X, Y = cx + 2.0 * math.sin(a), cy + 2.0 * math.cos(a)
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.restoreState()
    text(c, "panel tibio", cx, cy + 2.35, 6.8, "LS-B", COL["panel"], "c")
    text(c, "base ya formada (molde)", cx, cy + 0.5, 6.5, "LS-I", GRAYT, "c")
    text(c, "sujétalo con cinta 1–2 min", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_hinge(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Bisagra guarda–puño (lateral)")
    y0 = yt - 2.75
    path_pts(c, [(x + 0.3, y0 + 0.5), (x + 3.0, y0 + 0.5), (x + 3.0, y0 + 1.0), (x + 0.3, y0 + 1.0)], COL["base"])
    path_pts(c, [(x + 0.3, y0 + 1.0), (x + 3.6, y0 + 1.0), (x + 3.6, y0 + 1.5), (x + 0.3, y0 + 1.5)], COL["cuff"])
    path_pts(c, [(x + 2.9, y0 - 0.1), (x + 5.4, y0 - 0.55), (x + 5.45, y0 - 0.05), (x + 3.0, y0 + 0.4)], COL["guard"])
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(2.2)
    p = c.beginPath()
    p.moveTo((x + 2.6) * CM, (y0 + 0.92) * CM)
    p.curveTo((x + 3.3) * CM, (y0 + 0.9) * CM, (x + 3.1) * CM, (y0 + 0.4) * CM, (x + 4.2) * CM, (y0 + 0.1) * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.restoreState()
    text(c, "elástico / polipiel 3 cm", x + 3.9, y0 + 1.8, 6.5, "LS-B", ACCENT, "c")
    text(c, "base", x + 1.0, y0 + 0.63, 6.2, "LS-B", colors.white, "c")
    text(c, "puño", x + 1.0, y0 + 1.13, 6.2, "LS-B", colors.white, "c")
    text(c, "guarda", x + 4.9, y0 + 0.3, 6.5, "LS-B", COL["guard"], "c")
    text(c, "pegada por dentro: la muñeca se mueve", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_dagger(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Daga: qué va encima (lateral)")
    y0 = yt - 3.0
    path_pts(c, [(x + 0.3, y0), (x + 5.4, y0), (x + 5.4, y0 + 0.4), (x + 0.3, y0 + 0.4)], COL["panel"])
    path_pts(c, [(x + 0.4, y0 + 0.4), (x + 2.0, y0 + 0.4), (x + 2.0, y0 + 0.75), (x + 0.4, y0 + 0.75)], COL["strap"])
    path_pts(c, [(x + 0.6, y0 + 0.75), (x + 2.6, y0 + 0.75), (x + 2.6, y0 + 1.25), (x + 0.6, y0 + 1.25)], COL["grip"])
    path_pts(c, [(x + 2.6, y0 + 0.5), (x + 2.85, y0 + 0.5), (x + 2.85, y0 + 1.5), (x + 2.6, y0 + 1.5)], STEEL)
    path_pts(c, [(x + 2.85, y0 + 0.4), (x + 5.2, y0 + 0.4), (x + 5.2, y0 + 1.2), (x + 2.85, y0 + 1.2)], COL["sheath"])
    path_pts(c, [(x + 3.3, y0 + 0.4), (x + 3.3, y0 + 1.2), (x + 3.4, y0 + 1.5), (x + 4.4, y0 + 1.5), (x + 4.5, y0 + 1.2), (x + 4.5, y0 + 0.4),
                 (x + 4.35, y0 + 0.4), (x + 4.35, y0 + 1.2), (x + 4.3, y0 + 1.33), (x + 3.5, y0 + 1.33), (x + 3.45, y0 + 1.2), (x + 3.45, y0 + 0.4)],
             COL["strap"], colors.HexColor("#666666"), 0.4)
    text(c, "B3c", x + 1.2, y0 + 0.47, 6, "LS-B", colors.white, "c")
    text(c, "empuñadura", x + 1.6, y0 + 1.45, 6.3, "LS-B", COL["grip"], "c")
    text(c, "B1c / B2c", x + 3.9, y0 + 1.75, 6.3, "LS-B", DARK, "c")
    text(c, "funda", x + 4.9, y0 + 0.7, 6.0, "LS-B", colors.white, "c")
    text(c, "panel", x + 0.6, y0 + 0.1, 6.0, "LS-B", colors.white)
    text(c, "funda bajo B1c/B2c · empuñadura sobre B3c", x + w / 2, yt - 3.85, 6.6, "LS", DARK, "c")


def fig_ring(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Corte: hueco de cierre")
    cx, cy, r = x + w / 2, yt - 1.85, 1.2
    gap = G.GAP / (G.skin(10) / (2 * math.pi) + 0.6) / 1.0
    a0, a1 = -math.pi / 2 + gap / 2, -math.pi / 2 + 2 * math.pi - gap / 2
    pts = [(cx + r * math.cos(a0 + (a1 - a0) * i / 60), cy + r * math.sin(a0 + (a1 - a0) * i / 60)) for i in range(61)]
    c.saveState(); c.setStrokeColor(COL["base"]); c.setLineWidth(5); c.setLineCap(1)
    p = c.beginPath(); p.moveTo(pts[0][0] * CM, pts[0][1] * CM)
    for X, Y in pts[1:]:
        p.lineTo(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.setStrokeColor(colors.HexColor("#E0B88F")); c.setLineWidth(0.8); c.setDash(2, 2)
    c.circle(cx * CM, cy * CM, (r - 0.45) * CM, stroke=1, fill=0)
    c.restoreState()
    text(c, "antebrazo", cx, cy - 0.1, 6.5, "LS-I", GRAYT, "c")
    text(c, f"hueco ≈ {f1(G.GAP)} cm", cx, cy - r - 0.5, 6.8, "LS-B", ACCENT, "c")
    text(c, "(lado interior, lo cierran las cinchas)", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_row(c, yt, figs, x=1.6):
    for fn in figs:
        fn(c, x, yt)
        x += 5.7 + 0.35
    return yt - 4.3


def checklist(c, x, ytop, w, title, items, cols=2):
    rows = (len(items) + cols - 1) // cols
    h = 0.95 + rows * 0.6
    box(c, x, ytop - h, w, h, fill=colors.white)
    text(c, title, x + 0.35, ytop - 0.65, 9.5, "LS-B", BROWN)
    cw = (w - 0.7) / cols
    for i, it in enumerate(items):
        col, row = i // rows, i % rows
        xx, yy = x + 0.35 + col * cw, ytop - 1.3 - row * 0.6
        c.saveState(); c.setStrokeColor(DARK); c.setLineWidth(0.7)
        c.rect(xx * CM, (yy - 0.05) * CM, 0.3 * CM, 0.3 * CM, stroke=1, fill=0)
        c.restoreState()
        text(c, it, xx + 0.5, yy + 0.02, 7.8, "LS", DARK)
    return ytop - h


def steps_block(c, y, steps, x=1.6, w=17.8, style="step"):
    for tag, title, body in steps:
        c.setFillColor(ACCENT)
        c.circle((x + 0.35) * CM, (y - 0.45) * CM, 0.38 * CM, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont("LS-B", 9 if len(str(tag)) < 2 else 8)
        c.drawCentredString((x + 0.35) * CM, (y - 0.45) * CM - 3.2, str(tag))
        p = Paragraph(f"<b>{title}.</b> {body}", ST[style])
        _, h = p.wrap((w - 1.2) * CM, 2000)
        p.drawOn(c, (x + 1.1) * CM, y * CM - h)
        y -= max(h / CM, 0.85) + 0.25
    return y
