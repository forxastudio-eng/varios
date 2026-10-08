"""Genera el PDF de patrones + tutorial de las braceras de Hipo (EVA 5 mm, A4)."""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geom as G
from shapely import affinity
from shapely.geometry import LineString
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph, Table, TableStyle

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "Braceras_Hipo_Patrones_y_Tutorial.pdf")

# ------------------------------------------------------------------ fuentes
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
GRAYT = colors.HexColor("#6B6B6B")
GUIDE = colors.HexColor("#3A78B5")
STEEL = colors.HexColor("#B8B8BC")

PW, PH = 21.0, 29.7   # retrato (cm)
TOTAL_PAGES = 13


def f1(v):
    return f"{v:.1f}".replace(".", ",")


def f2(v):
    return f"{v:.2f}".replace(".", ",")


C = G.C
R1, R2, TH = G.R1, G.R2, G.THETA

# ------------------------------------------------------------------ estilos
ST = {
    "b": ParagraphStyle("b", fontName="LS", fontSize=9, leading=12.3, textColor=DARK),
    "s": ParagraphStyle("s", fontName="LS", fontSize=8, leading=10.6, textColor=DARK),
    "xs": ParagraphStyle("xs", fontName="LS", fontSize=7.2, leading=9.2, textColor=GRAYT),
    "h2": ParagraphStyle("h2", fontName="LS-B", fontSize=12, leading=15, textColor=BROWN, spaceBefore=0),
    "h3": ParagraphStyle("h3", fontName="LS-B", fontSize=9.6, leading=12.5, textColor=BROWN),
    "cell": ParagraphStyle("cell", fontName="LS", fontSize=8, leading=10.2, textColor=DARK),
    "cellb": ParagraphStyle("cellb", fontName="LS-B", fontSize=8, leading=10.2, textColor=DARK),
    "th": ParagraphStyle("th", fontName="LS-B", fontSize=8, leading=10, textColor=colors.white),
    "bul": ParagraphStyle("bul", fontName="LS", fontSize=9, leading=12.3, textColor=DARK, leftIndent=11, bulletIndent=1),
    "buls": ParagraphStyle("buls", fontName="LS", fontSize=8, leading=10.6, textColor=DARK, leftIndent=10, bulletIndent=1),
    "step": ParagraphStyle("step", fontName="LS", fontSize=10.2, leading=14.4, textColor=DARK, leftIndent=0),
    "center": ParagraphStyle("center", fontName="LS", fontSize=9, leading=12, textColor=DARK, alignment=1),
}


def para(c, text, x, ytop, w, style="b"):
    p = Paragraph(text, ST[style] if isinstance(style, str) else style)
    _, h = p.wrap(w * CM, 1000)
    p.drawOn(c, x * CM, ytop * CM - h)
    return ytop - h / CM


def table(c, data, x, ytop, colw, header=True, zebra=True, pad=3):
    rows = []
    for i, r in enumerate(data):
        row = []
        for j, cell in enumerate(r):
            if isinstance(cell, str):
                st = "th" if (header and i == 0) else ("cellb" if j == 0 else "cell")
                row.append(Paragraph(cell, ST[st]))
            else:
                row.append(cell)
        rows.append(row)
    t = Table(rows, colWidths=[w * CM for w in colw])
    style = [
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#D9CBB8")),
        ("TOPPADDING", (0, 0), (-1, -1), pad),
        ("BOTTOMPADDING", (0, 0), (-1, -1), pad),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
    ]
    if header:
        style.append(("BACKGROUND", (0, 0), (-1, 0), BROWN))
    if zebra:
        for i in range(1 if header else 0, len(rows)):
            if i % 2 == 0:
                style.append(("BACKGROUND", (0, i), (-1, i), CREAM))
    t.setStyle(TableStyle(style))
    _, h = t.wrap(sum(colw) * CM, 1000)
    t.drawOn(c, x * CM, ytop * CM - h)
    return ytop - h / CM


def box(c, x, y, w, h, fill=CREAM, stroke=colors.HexColor("#D9CBB8"), r=0.2):
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(0.6)
    c.roundRect(x * CM, y * CM, w * CM, h * CM, r * CM, stroke=1, fill=1)


def text(c, s, x, y, size=8, font="LS", color=DARK, align="l", rot=0):
    c.saveState()
    c.setFillColor(color)
    c.setFont(font, size)
    c.translate(x * CM, y * CM)
    if rot:
        c.rotate(rot)
    if align == "c":
        c.drawCentredString(0, 0, s)
    elif align == "r":
        c.drawRightString(0, 0, s)
    else:
        c.drawString(0, 0, s)
    c.restoreState()


def chrome(c, n, part, title):
    """Cabecera y pie de las páginas de texto (retrato)."""
    c.setFillColor(BROWN)
    c.rect(0, (PH - 1.35) * CM, PW * CM, 1.35 * CM, stroke=0, fill=1)
    c.setFillColor(ACCENT)
    c.rect(0, (PH - 1.45) * CM, PW * CM, 0.1 * CM, stroke=0, fill=1)
    text(c, title, 1.6, PH - 0.88, 13, "LS-B", colors.white)
    text(c, part, PW - 1.6, PH - 0.88, 8.5, "LS", colors.HexColor("#F0DCC4"), "r")
    c.setStrokeColor(colors.HexColor("#D9CBB8"))
    c.setLineWidth(0.5)
    c.line(1.6 * CM, 1.35 * CM, (PW - 1.6) * CM, 1.35 * CM)
    text(c, "Braceras de Hipo · goma EVA 5 mm · talla de referencia 1,75 m", 1.6, 0.85, 7.5, "LS", GRAYT)
    text(c, f"{n} / {TOTAL_PAGES}", PW - 1.6, 0.85, 7.5, "LS-B", GRAYT, "r")


# ------------------------------------------------------------------ dibujo helpers
def draw_poly(c, poly, fill=None, stroke=DARK, lw=1.0, dash=None, ox=0.0, oy=0.0):
    p = c.beginPath()
    pts = list(poly.exterior.coords)
    p.moveTo((pts[0][0] + ox) * CM, (pts[0][1] + oy) * CM)
    for x, y in pts[1:]:
        p.lineTo((x + ox) * CM, (y + oy) * CM)
    p.close()
    c.saveState()
    c.setLineWidth(lw)
    if dash:
        c.setDash(list(dash))
    if fill is not None:
        c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.drawPath(p, stroke=1 if stroke is not None else 0, fill=1 if fill is not None else 0)
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


def arc_line(r, a0, a1, n=80):
    return G.arc_pts(r, a0, a1, n)


# ================================================================== elevación esquemática
COL = dict(body=colors.HexColor("#7B4A2A"), rim=colors.HexColor("#A9744A"), cuff=colors.HexColor("#2B2B2B"),
           strap=colors.HexColor("#1E1E1E"), plate=colors.HexColor("#8A5530"), sheath=colors.HexColor("#4A4A4D"),
           grip=colors.HexColor("#5A3820"), metal=STEEL)
LAYERS = ["body", "rim", "cuff", "straps", "plate", "dagger"]
BODY_H = 17.4


def hw_body(s):
    return 4.65 - 1.3 * s / BODY_H


def draw_bracer(c, ox, oy, sc, upto=6, hl=None, ghost=False):
    """Vista frontal esquemática. (ox,oy) en cm: eje del brazal, borde superior; sc = factor (1 = tamaño real)."""

    def P(x, s):
        return ((ox + x * sc), (oy - s * sc))

    def poly(pts, fill, stroke=DARK, lw=0.5, hlite=False):
        p = c.beginPath()
        for i, (x, s) in enumerate(pts):
            X, Y = P(x, s)
            (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
        p.close()
        c.saveState()
        c.setFillColor(fill)
        c.setStrokeColor(ACCENT if hlite else stroke)
        c.setLineWidth(2.2 if hlite else lw)
        c.drawPath(p, stroke=1, fill=1)
        c.restoreState()

    def active(name):
        return LAYERS.index(name) < upto

    def is_hl(name):
        return hl == name

    if active("body"):
        poly([(-hw_body(0), 0), (hw_body(0), 0), (hw_body(BODY_H), BODY_H), (-hw_body(BODY_H), BODY_H)],
             COL["body"], hlite=is_hl("body"))
    if active("rim"):
        for s0, s1 in ((0, 1.0), (BODY_H - 1.0, BODY_H)):
            poly([(-hw_body(s0), s0), (hw_body(s0), s0), (hw_body(s1), s1), (-hw_body(s1), s1)],
                 COL["rim"], hlite=is_hl("rim"))
    if active("cuff"):
        poly([(-hw_body(BODY_H), BODY_H), (hw_body(BODY_H), BODY_H), (3.0, BODY_H + 3), (-3.0, BODY_H + 3)],
             COL["cuff"], hlite=is_hl("cuff"))
    if active("straps"):
        for s in G.STRAP_S:
            h = hw_body(s)
            poly([(-h + 0.3, s - 0.75), (h, s - 0.75), (h + 1.0, s), (h, s + 0.75), (-h + 0.3, s + 0.75)],
                 COL["strap"], stroke=colors.HexColor("#555555"), hlite=is_hl("straps"))
    if active("plate"):
        pl = G.plate()
        pts = [(x * 0.85, 18.9 - y) for x, y in pl.exterior.coords]
        poly(pts, COL["plate"], hlite=is_hl("plate"))
        # línea de grabado
        inner = pl.buffer(-0.6)
        if not inner.is_empty:
            ipts = [(x * 0.85, 18.9 - y) for x, y in inner.exterior.coords]
            c.saveState()
            c.setStrokeColor(colors.HexColor("#C79A6B"))
            c.setLineWidth(0.5)
            c.setDash(1.5, 1.5)
            p = c.beginPath()
            for i, (x, s) in enumerate(ipts):
                X, Y = P(x, s)
                (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
            p.close()
            c.drawPath(p, stroke=1, fill=0)
            c.restoreState()
    if active("dagger"):
        xc = -0.6
        sh = G.sheath_base()
        poly([(x + xc, 1.6 + (7.5 - y)) for x, y in sh.exterior.coords], COL["sheath"], hlite=is_hl("dagger"))
        cv = G.sheath_cover()
        c.saveState()
        c.setStrokeColor(colors.HexColor("#8A8A90"))
        c.setLineWidth(0.4)
        c.setDash(1.5, 1.5)
        p = c.beginPath()
        for i, (x, y) in enumerate(cv.buffer(-0.15).exterior.coords):
            X, Y = P(x + xc, 1.6 + (7.5 - y) - 0.0)
            (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
        p.close()
        c.drawPath(p, stroke=1, fill=0)
        c.restoreState()
        poly([(x + xc, 9.1 + (0.8 - y)) for x, y in G.guard().exterior.coords], COL["metal"], hlite=is_hl("dagger"))
        poly([(x + xc, 9.9 + (4.6 - y)) for x, y in G.grip().exterior.coords], COL["grip"], hlite=is_hl("dagger"))
        poly([(x + xc, 14.5 + 0.75 - y) for x, y in G.pommel().exterior.coords], COL["metal"], hlite=is_hl("dagger"))
    if ghost:
        c.saveState()
        c.setStrokeColor(GRAYT)
        c.setDash(1.5, 1.5)
        c.setLineWidth(0.7)
        for s in G.STRAP_S:   # anillas D fantasma en las puntas
            X, Y = P(hw_body(s) + 1.55, s)
            c.circle(X * CM, Y * CM, 0.32 * sc * CM, stroke=1, fill=0)
        for x, s in ((-1.6, 26.4), (1.6, 26.4), (0.0, 28.3)):   # remaches fantasma
            X, Y = P(x, s)
            c.circle(X * CM, Y * CM, 0.2 * sc * CM, stroke=1, fill=0)
        c.restoreState()
    return P


def callout(c, n, p_from, p_to, r=0.3):
    c.saveState()
    c.setStrokeColor(ACCENT)
    c.setLineWidth(0.8)
    c.line(p_from[0] * CM, p_from[1] * CM, p_to[0] * CM, p_to[1] * CM)
    c.setFillColor(ACCENT)
    c.circle(p_to[0] * CM, p_to[1] * CM, r * CM, stroke=0, fill=1)
    c.setFillColor(colors.white)
    c.setFont("LS-B", 9)
    c.drawCentredString(p_to[0] * CM, p_to[1] * CM - 3.1, str(n))
    c.setFillColor(ACCENT)
    c.circle(p_from[0] * CM, p_from[1] * CM, 0.07 * CM, stroke=0, fill=1)
    c.restoreState()


# ================================================================== dibujo de antebrazo/mano (medidas)
def draw_arm(c, ox, oy, sc):
    """ox,oy: posición (cm) de la muñeca (y=0 en el pliegue); y hacia arriba = codo."""

    def P(x, y):
        return (ox + x * sc, oy + y * sc)

    def hw(y):
        return 2.75 + 0.065 * y

    skin = colors.HexColor("#EBCBA8")
    edge = colors.HexColor("#B88B62")

    def poly(pts, fill, stroke=edge, lw=0.8, alpha=1.0):
        p = c.beginPath()
        for i, (x, y) in enumerate(pts):
            X, Y = P(x, y)
            (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
        p.close()
        c.saveState()
        c.setFillAlpha(alpha)
        c.setFillColor(fill)
        c.setStrokeColor(stroke)
        c.setLineWidth(lw)
        c.drawPath(p, stroke=1, fill=1)
        c.restoreState()

    # antebrazo
    poly([(-hw(0), 0), (hw(0), 0), (hw(25.5), 25.5), (-hw(25.5), 25.5)], skin)
    # mano (dorso) y dedos
    poly([(-2.9, 0), (2.9, 0), (4.3, -10), (-4.3, -10)], skin)
    for i in range(4):
        x0 = -4.3 + i * 2.15
        ln = [7.6, 8.6, 8.2, 6.6][i]
        poly([(x0 + 0.05, -10), (x0 + 2.1, -10), (x0 + 2.0, -10 - ln), (x0 + 0.15, -10 - ln)], skin)
    # brazal translúcido
    for pts, col in (([(-hw(4) - 0.4, 4), (hw(4) + 0.4, 4), (hw(21.5) + 0.4, 21.5), (-hw(21.5) - 0.4, 21.5)], COL["body"]),
                     ([(-hw(1) - 0.35, 1), (hw(1) + 0.35, 1), (hw(4) + 0.4, 4), (-hw(4) - 0.4, 4)], COL["cuff"]),
                     ([(-3.7, 2.5), (3.7, 2.5), (4.7, -2.5), (4.5, -6), (3.5, -7.8), (0, -9.0), (-3.5, -7.8), (-4.5, -6), (-4.7, -2.5)], COL["plate"])):
        poly(pts, col, stroke=DARK, lw=0.6, alpha=0.55)

    def dim_h(y, x0, x1, label, side="r", extra=0.0):
        X0, Y = P(x0, y)
        X1, _ = P(x1, y)
        c.saveState()
        c.setStrokeColor(ACCENT)
        c.setLineWidth(0.8)
        c.line(X0 * CM, Y * CM, X1 * CM, Y * CM)
        for X in (X0, X1):
            c.line(X * CM, (Y - 0.15) * CM, X * CM, (Y + 0.15) * CM)
        c.restoreState()
        if side == "r":
            text(c, label, X1 + 0.2 + extra * sc, Y - 0.1, 7.5, "LS-B", ACCENT)

    def dim_v(x, y0, y1, label, left=True):
        X, Y0 = P(x, y0)
        _, Y1 = P(x, y1)
        c.saveState()
        c.setStrokeColor(ACCENT)
        c.setLineWidth(0.8)
        c.line(X * CM, Y0 * CM, X * CM, Y1 * CM)
        for Y in (Y0, Y1):
            c.line((X - 0.15) * CM, Y * CM, (X + 0.15) * CM, Y * CM)
        c.restoreState()
        text(c, label, X - 0.2 if left else X + 0.2, (Y0 + Y1) / 2, 7.5, "LS-B", ACCENT, "c", 90 if left else 90)

    dim_h(21.5, -hw(21.5) - 0.4, hw(21.5) + 0.4, "M2 · 26,0")
    dim_h(4.0, -hw(4) - 0.4, hw(4) + 0.4, "M3 · 18,0")
    dim_h(0.0, -hw(0) - 0.2, hw(0) + 0.2, "M4 · 17,0", extra=2.2)
    dim_h(-10.0, -4.3, 4.3, "M8 · 8,6", extra=0.2)
    dim_v(-hw(21.5) - 1.0, 4, 21.5, "M5 · 17,5 (largo del brazal)")
    dim_v(-5.0, 0, -10, "M7 · 10,0")
    text(c, "codo ↑", ox, oy + 26.3 * sc, 7.5, "LS-I", GRAYT, "c")


# ================================================================== PORTADA (pág. 1)
def page_cover(c):
    c.setFillColor(BROWN)
    c.rect(0, (PH - 7.6) * CM, PW * CM, 7.6 * CM, stroke=0, fill=1)
    c.setFillColor(ACCENT)
    c.rect(0, (PH - 7.75) * CM, PW * CM, 0.15 * CM, stroke=0, fill=1)
    text(c, "COSPLAY · PATRONES + TUTORIAL", 1.8, PH - 2.0, 9.5, "LS-B", colors.HexColor("#F0DCC4"))
    text(c, "BRACERAS DE HIPO", 1.8, PH - 3.7, 34, "LS-B", colors.white)
    text(c, "Cómo entrenar a tu dragón 2", 1.8, PH - 4.7, 15, "LS-I", colors.HexColor("#F0DCC4"))
    text(c, "Hecho en goma EVA de 5 mm · piezas a escala 1:1 en hojas A4", 1.8, PH - 5.65, 11, "LS", colors.white)
    text(c, "Talla de referencia: hombre de 1,75 m · complexión normal", 1.8, PH - 6.4, 10, "LS-B", colors.HexColor("#F0DCC4"))
    # dibujo
    draw_bracer(c, 15.4, PH - 8.9, 0.3, upto=6)
    # texto lateral
    y = PH - 9.2
    box(c, 1.6, y - 6.4, 9.7, 6.4)
    text(c, "Qué incluye", 2.0, y - 0.75, 11, "LS-B", BROWN)
    items = [
        "Análisis de la pieza a partir de tus dos referencias (render de la película y réplica de cosplay).",
        "Medidas y cálculo para un hombre de 1,75 m, con la fórmula para cualquier otra talla.",
        "<b>4 hojas de patrones A4 a escala 1:1</b> (P1–P4): cada pieza cabe en una hoja A4 de goma EVA.",
        "Orden de montaje por capas y tutorial paso a paso: corte, termoformado, pegado, sellado y pintura.",
    ]
    yy = y - 1.05
    for it in items:
        yy = para(c, it, 2.0, yy, 9.0, ST["bul"].clone("x", bulletText="•")) if False else yy
        p = Paragraph(it, ST["bul"], bulletText="•")
        _, h = p.wrap(9.0 * CM, 1000)
        p.drawOn(c, 2.0 * CM, yy * CM - h)
        yy -= h / CM + 0.15
    # cómo usar
    y2 = y - 9.4
    box(c, 1.6, y2 - 5.6, 17.8, 5.6)
    text(c, "Cómo usar este PDF", 2.0, y2 - 0.75, 11, "LS-B", BROWN)
    steps = [
        "<b>Imprime las hojas P1–P4</b> (páginas 6 a 9) en horizontal y al <b>100 % · «tamaño real»</b>. Desactiva «ajustar a página».",
        "<b>Comprueba la regla de 10 cm</b> impresa en la esquina de cada hoja. Si no mide 10,0 cm, corrige la escala.",
        "Recorta las piezas por la línea negra gruesa, traza sobre la goma EVA y corta. Las líneas discontinuas son guías, no se cortan.",
        "Prueba la talla con papel (tutorial, paso 1) y, si tu antebrazo es distinto, usa la tabla de la página 13.",
        "Monta siguiendo el orden de capas de la página 5 y el tutorial de las páginas 10 a 12.",
    ]
    yy = y2 - 1.1
    for i, s in enumerate(steps, 1):
        p = Paragraph(s, ST["b"], bulletText=f"{i}.")
        p.style = ParagraphStyle("n", parent=ST["b"], leftIndent=13, bulletIndent=1, bulletFontName="LS-B")
        p = Paragraph(s, p.style, bulletText=f"{i}.")
        _, h = p.wrap(17.0 * CM, 1000)
        p.drawOn(c, 2.0 * CM, yy * CM - h)
        yy -= h / CM + 0.2
    # fuera del pdf
    y3 = y2 - 5.6 - 0.4
    box(c, 1.6, y3 - 3.0, 17.8, 3.0, fill=colors.HexColor("#F3F3F3"))
    text(c, "Fuera de este PDF (detalles)", 2.0, y3 - 0.75, 11, "LS-B", GRAYT)
    para(c, "Hebillas y anillas D · remaches y tachuelas · lazo/correa del dedo · hoja metálica y adornos · ojales y cierre interior · "
            "costuras y texturas finas. El tutorial solo indica <i>dónde van</i> (en gris discontinuo en el dibujo) para que puedas añadirlos después con los materiales que prefieras.",
         2.0, y3 - 1.05, 17.0, "b")


# ================================================================== PÁG 2: análisis
def page_analysis(c):
    chrome(c, 2, "PARTE I · ANÁLISIS", "Análisis de la pieza")
    y = para(c, "Las dos referencias muestran el mismo brazal con dos lecturas. La <b>ref. 1</b> (render de la película) da las proporciones y "
                "la distribución de piezas; la <b>ref. 2</b> (réplica de cosplay) muestra cómo se resuelve en capas: borde elevado, cinchas, "
                "funda de daga y placa de mano. Tomamos lo estructural de ambas y dejamos fuera la herrería (hebillas, remaches, anillas).",
             1.6, PH - 2.2, 17.8, "b")
    # dibujo con llamadas
    ox, oy, sc = 4.3, y - 1.2, 0.56
    P = draw_bracer(c, ox, oy, sc, upto=6, ghost=True)
    def pt(x, s):
        return P(x, s)
    callout(c, 1, pt(3.3, 11.6), (pt(3.3, 11.6)[0] + 1.9, pt(3.3, 11.6)[1]))
    callout(c, 2, pt(4.9, 8.75), (pt(4.9, 8.75)[0] + 1.3, pt(4.9, 8.75)[1] + 0.6))
    callout(c, 3, pt(2.8, 0.5), (pt(2.8, 0.5)[0] + 2.0, pt(2.8, 0.5)[1] + 0.3))
    callout(c, 4, pt(2.5, 19.0), (pt(2.5, 19.0)[0] + 2.2, pt(2.5, 19.0)[1]))
    callout(c, 5, pt(3.0, 24.5), (pt(3.0, 24.5)[0] + 2.1, pt(3.0, 24.5)[1]))
    callout(c, 6, pt(-0.6, 4.5), (pt(-0.6, 4.5)[0] - 2.4, pt(-0.6, 4.5)[1] + 0.3))
    callout(c, 6, pt(-0.6, 12.2), (pt(-0.6, 12.2)[0] - 2.4, pt(-0.6, 12.2)[1]))
    callout(c, 7, pt(1.6, 26.4), (pt(1.6, 26.4)[0] + 1.9, pt(1.6, 26.4)[1] - 0.4))
    text(c, "Vista frontal esquemática del brazal con daga", ox, oy - 31.2 * sc - 0.2, 7.5, "LS-I", GRAYT, "c")
    # tabla
    data = [
        ["", "Qué se ve en las referencias", "Cómo lo resolvemos en EVA 5 mm", "Pieza"],
        ["1", "Cuerpo de cuero marrón, cónico: ancho en el codo y estrecho en la muñeca. Se abre por el lado interior del antebrazo.",
         "Un <b>sector de corona circular</b> (cono desarrollado) que se termoforma y deja ~2 cm de hueco de cierre.", "A ×2"],
        ["2", "Tres cinchas negras horizontales; sus puntas sobresalen por el borde de cierre.",
         "Tiras rectas de 1,5 cm pegadas encima (no se enhebran en EVA de 5 mm). La punta libre queda para la hebilla.", "B1–B3 ×2"],
        ["3", "Borde elevado alrededor del cuerpo (marcado en la ref. 2, más claro en la ref. 1).",
         "Dos tiras en arco, arriba y abajo, con los cantos biselados.", "C, D ×2"],
        ["4", "Puño negro justo antes de la mano, en continuidad con el cono.", "Banda cónica de 3 cm que prolonga el cuerpo hasta la muñeca.", "E ×2"],
        ["5", "Placa sobre el dorso de la mano con línea grabada paralela al borde y remaches.",
         "Placa de 5 mm curvada a lo ancho, con línea de grabado a 0,6 cm del borde. Remaches fuera.", "F ×2"],
        ["6", "Daga en un solo brazal: funda oscura apuntando al codo, empuñadura marrón, guarda y pomo metálicos.",
         "Funda en 2 capas (base + tapa más pequeña) y empuñadura en 2 capas (10 mm). Daga fija, sin hoja.", "G1, G2, H1–H3"],
        ["7", "Anillas D, remaches, hebillas, correa del dedo.", "<b>Fuera del PDF</b>. Van en gris discontinuo en el dibujo.", "—"],
    ]
    yt = table(c, data, 8.3, y - 0.2, [0.55, 4.3, 4.65, 1.6])
    # observaciones
    yb = min(yt, oy - 31.2 * sc - 0.7) - 0.3
    yb = para(c, "Observaciones de construcción", 1.6, yb, 17.8, "h2")
    obs = [
        "<b>Asimetría:</b> en las referencias la daga aparece en un solo brazal (ref. 1: el brazo izquierdo del personaje). Todas las piezas son simétricas, así que "
        "puedes montar la funda en el brazal que prefieras; el otro lleva solo cinchas.",
        "<b>Cono, no cilindro:</b> el antebrazo pasa de ~26 cm de perímetro cerca del codo a ~18 cm junto a la muñeca. Un rectángulo arrugaría; "
        "el sector circular (pág. 3) da un ajuste limpio sin cortes.",
        "<b>Capas:</b> cuerpo (5 mm) → ribetes y cinchas (+5 mm) → daga (+10 mm). Con tan poca altura acumulada el brazal sigue siendo cómodo y flexible.",
        "<b>Todo cabe en A4:</b> la pieza más grande (cuerpo, "
        f"{f1(C['bw'])} × {f1(C['bh'])} cm) cabe en una hoja de goma EVA de 21 × 29,7 cm sin empalmes.",
    ]
    for o in obs:
        p = Paragraph(o, ST["bul"], bulletText="•")
        _, h = p.wrap(17.8 * CM, 1000)
        p.drawOn(c, 1.6 * CM, yb * CM - h)
        yb -= h / CM + 0.15
    assert yb > 1.8, f"pág 2 desborda: {yb}"


# ================================================================== PÁG 3: medidas
def page_measures(c):
    chrome(c, 3, "PARTE I · ANÁLISIS", "Medidas para 1,75 m y cálculo del patrón")
    y = para(c, "Medidas de partida para un hombre de <b>1,75 m y complexión normal</b> (valores antropométricos medios, medidos sobre la manga que "
                "vayas a llevar). Si las tuyas difieren, usa la fórmula de abajo o la tabla de la página 13.", 1.6, PH - 2.2, 17.8, "b")
    draw_arm(c, 4.4, 11.9, 0.46)
    data = [
        ["Cód.", "Medida", "Valor"],
        ["M1", "Estatura de referencia", "175 cm"],
        ["M2", "Perímetro del antebrazo, borde superior (a 21,5 cm de la muñeca)", "26,0 cm"],
        ["M3", "Perímetro del antebrazo, borde inferior (a 4 cm de la muñeca)", "18,0 cm"],
        ["M4", "Perímetro de la muñeca (hueso)", "17,0 cm"],
        ["M5", "Largo del cuerpo del brazal (medido sobre el cono)", "17,5 cm"],
        ["M6", "Alto del puño", "3,0 cm"],
        ["M7", "Largo muñeca → nudillos (dorso de la mano)", "10,0 cm"],
        ["M8", "Ancho del dorso a la altura de los nudillos", "8,6 cm"],
        ["M9", "Holgura (manga + movimiento), sumada al perímetro", "+1,5 cm"],
        ["M10", "Hueco de cierre entre bordes (lado interior)", "2,0 cm"],
        ["M11", "Compensación por grosor de EVA: π × 0,5 cm", "+1,57 cm"],
    ]
    X0 = 9.0
    yt = table(c, data, X0, y - 0.3, [1.1, 7.3, 2.0], pad=2.5)
    yb = yt - 0.5
    yb = para(c, "Cómo se obtiene el cuerpo del brazal (pieza A)", X0, yb, 10.4, "h3")
    yb -= 0.1
    L1, L2 = C["L1"], C["L2"]
    form = [
        f"Arco superior  L1 = M2 + M9 + M11 − M10<br/>= 26,0 + 1,5 + 1,57 − 2,0 = <b>{f1(L1)} cm</b>",
        f"Arco inferior  L2 = M3 + M9 + M11 − M10<br/>= 18,0 + 1,5 + 1,57 − 2,0 = <b>{f1(L2)} cm</b>",
        f"Ángulo = (L1 − L2) / M5 = {f1(L1 - L2)} / 17,5 = <b>{f2(TH)} rad = {f1(math.degrees(TH))}°</b>",
        f"Radio inferior R2 = L2 / ángulo = <b>{f1(R2)} cm</b><br/>Radio superior R1 = R2 + M5 = <b>{f1(R1)} cm</b>",
        f"Resultado: sector de {f1(C['bw'])} × {f1(C['bh'])} cm → <b>cabe en una hoja A4</b> (29,7 × 21).",
    ]
    hbox = 4.7
    box(c, X0, yb - hbox, 10.4, hbox)
    yy = yb - 0.25
    for f in form:
        yy = para(c, f, X0 + 0.3, yy, 9.9, "s") - 0.2
    yb = yb - hbox - 0.3
    yb = para(c, "Por qué estas correcciones: un tubo desarrollado es un arco; al curvar goma de 5 mm la fibra neutra queda a 2,5 mm de la cara "
                 "interior, por eso el patrón se mide en el centro del grosor (+π × t). El hueco de cierre se resta porque los bordes no se tocan.",
              X0, yb, 10.4, "xs")
    yb = para(c, "<b>Otras piezas derivadas del mismo cono.</b> Puño E: radios "
            f"{f1(R2)} y {f1(R2 - G.CUFF_H)} cm, ángulo {f1(math.degrees(TH))}°, alto 3,0 cm. "
            f"Cinchas B: largo = arco a esa altura + 1,0 cm "
            f"(B1 {f1(G.strap(G.STRAP_S[0])[1])} · B2 {f1(G.strap(G.STRAP_S[1])[1])} · B3 {f1(G.strap(G.STRAP_S[2])[1])} cm × 1,5 cm). "
            "Ribetes C y D: arcos de 1,0 cm de ancho con el mismo ángulo. Placa F: 11,5 × 9,4 cm (1,5 cm superiores sobre el puño).",
         X0, yb - 0.35, 10.4, "s")
    yb -= 0.5
    box(c, X0, yb - 6.6, 10.4, 6.6, fill=colors.HexColor("#FFF6E8"), stroke=ACCENT)
    text(c, "Cómo tomar tus medidas", X0 + 0.3, yb - 0.65, 10, "LS-B", ACCENT)
    tips = ["Usa una cinta métrica flexible y mide <b>con la manga que llevarás puesta</b> (en el traje de Hipo, la manga verde).",
            "Brazo relajado y ligeramente flexionado, sin apretar la cinta.",
            "Marca con un rotulador de piel el punto a 4 cm y a 21,5 cm del pliegue de la muñeca y mide el perímetro en cada marca.",
            "Mide el largo muñeca → nudillos con la mano abierta y el ancho a la altura de los nudillos.",
            "Si tu proporción difiere (brazo muy musculado o muy delgado), sustituye M2 y M3 en la fórmula o usa la tabla de la pág. 13.",
            "Haz el patrón de papel (tutorial, paso 1) antes de cortar la goma."]
    yy = yb - 1.0
    for t_ in tips:
        p = Paragraph(t_, ST["buls"], bulletText="•")
        _, h = p.wrap(9.8 * CM, 1000)
        p.drawOn(c, (X0 + 0.3) * CM, yy * CM - h)
        yy -= h / CM + 0.15
    yb = yb - 6.6
    assert yb > 1.8, f"pág 3 desborda: {yb}"


# ================================================================== PÁG 4: materiales
def page_materials(c):
    chrome(c, 4, "PARTE I · ANÁLISIS", "Materiales, herramientas y lista de piezas")
    y = PH - 2.2
    colw = 8.7
    y = para(c, "Materiales", 1.6, y, colw, "h2")
    mats = [
        "<b>Goma EVA 5 mm, hojas A4 (21 × 29,7 cm): 5 hojas</b> (compra 6: una de repuesto). Mejor densidad media-alta; evita la goma muy blanda de manualidades.",
        "Cemento de contacto (tipo «contact cement»), con pincel o espátula.",
        "Papel A4 (para imprimir), tijeras y cartulina fina (plantillas reutilizables, opcional).",
        "Imprimación flexible: Plasti Dip o vinílica/PVA diluida; pintura acrílica (marrón, negro, gris, plata, ocre); barniz mate.",
        "Cinta de tela o tela fina para el refuerzo interior de la unión cuerpo–puño.",
        "Elástico, velcro o cinta para el cierre interior (fuera del PDF).",
    ]
    for m in mats:
        p = Paragraph(m, ST["buls"], bulletText="•")
        _, h = p.wrap(colw * CM, 1000)
        p.drawOn(c, 1.6 * CM, y * CM - h)
        y -= h / CM + 0.1
    ytop2 = PH - 2.2
    x2 = 10.7
    y2 = para(c, "Herramientas", x2, ytop2, 8.7, "h2")
    tools = [
        "Cúter o bisturí con cuchillas nuevas (9 mm, de partir).",
        "Regla metálica, tapete de corte, lápiz o rotulador fino, punzón.",
        "Pistola de calor (con temperatura regulable si es posible).",
        "Botella o tubo cónico/cilíndrico (Ø 6–9 cm) para formar; cinta de carrocero.",
        "Lija 120 y 240, o herramienta rotativa con fresa de lijado.",
        "Pirograbador o cúter para las líneas de grabado (opcional).",
        "Pinceles, esponja, trapos; guantes y mascarilla (ventilación con el cemento).",
    ]
    for m in tools:
        p = Paragraph(m, ST["buls"], bulletText="•")
        _, h = p.wrap(8.7 * CM, 1000)
        p.drawOn(c, x2 * CM, y2 * CM - h)
        y2 -= h / CM + 0.1
    y = min(y, y2) - 0.4
    y = para(c, "Lista de piezas (todas simétricas: no hace falta espejar)", 1.6, y, 17.8, "h2")
    s = lambda k: G.size(G.layout_p3()[k]) if False else None
    bw, bh = C["bw"], C["bh"]
    pw, ph = G.size(G.plate())
    sbw, sbh = G.size(G.sheath_base())
    scw, sch = G.size(G.sheath_cover())
    data = [
        ["ID", "Pieza", "Cant.", "Tamaño aprox. (cm)", "Hoja", "Uso"],
        ["A", "Cuerpo del brazal", "2", f"{f1(bw)} × {f1(bh)}", "P1 (2 hojas)", "Cono principal"],
        ["B1", "Cincha superior", "2", f"{f1(G.strap(G.STRAP_S[0])[1])} × 1,5", "P2", "Encima del cuerpo, s = 3,0 cm"],
        ["B2", "Cincha central", "2", f"{f1(G.strap(G.STRAP_S[1])[1])} × 1,5", "P2", "Encima del cuerpo, s = 8,75 cm"],
        ["B3", "Cincha inferior", "2", f"{f1(G.strap(G.STRAP_S[2])[1])} × 1,5", "P2", "Encima del cuerpo, s = 14,5 cm"],
        ["C", "Ribete superior (arco)", "2", f"arco {f1(C['L1'])} × 1,0", "P2", "Borde superior del cuerpo"],
        ["D", "Ribete inferior (arco)", "2", f"arco {f1((R2 + 1) * TH)} × 1,0", "P4", "Borde inferior del cuerpo"],
        ["E", "Puño", "2", f"arco {f1(C['L2'])} / {f1((R2 - 3) * TH)} × 3,0", "P3", "Prolonga el cuerpo hasta la muñeca"],
        ["F", "Placa de mano", "2", f"{f1(ph)} × {f1(pw)}", "P3", "Dorso de la mano"],
        ["G1", "Funda – base", "1*", f"{f1(sbh)} × {f1(sbw)}", "P4", "Funda de la daga"],
        ["G2", "Funda – tapa", "1*", f"{f1(sch)} × {f1(scw)}", "P4", "Capa superior de la funda"],
        ["H1", "Empuñadura", "2*", "4,6 × 1,4", "P4", "2 capas = 10 mm"],
        ["H2", "Guarda", "2*", "3,0 × 0,8", "P4", "2 capas"],
        ["H3", "Pomo", "2*", "Ø 1,5", "P4", "2 capas"],
    ]
    y = table(c, data, 1.6, y - 0.1, [1.0, 4.1, 1.2, 3.8, 2.4, 5.3], pad=2.2)
    y = para(c, "* Cantidades para <b>una</b> daga (un solo brazal). Hojas de goma EVA necesarias: P1 ×2 + P2 + P3 + P4 = <b>5 hojas</b>.", 1.6, y - 0.15, 17.8, "xs")
    y = para(c, "Distribución de las hojas de patrón", 1.6, y - 0.4, 17.8, "h2")
    data2 = [
        ["Hoja", "Contenido", "Hojas de EVA"],
        ["P1", "A · cuerpo del brazal (trazar dos veces, una por brazal)", "2"],
        ["P2", "B1–B3 ×2 (6 cinchas) + C ×2 (ribetes superiores)", "1"],
        ["P3", "E ×2 (puños) + F ×2 (placas de mano)", "1"],
        ["P4", "D ×2 (ribetes inferiores) + G1, G2, H1–H3 (daga)", "1"],
    ]
    y = table(c, data2, 1.6, y - 0.1, [1.3, 13.0, 3.5], pad=2.4)
    assert y > 1.8, f"pág 4 desborda: {y}"


# ================================================================== PÁG 5: orden de montaje
def page_assembly(c):
    chrome(c, 5, "PARTE I · ANÁLISIS", "Orden de montaje por capas")
    y = para(c, "Cada tarjeta añade una capa a la anterior (la pieza nueva lleva contorno naranja). Sigue este orden para que las uniones queden ocultas "
                "y no tengas que pegar sobre superficies ya pintadas o selladas.", 1.6, PH - 2.2, 17.8, "b")
    cards = [
        ("1 · Cuerpo (A)", "body", "Corta A, bisela el borde inferior y termoforma el cono. Comprueba el cierre (≈ 2 cm) con cinta de carrocero.", "A"),
        ("2 · Ribetes (C, D)", "rim", "Pega C arriba y D abajo, por la cara exterior y a ras del borde. Bisela sus extremos para que mueran suavemente.", "C, D"),
        ("3 · Puño (E)", "cuff", "Unión a tope con bisel de 45° al borde inferior del cuerpo; refuerza por dentro con tira de tela.", "E"),
        ("4 · Cinchas (B1–B3)", "straps", "Sigue las guías de P1. Empieza a 0,5 cm del borde izquierdo y deja ~1,5 cm de punta libre en el borde de cierre.", "B1–B3"),
        ("5 · Placa de mano (F)", "plate", "Curva a lo ancho y pega los 1,5 cm superiores sobre el puño, centrada. Graba la línea a 0,6 cm del borde.", "F"),
        ("6 · Daga (G, H)", "dagger", "Une por pares G1+G2, H1, H2 y H3; pega sobre las cinchas, con la funda hacia el codo (solo un brazal).", "G1, G2, H1–H3"),
    ]
    x0, y0 = 1.6, y - 0.4
    cw, ch = 5.8, 9.6
    for i, (title, layer, desc, ids) in enumerate(cards):
        col, row = i % 3, i // 3
        x = x0 + col * (cw + 0.3)
        yt = y0 - row * (ch + 0.35)
        box(c, x, yt - ch, cw, ch)
        text(c, title, x + 0.3, yt - 0.6, 9.5, "LS-B", BROWN)
        draw_bracer(c, x + cw / 2 - 0.2, yt - 1.1, 0.2, upto=i + 1, hl=layer)
        p = Paragraph(desc, ST["s"])
        _, h = p.wrap((cw - 0.6) * CM, 1000)
        p.drawOn(c, (x + 0.3) * CM, (yt - ch + 0.3) * CM + 0)
    yb = y0 - 2 * ch - 0.35 - 0.3
    yb = para(c, "Reglas de oro del montaje", 1.6, yb, 17.8, "h2")
    rules = [
        "Pega siempre de dentro hacia fuera: primero lo que quedará debajo.",
        "Prueba en seco (sin pegar) cada capa antes de aplicar cemento: el contacto no se puede recolocar.",
        "Lija y limpia el polvo de las zonas de unión; una cara quemada por el calor o el cúter pega peor.",
        "Sella y pinta <b>después</b> de montar las capas planas, pero antes de añadir hebillas y remaches.",
    ]
    for r in rules:
        p = Paragraph(r, ST["bul"], bulletText="•")
        _, h = p.wrap(17.8 * CM, 1000)
        p.drawOn(c, 1.6 * CM, yb * CM - h)
        yb -= h / CM + 0.1
    assert yb > 1.8, f"pág 5 desborda: {yb}"


# ================================================================== HOJAS DE PATRONES
def ruler(c):
    x0, y = 18.7, 19.85
    c.saveState()
    c.setStrokeColor(DARK)
    c.setLineWidth(0.8)
    c.line(x0 * CM, y * CM, (x0 + 10) * CM, y * CM)
    for i in range(11):
        h = 0.32 if i % 5 == 0 else 0.18
        c.line((x0 + i) * CM, y * CM, (x0 + i) * CM, (y + h) * CM)
    c.restoreState()
    text(c, "0", x0, y - 0.32, 6.5, "LS", DARK, "c")
    text(c, "5", x0 + 5, y - 0.32, 6.5, "LS", DARK, "c")
    text(c, "10 cm", x0 + 10, y - 0.32, 6.5, "LS-B", DARK, "c")
    text(c, "Regla de control: debe medir exactamente 10,0 cm", x0 - 0.3, y + 0.05, 7, "LS", DARK, "r")


def pattern_header(c, code, title):
    text(c, f"HOJA {code} · {title}", 0.9, 19.95, 9.5, "LS-B", BROWN)
    text(c, "Imprimir al 100 % (tamaño real) · A4 horizontal · EVA 5 mm · línea gruesa = corte", 0.9, 19.55 if code != "P1" else 20.45, 6.8, "LS", GRAYT)
    ruler(c)


def label_in(c, poly, s, size=7, color=DARK, font="LS-B", dy=0.0, dx=0.0):
    p = poly.representative_point()
    text(c, s, p.x + dx, p.y - size / 2 / CM * 0.7 + dy, size, font, color, "c")


def page_p1(c):
    c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
    items = G.layout_p1()
    A = items["A"]
    pattern_header(c, "P1", "A · CUERPO DEL BRAZAL ×2")
    ob = G.body()
    dx, dy = A.bounds[0] - ob.bounds[0], A.bounds[1] - ob.bounds[1]

    def pt(px, py):
        return (px + dx, py + dy)

    draw_poly(c, A, fill=None, stroke=DARK, lw=1.6)
    # guías de cinchas
    for k, s in enumerate(G.STRAP_S, 1):
        for off in (-G.STRAP_W / 2, G.STRAP_W / 2):
            r = R1 - s + off
            a_start = -TH / 2 + 0.5 / r
            draw_line(c, arc_line(r, a_start, TH / 2, 60), GUIDE, 0.8, (4, 2.5), dx, dy)
        r = R1 - s
        text(c, f"B{k}", pt(r * math.sin(-TH / 2 + 0.75 / r + 0.02), r * math.cos(-TH / 2 + 0.75 / r + 0.02))[0] + 0.55,
             pt(0, r)[1] - 0.12 - (R1 - s - (R1 - s)) , 7.5, "LS-B", GUIDE, "c") if False else None
        lx, ly = pt(r * math.sin(-TH / 2 + 1.6 / r), r * math.cos(-TH / 2 + 1.6 / r))
        text(c, f"cincha B{k}", lx, ly - 0.1, 7, "LS-B", GUIDE, "l")
    # guías de ribetes
    for r, nm in ((R1 - G.RIM_W, "ribete C (arriba)"), (R2 + G.RIM_W, "ribete D (abajo)")):
        draw_line(c, arc_line(r, -TH / 2, TH / 2, 80), colors.HexColor("#8E8E8E"), 0.6, (1.5, 2.5), dx, dy)
    # daga
    top = R1 - 1.6
    parts = [
        affinity.translate(G.sheath_base(), 0, top - 7.5 - 0.0 + 0.0),
    ]
    y_top = top
    sheath = affinity.translate(G.sheath_base(), 0, y_top - G.size(G.sheath_base())[1])
    gy = sheath.bounds[1] - 0.8
    guard = affinity.translate(G.guard(), 0, gy)
    gripy = guard.bounds[1] - 4.6
    grip = affinity.translate(G.grip(), 0, gripy)
    pom = affinity.translate(G.pommel(), 0, grip.bounds[1] - 0.75)
    for p_ in (sheath, guard, grip, pom):
        draw_poly(c, p_, fill=None, stroke=ACCENT, lw=1.0, dash=(5, 3), ox=dx, oy=dy)
    text(c, "GUÍA DE LA DAGA", *pt(0, sheath.bounds[3] + 0.0), 6.5, "LS-B", ACCENT, "c") if False else None
    tx, ty = pt(0.9, sheath.bounds[3] - 0.45)
    text(c, "guía daga (solo 1 brazal)", tx, ty, 6.5, "LS-B", ACCENT, "l")
    # eje central
    draw_line(c, [(0, R2 + 0.0), (0, R1)], colors.HexColor("#B0B0B0"), 0.5, (6, 3, 1, 3), dx, dy)
    # etiquetas de bordes
    for sign, ang in ((-1, math.degrees(math.atan2(math.cos(TH / 2), -math.sin(TH / 2)))), (1, math.degrees(math.atan2(math.cos(TH / 2), math.sin(TH / 2))))):
        r_mid = (R1 + R2) / 2 + (0.0 if sign < 0 else 0.0)
        a = sign * TH / 2
        # punto sobre el borde, desplazado 0,45 cm hacia el interior
        ex, ey = r_mid * math.sin(a), r_mid * math.cos(a)
        nx, ny = -sign * math.cos(a), sign * math.sin(a)   # normal hacia el interior (aprox.)
        nx, ny = (math.cos(TH / 2) * (-sign) * -1, 0)  # placeholder, se recalcula abajo
        # normal interior: perpendicular a la dirección del borde
        d = (math.sin(a), math.cos(a))
        n = (d[1], -d[0]) if sign < 0 else (-d[1], d[0])
        X, Y = pt(ex + n[0] * 0.5, ey + n[1] * 0.5)
        # leer de abajo hacia arriba en el borde izquierdo, de arriba hacia abajo en el derecho
        if sign < 0:
            text(c, "BORDE DE CIERRE · lado interior del antebrazo", X, Y, 6.8, "LS-I", GRAYT, "c", ang - 0)
        else:
            text(c, "BORDE DE CIERRE · lado interior del antebrazo", X, Y, 6.8, "LS-I", GRAYT, "c", ang - 180 + 0)
    # textos
    tx, ty = pt(0, R1 - 0.62)
    text(c, f"↑ CODO · borde superior · arco {f1(C['L1'])} cm", tx, ty, 7.5, "LS-B", DARK, "c")
    tx, ty = pt(0, R2 + 0.2)
    text(c, f"↓ MUÑECA · borde inferior · arco {f1(C['L2'])} cm", tx, ty, 7.5, "LS-B", DARK, "c")
    mx, my = pt(-6.6, R1 - 5.9)
    text(c, "A · CUERPO DEL BRAZAL", mx, my + 0.35, 12, "LS-B", BROWN, "c")
    text(c, "cortar 2 piezas (una por brazal)", mx, my - 0.25, 8, "LS", DARK, "c")
    text(c, f"largo {f1(G.H)} cm · {f1(math.degrees(TH))}° de abertura", mx, my - 0.8, 7.5, "LS", GRAYT, "c")
    nx_, ny_ = pt(7.0, R1 - 5.9)
    text(c, "Termoformar en cono: borde ancho", nx_, ny_ + 0.5, 7.5, "LS", DARK, "c")
    text(c, "hacia el codo. Los 2 bordes rectos", nx_, ny_ + 0.05, 7.5, "LS", DARK, "c")
    text(c, "quedan a ~2 cm al cerrar.", nx_, ny_ - 0.4, 7.5, "LS", DARK, "c")
    text(c, "Líneas azules: guía de cinchas B1–B3 · naranja: guía de daga", nx_, ny_ - 1.0, 6.5, "LS-I", GUIDE, "c")


def page_p2(c):
    c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
    items = G.layout_p2()
    pattern_header(c, "P2", "B · CINCHAS ×6 + C · RIBETES SUPERIORES ×2")
    for k, poly in items.items():
        draw_poly(c, poly, stroke=DARK, lw=1.4)
    for k, poly in items.items():
        if k.startswith("B"):
            ln = G.size(poly)[0]
            n = k.split(".")[0]
            minx, miny, maxx, maxy = poly.bounds
            text(c, f"{n} · cincha {k.split('.')[1]}/2 · {f1(ln)} × 1,5 cm  (punta a la derecha)", minx + 0.5, miny + 0.55, 7, "LS-B", DARK, "l")
        else:
            label_in(c, poly, k, 7)
    # nota
    box(c, 15.0, 17.7 - 0.3, 13.0, 1.7, fill=CREAM)
    y = 17.55 + 0.1
    lines = ["C1, C2 · ribetes superiores en arco (1,0 cm de ancho): van a ras del borde superior del cuerpo A.",
             "B1 (la más larga) va arriba; B3 (la más corta) abajo. Bisela los cantos y la punta.",
             "Cada cincha: 0,5 cm de margen en el borde izquierdo y ~1,5 cm de punta libre a la derecha."]
    for i, l in enumerate(lines):
        text(c, l, 15.2, 18.85 - i * 0.45, 6.8, "LS", DARK)


def page_p3(c):
    c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
    items = G.layout_p3()
    pattern_header(c, "P3", "E · PUÑOS ×2 + F · PLACAS DE MANO ×2")
    for k, poly in items.items():
        draw_poly(c, poly, stroke=DARK, lw=1.4)
    for k in ("E1", "E2"):
        label_in(c, items[k], f"{k} · PUÑO", 8, dy=0.0)
        minx, miny, maxx, maxy = items[k].bounds
    # guías placa: rotadas
    pl = G.plate()
    plr = affinity.rotate(pl, 90, origin=(0, 0))
    for k in ("F1", "F2"):
        P = items[k]
        ox, oy = P.bounds[0] - plr.bounds[0], P.bounds[1] - plr.bounds[1]
        inner = affinity.rotate(pl.buffer(-0.6), 90, origin=(0, 0))
        draw_poly(c, inner, stroke=GUIDE, lw=0.8, dash=(3, 2.5), ox=ox, oy=oy)
        tabline = affinity.rotate(LineString([(-4.2, -G.PLATE_TAB), (4.2, -G.PLATE_TAB)]), 90, origin=(0, 0))
        draw_line(c, list(tabline.coords), ACCENT, 0.9, (4, 2), ox, oy)
        label_in(c, P, f"{k} · PLACA DE MANO", 8, dx=0.2, dy=0.35)
        mx, my = P.representative_point().x + 0.2, P.representative_point().y
        text(c, "línea de grabado a 0,6 cm", mx, my - 0.15, 6.5, "LS-I", GUIDE, "c")
        text(c, f"{f1(G.size(P)[0])} × {f1(G.size(P)[1])} cm", mx, my - 0.65, 6.5, "LS", GRAYT, "c")
        # marcar zona de pegado
        bx = P.bounds[0]
        text(c, "← muñeca", bx + 0.25, P.bounds[1] - 0.0 + G.size(P)[1] + 0.12, 6.3, "LS-I", GRAYT, "l")
        text(c, "nudillos →", P.bounds[2] - 0.25, P.bounds[1] - 0.0 + G.size(P)[1] + 0.12, 6.3, "LS-I", GRAYT, "r")
        text(c, "pegar 1,5 cm", bx + 0.75, P.bounds[1] + 0.25, 6, "LS-B", ACCENT, "c", 90) if False else None
    box(c, 21.5, 0.9 + 0.0, 6.9, 8.0, fill=CREAM) if False else None
    # notas (zona libre derecha del puño)
    nx = 1.2 + G.size(items["E1"])[0] + 0.8
    ny = items["E2"].bounds[3] - 0.2
    lines = ["E · puño (cono continuo del cuerpo):",
             f"  arco sup. {f1(C['L2'])} cm · arco inf. {f1((R2 - 3) * TH)} cm · alto 3,0 cm.",
             "  Une a tope (bisel 45°) con el borde inferior de A.",
             "F · placa: curva a lo ancho sobre el dorso.",
             "  Línea naranja = zona de 1,5 cm que se pega sobre E.",
             "  Línea azul = grabado decorativo (opcional)."]
    for i, l in enumerate(lines):
        text(c, l, nx, ny - i * 0.42, 6.8, "LS-B" if not l.startswith(" ") else "LS", DARK)


def page_p4(c):
    c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
    items = G.layout_p4()
    pattern_header(c, "P4", "D · RIBETES INFERIORES ×2 + DAGA (G, H)")
    for k, poly in items.items():
        draw_poly(c, poly, stroke=DARK, lw=1.4)
    for k, poly in items.items():
        if k.startswith("D"):
            label_in(c, poly, k, 7)
        elif k in ("G1", "G2"):
            p = poly.representative_point()
            text(c, k, p.x, p.y - 0.1, 8, "LS-B", DARK, "c")
        else:
            p = poly.representative_point()
            text(c, k.split(".")[0], p.x, p.y - 0.1, 6.5, "LS-B", DARK, "c")
    # grabado de la tapa
    inner = items["G2"].buffer(-0.25)
    if not inner.is_empty:
        draw_poly(c, inner, stroke=GUIDE, lw=0.7, dash=(2, 2))
    # cuadro de notas (zona libre superior)
    box(c, 1.0, 13.9, 27.6, 5.2, fill=CREAM)
    lines = [("D1, D2 · ribetes inferiores (arco, 1,0 cm de ancho): a ras del borde inferior del cuerpo, cara exterior.", False),
             ("Daga fija (sin hoja), solo para un brazal:", True),
             ("G1 · base de la funda (cortar 1) → se pega sobre las cinchas.", False),
             ("G2 · tapa de la funda (cortar 1) → se pega centrada sobre G1; deja un escalón de 3 mm. Línea azul = costura grabada.", False),
             ("H1 · empuñadura: pegar H1.1 + H1.2 (10 mm de grosor) y marcar el cordón con cortes oblicuos cada ~4 mm.", False),
             ("H2 · guarda: pegar H2.1 + H2.2 y biselar los extremos.   H3 · pomo: pegar H3.1 + H3.2 y redondear con lija.", False),
             ("Orden de abajo arriba (hacia el codo): pomo → empuñadura → guarda → funda.", True),
             ("La funda apunta al codo; la posición exacta está en la guía naranja de la hoja P1.", False)]
    yy = 18.4
    for t_, b_ in lines:
        text(c, t_, 1.4, yy, 7.2, "LS-B" if b_ else "LS", DARK)
        yy -= 0.58



# ------------------------------------------------------------------ figuras del tutorial
def figbox(c, x, yt, w, h, title):
    box(c, x, yt - h, w, h, fill=colors.white)
    text(c, title, x + w / 2, yt - 0.5, 8, "LS-B", BROWN, "c")


def _poly(c, pts, fill, stroke=DARK, lw=0.7):
    p = c.beginPath()
    for i, (X, Y) in enumerate(pts):
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    p.close()
    c.saveState()
    c.setFillColor(fill)
    c.setStrokeColor(stroke)
    c.setLineWidth(lw)
    c.drawPath(p, stroke=1, fill=1)
    c.restoreState()


def fig_cut(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Corte: cuchilla a 90°")
    y0 = yt - 3.0
    _poly(c, [(x + 0.7, y0), (x + 5.0, y0), (x + 5.0, y0 + 0.9), (x + 0.7, y0 + 0.9)], LEATHER)
    cx = x + 2.9
    _poly(c, [(cx - 0.18, y0 + 1.9), (cx + 0.18, y0 + 1.9), (cx + 0.02, y0 + 0.9), (cx - 0.02, y0 + 0.9)], STEEL)
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(0.8)
    c.line((cx + 0.35) * CM, y0 * CM, (cx + 0.35) * CM, (y0 + 0.9) * CM)
    c.line((cx + 0.35) * CM, y0 * CM, (cx + 0.85) * CM, y0 * CM)
    c.restoreState()
    text(c, "90°", cx + 0.55, y0 + 0.2, 7, "LS-B", ACCENT)
    text(c, "2–3 pasadas suaves, nunca una fuerte", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_bevel(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Unión a tope con bisel de 45°")
    y0 = yt - 2.7
    _poly(c, [(x + 0.4, y0), (x + 3.0, y0), (x + 2.1, y0 + 0.9), (x + 0.4, y0 + 0.9)], BROWN)
    _poly(c, [(x + 3.0, y0), (x + 5.3, y0), (x + 5.3, y0 + 0.9), (x + 2.1, y0 + 0.9)], COL["cuff"])
    _poly(c, [(x + 1.9, y0 - 0.15), (x + 3.7, y0 - 0.15), (x + 3.7, y0), (x + 1.9, y0)], colors.HexColor("#9AA0A6"), lw=0.4)
    text(c, "cuerpo A", x + 1.1, y0 + 0.28, 6.5, "LS-B", colors.white, "c")
    text(c, "puño E", x + 4.4, y0 + 0.28, 6.5, "LS-B", colors.white, "c")
    text(c, "tira de tela por dentro", x + 2.8, y0 - 0.5, 6.5, "LS-I", GRAYT, "c")
    text(c, "pegar y presionar", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_strap(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Cincha sobre el cuerpo")
    y0 = yt - 3.0
    _poly(c, [(x + 0.5, y0), (x + 4.1, y0), (x + 4.1, y0 + 2.0), (x + 0.5, y0 + 2.0)], BROWN)
    _poly(c, [(x + 0.9, y0 + 0.6), (x + 4.3, y0 + 0.6), (x + 5.0, y0 + 0.95), (x + 4.3, y0 + 1.3), (x + 0.9, y0 + 1.3)], COL["strap"])
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(0.7)
    c.line((x + 0.5) * CM, (y0 + 0.35) * CM, (x + 0.9) * CM, (y0 + 0.35) * CM)
    c.line((x + 4.1) * CM, (y0 + 0.35) * CM, (x + 5.0) * CM, (y0 + 0.35) * CM)
    c.restoreState()
    text(c, "0,5", x + 0.7, y0 + 0.1, 6.5, "LS-B", ACCENT, "c")
    text(c, "1,5 libre", x + 4.55, y0 + 0.1, 6.5, "LS-B", ACCENT, "c")
    text(c, "borde de cierre ↓", x + 4.1, y0 + 2.12, 6.3, "LS-I", GRAYT, "r")
    text(c, "empieza dentro, termina saliendo", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_ring(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Vista superior: hueco de cierre")
    cx, cy, r = x + w / 2, yt - 1.8, 1.2
    gap = 2.0 / (G.TOP_SKIN / (2 * math.pi) + 0.5)
    a0, a1 = -math.pi / 2 + gap / 2, -math.pi / 2 + 2 * math.pi - gap / 2
    pts = [(cx + r * math.cos(a0 + (a1 - a0) * i / 60), cy + r * math.sin(a0 + (a1 - a0) * i / 60)) for i in range(61)]
    c.saveState(); c.setStrokeColor(BROWN); c.setLineWidth(5); c.setLineCap(1)
    p = c.beginPath(); p.moveTo(pts[0][0] * CM, pts[0][1] * CM)
    for X, Y in pts[1:]:
        p.lineTo(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.setStrokeColor(colors.HexColor("#E0B88F")); c.setLineWidth(0.8); c.setDash(2, 2)
    c.circle(cx * CM, cy * CM, (r - 0.45) * CM, stroke=1, fill=0)
    c.restoreState()
    text(c, "antebrazo", cx, cy - 0.1, 6.5, "LS-I", GRAYT, "c")
    text(c, "hueco ≈ 2 cm", cx, cy - r - 0.5, 6.8, "LS-B", ACCENT, "c")
    text(c, "(lado interior del antebrazo)", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_heat(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Termoformado")
    cy = yt - 1.9
    _poly(c, [(x + 0.4, cy - 0.3), (x + 1.5, cy - 0.3), (x + 1.5, cy + 0.3), (x + 0.4, cy + 0.3)], colors.HexColor("#555555"))
    _poly(c, [(x + 1.5, cy - 0.15), (x + 2.0, cy - 0.12), (x + 2.0, cy + 0.12), (x + 1.5, cy + 0.15)], colors.HexColor("#888888"))
    _poly(c, [(x + 2.0, cy - 0.1), (x + 3.7, cy - 0.7), (x + 3.7, cy + 0.7), (x + 2.0, cy + 0.1)], colors.HexColor("#FBD9B5"), stroke=ACCENT, lw=0.4)
    c.saveState(); c.setStrokeColor(BROWN); c.setLineWidth(5); c.setLineCap(1)
    p = c.beginPath()
    for i in range(41):
        a = -0.8 + 1.6 * i / 40
        X, Y = x + 3.9 + 0.85 * math.cos(a) - 0.5 + 0.45, cy + 1.0 * math.sin(a)
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    c.drawPath(p, stroke=1, fill=0)
    c.setStrokeColor(ACCENT); c.setLineWidth(0.7)
    c.line((x + 2.0) * CM, (cy - 0.95) * CM, (x + 3.8) * CM, (cy - 0.95) * CM)
    c.restoreState()
    text(c, "15–20 cm", x + 2.9, cy - 1.3, 6.8, "LS-B", ACCENT, "c")
    text(c, "mover siempre · sujetar hasta enfriar", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_overlap(c, x, yt, w=5.7, h=4.3):
    figbox(c, x, yt, w, h, "Placa sobre el puño (vista lateral)")
    y0 = yt - 3.0
    _poly(c, [(x + 0.4, y0), (x + 3.2, y0), (x + 3.2, y0 + 0.8), (x + 0.4, y0 + 0.8)], COL["cuff"])
    _poly(c, [(x + 2.0, y0 + 0.8), (x + 5.3, y0 + 0.8), (x + 5.3, y0 + 1.6), (x + 2.0, y0 + 1.6)], COL["plate"])
    c.saveState(); c.setStrokeColor(ACCENT); c.setLineWidth(0.7)
    c.line((x + 2.0) * CM, (y0 + 1.95) * CM, (x + 3.2) * CM, (y0 + 1.95) * CM)
    c.restoreState()
    text(c, "1,5 cm", x + 2.6, y0 + 2.05, 6.8, "LS-B", ACCENT, "c")
    text(c, "puño E", x + 1.2, y0 + 0.25, 6.5, "LS-B", colors.white, "c")
    text(c, "placa F", x + 4.2, y0 + 1.0, 6.5, "LS-B", colors.white, "c")
    text(c, "la zona marcada en naranja en P3", x + w / 2, yt - 3.85, 6.8, "LS", DARK, "c")


def fig_row(c, yt, figs):
    x = 1.6
    for fn in figs:
        fn(c, x, yt)
        x += 5.7 + 0.35
    return yt - 4.3



def checklist(c, x, ytop, w, title, items, cols=2):
    rows = (len(items) + cols - 1) // cols
    h = 0.95 + rows * 0.62
    box(c, x, ytop - h, w, h, fill=colors.white)
    text(c, title, x + 0.35, ytop - 0.65, 9.5, "LS-B", BROWN)
    cw = (w - 0.7) / cols
    for i, it in enumerate(items):
        col, row = i // rows, i % rows
        xx, yy = x + 0.35 + col * cw, ytop - 1.3 - row * 0.62
        c.saveState(); c.setStrokeColor(DARK); c.setLineWidth(0.7)
        c.rect(xx * CM, (yy - 0.05) * CM, 0.3 * CM, 0.3 * CM, stroke=1, fill=0)
        c.restoreState()
        text(c, it, xx + 0.5, yy + 0.02, 8, "LS", DARK)
    return ytop - h


# ================================================================== TUTORIAL
def steps_block(c, y, steps, x=1.6, w=17.8):
    for tag, title, body in steps:
        circ_y = y - 0.32
        c.setFillColor(ACCENT)
        c.circle((x + 0.35) * CM, circ_y * CM - 0.3 * CM / 2, 0.38 * CM, stroke=0, fill=1)
        c.setFillColor(colors.white)
        c.setFont("LS-B", 9)
        c.drawCentredString((x + 0.35) * CM, circ_y * CM - 0.3 * CM / 2 - 3.2, str(tag))
        p = Paragraph(f"<b>{title}.</b> {body}", ST["step"])
        _, h = p.wrap((w - 1.2) * CM, 1000)
        p.drawOn(c, (x + 1.1) * CM, y * CM - h)
        y -= max(h / CM, 0.8) + 0.28
    return y


def page_tut1(c):
    chrome(c, 10, "PARTE III · TUTORIAL 1/3", "Preparación y corte")
    y = PH - 2.2
    steps = [
        (1, "Prueba de talla en papel",
         "Imprime la hoja P1, recorta el cuerpo y envuélvelo alrededor del antebrazo con la manga puesta, con el borde ancho hacia el codo. "
         "Los dos bordes rectos deben quedar separados unos <b>2 cm</b>, en el lado interior. Si te queda justo o sobra mucho, mira la tabla de ajuste "
         "(pág. 13) <i>antes</i> de cortar goma."),
        (2, "Imprime a escala real",
         "Todas las hojas P1–P4 son A4 horizontales. Imprime en <b>«tamaño real» / 100 %</b> y desactiva «ajustar a página» y «reducir al área imprimible». "
         "Mide la regla de control (10 cm): si no mide exactamente 10,0 cm, corrige la escala y vuelve a imprimir."),
        (3, "Recorta las plantillas",
         "Recorta cada pieza por la línea negra gruesa. Las líneas discontinuas (azules, naranjas, grises) y los textos son guías: no se cortan. "
         "Si vas a repetir el proyecto, pega las hojas sobre cartulina fina para tener plantillas reutilizables."),
        (4, "Traza sobre la goma",
         "Coloca la plantilla sobre la cara lisa de la goma EVA y repasa el contorno con rotulador fino (o punzón, que no mancha). Todas las piezas son simétricas, "
         "no hace falta espejar. Cada hoja de patrón corresponde a una hoja A4 de goma: P1 se traza dos veces. Transfiere también las guías con un punzón, "
         "apretando poco."),
        (5, "Corta",
         "Usa una cuchilla recién partida y una regla metálica para las rectas. Haz <b>varias pasadas suaves</b> en lugar de una fuerte; con 5 mm suelen bastar 2–3. "
         "Mantén la cuchilla a 90° para que el canto salga vertical y limpio. En curvas gira la goma, no la mano. En piezas pequeñas (guarda, pomo) corta primero el contorno "
         "con pasadas cortas y ve girando. Corta siempre alejando la mano de la cuchilla."),
        (6, "Bisela los bordes de unión",
         "Con el cúter casi plano (o lija 120) rebaja a 45° el borde inferior del cuerpo A y el superior del puño E (se unirán a tope), los extremos de los ribetes C y D y los cantos de las "
         "cinchas B. <b>No biseles</b> los bordes rectos de cierre del cuerpo: deben quedar firmes."),
    ]
    y = steps_block(c, y, steps)
    y = fig_row(c, y - 0.1, [fig_cut, fig_bevel, fig_strap]) - 0.35
    box(c, 1.6, y - 3.5, 17.8, 3.5, fill=colors.HexColor("#FFF6E8"), stroke=ACCENT)
    text(c, "Consejos de profesional", 2.0, y - 0.65, 10, "LS-B", ACCENT)
    tips = [
        "Antes de gastar goma buena, haz un modelo completo en cartulina o EVA fina: detecta errores de talla en 15 minutos.",
        "La cuchilla se desafila rápido con EVA densa: cámbiala o parte la punta al notar que «arrastra» el canto.",
        "Si el canto queda «peludo», pasa una llama rápida de pistola de calor a 15 cm y lija suave: se sella y se alisa.",
        "Marca con lápiz el codo y la muñeca en cada pieza cortada para no mezclar bordes al montar.",
    ]
    yy = y - 0.95
    for t_ in tips:
        p = Paragraph(t_, ST["buls"], bulletText="•")
        _, h = p.wrap(16.8 * CM, 1000)
        p.drawOn(c, 2.0 * CM, yy * CM - h)
        yy -= h / CM + 0.1
    y = y - 3.5 - 0.4
    items = ["A · cuerpo ×2", "B1 · cincha ×2", "B2 · cincha ×2", "B3 · cincha ×2", "C · ribete sup. ×2", "D · ribete inf. ×2",
             "E · puño ×2", "F · placa ×2", "G1 · funda base ×1", "G2 · funda tapa ×1", "H1 · empuñadura ×2", "H2 · guarda ×2", "H3 · pomo ×2",
             "Guías transferidas", "Bordes biselados"]
    y = checklist(c, 1.6, y, 17.8, "Lista de control del corte", items, cols=3)
    assert y > 1.8, f"pág 10 desborda {y}"


def page_tut2(c):
    chrome(c, 11, "PARTE III · TUTORIAL 2/3", "Termoformado, pegado y montaje")
    y = PH - 2.2
    steps = [
        (7, "Termoforma el cuerpo (A) y el puño (E)",
         "Calienta con la pistola a temperatura media-baja, a 15–20 cm y <b>sin dejarla quieta</b>, durante unos 15–20 s por zona hasta que la goma se vuelva flexible y pierda algo de brillo. "
         "Enróllala sobre una botella o un tubo (Ø 6–9 cm; la parte ancha para el codo) con el borde ancho hacia arriba y sujétala con cinta de carrocero 1–2 min hasta que se enfríe. "
         "Repite hasta que el cono cierre dejando ~2 cm de hueco. Nunca te pongas la goma caliente: espera a que esté tibia para probarla."),
        (8, "Prueba en seco",
         "Ponte A + E sujetos con cinta y comprueba que puedes doblar el codo y cerrar el puño, y que el hueso de la muñeca pasa sin forzar. Si aprieta, vuelve a calentar y abre ligeramente el cono."),
        (9, "Pega con cemento de contacto",
         "Aplica una capa fina en ambas superficies (la goma absorbe: da una segunda capa si queda mate). Deja secar <b>5–10 min</b> hasta que no brille ni se pegue al dedo. "
         "Une con cuidado, <b>no se puede recolocar</b>, y presiona con la palma o un rodillo. Trabaja en sitio ventilado y con guantes."),
        (10, "Monta por capas (orden de la pág. 5)",
         "<b>a)</b> Une el puño E al cuerpo A: unión a tope con bisel de 45° y refuerzo interior con tira de tela de ~2 cm. "
         "<b>b)</b> Pega los ribetes C (arriba) y D (abajo) por la cara exterior y a ras del borde. "
         "<b>c)</b> Pega las cinchas B1–B3 sobre las guías azules de P1: empieza a 0,5 cm del borde izquierdo y deja la punta (~1,5 cm) libre más allá del borde de cierre; ahí irán las hebillas. "
         "<b>d)</b> Curva la placa F sobre una botella pequeña (Ø 5–6 cm) y pega su zona de 1,5 cm sobre el puño, centrada. "
         "<b>e)</b> Monta la daga fuera del brazal: G1+G2 (tapa sobre base, centrada), H1.1+H1.2, H2.1+H2.2 y H3.1+H3.2; pégala sobre las cinchas, con la funda hacia el codo, en la guía naranja."),
        (11, "Graba los detalles",
         "Con un pirograbador (punta de cuchilla) o con cúter y un golpe de calor, marca la línea de grabado de la placa (a 0,6 cm del borde), la costura de la tapa de la funda y "
         "los cortes oblicuos del cordón de la empuñadura (cada ~4 mm). Pasa rápido y sin apretar para no atravesar la goma."),
    ]
    y = steps_block(c, y, steps)
    y = fig_row(c, y - 0.1, [fig_heat, fig_ring, fig_overlap]) - 0.4
    y = para(c, "Tiempos orientativos para un par de braceras", 1.6, y, 17.8, "h3") - 0.1
    data = [["Fase", "Tiempo estimado", "Notas"],
            ["Prueba en papel, impresión y plantillas", "45 min", "Una sola vez; reutilizable"],
            ["Trazado, corte y biselado", "2–3 h", "Las curvas largas de A y C/D llevan más tiempo"],
            ["Termoformado y prueba en seco", "30–45 min", "Mejor con ayuda para sujetar con cinta"],
            ["Pegado y montaje por capas", "2 h + secados", "Respeta 5–10 min de secado del cemento en cada unión"],
            ["Sellado, pintura y barniz", "3–4 h + secados", "Reparte en dos sesiones (sellado un día, pintura otro)"]]
    y = table(c, data, 1.6, y, [6.2, 3.3, 8.3], pad=2.4)
    assert y > 1.8, f"pág 11 desborda {y}"


def page_tut3(c):
    chrome(c, 12, "PARTE III · TUTORIAL 3/3", "Sellado, pintura, cierre y soluciones")
    y = PH - 2.2
    steps = [
        (12, "Sella la goma",
         "Lija bordes y juntas (240), quita el polvo y aplica 2–3 capas finas de Plasti Dip o de vinílica/PVA diluida 1:1, dejando secar 20–30 min entre capas. "
         "Sin sellar, la pintura absorbe, se agrieta al flexionar y los poros se notan."),
        (13, "Imprimación y color",
         "Imprimación negra mate; después acrílico en capas finas con esponja o pincel (dos capas finas mejor que una gruesa). Usa la paleta de abajo."),
    ]
    y = steps_block(c, y, steps)
    data = [
        ["Elemento", "Color base", "Código", "Acabado"],
        ["Cuerpo A y placa F", "Marrón cuero", "#7B4A2A", "Dry brush #A9744A en aristas y zonas altas"],
        ["Ribetes C, D", "Marrón cálido claro", "#A9744A", "Un tono más claro que el cuerpo, con lavado oscuro en las juntas"],
        ["Cinchas B y puño E", "Negro grafito", "#2B2B2B", "Dry brush gris #55555A en aristas"],
        ["Funda G1/G2", "Gris oscuro", "#4A4A4D", "Línea de costura en gris claro #8A8A90"],
        ["Empuñadura H1", "Marrón oscuro", "#5A3820", "Cordón en negro, aristas en #7B4A2A"],
        ["Guarda H2 y pomo H3", "Plata metalizada", "#B8B8BC", "Base negra + plata; lavado negro en huecos"],
    ]
    y = table(c, data, 1.6, y - 0.1, [4.1, 3.6, 2.2, 7.9], pad=2.4)
    y -= 0.4
    steps2 = [
        (14, "Envejece",
         "Aplica un lavado de marrón muy oscuro + agua en juntas y bordes, retira el exceso con un trapo; pasa dry brush ocre #B98B5B en las aristas y da toques con esponja para simular roce. "
         "Menos es más: el cuero de Hipo está usado, no destrozado."),
        (15, "Protege",
         "Dos capas de barniz mate en spray (o satinado solo en la funda y la placa) a 25 cm de distancia."),
        (16, "Cierre y detalles (fuera del PDF)",
         "Cierra el brazal por el lado interior con velcro o elástico pegado por dentro en cada borde de cierre, o con hebillas y correa en las puntas de las cinchas. "
         "Aquí también van las anillas D, los remaches de la placa y el lazo del dedo (gris discontinuo en el dibujo de la pág. 2)."),
    ]
    y = steps_block(c, y, steps2)
    y = para(c, "Problemas frecuentes", 1.6, y - 0.05, 17.8, "h2")
    data2 = [
        ["Problema", "Causa probable", "Solución"],
        ["El brazal no cierra o aprieta", "Holgura o perímetro mal medidos", "Reduce el hueco de cierre o recalcula con la tabla de la pág. 13."],
        ["Se despegan las capas", "Poco secado del cemento o cara quemada", "Lija la zona, aplica dos capas y espera a que quede mate antes de unir."],
        ["Cantos «peludos»", "Cuchilla roma", "Cuchilla nueva, pasadas suaves y lijado fino."],
        ["La pintura se agrieta", "Sin sellar o capas gruesas", "Sella con Plasti Dip/PVA y pinta en capas finas."],
        ["Vuelve a abrirse tras el calor", "Poco calor o enfriado sin sujeción", "Recalienta y sujeta con cinta hasta que se enfríe."],
        ["Marcas o burbujas", "Demasiado calor en un punto", "Mueve la pistola constantemente y aléjala a 20 cm."],
    ]
    y = table(c, data2, 1.6, y - 0.1, [4.1, 4.6, 9.1], pad=2.3)
    y = checklist(c, 1.6, y - 0.5, 17.8, "Lista de control final",
                  ["Cierra con ~2 cm de hueco y sin rozar", "Uniones firmes (tirón suave)", "Superficie sellada sin brillos",
                   "Cinchas y ribetes alineados", "Daga fija y centrada", "Barniz seco antes de añadir herrajes"], cols=2)
    assert y > 1.8, f"pág 12 desborda {y}"


# ================================================================== PÁG 13: tallas
def page_sizes(c):
    chrome(c, 13, "PARTE III · ANEXO", "Adaptar el patrón a otra talla")
    y = para(c, "Los patrones de esta guía son para un antebrazo de 26 cm (arriba) y 18 cm (abajo). Mide el tuyo con una cinta flexible <b>sobre la manga</b> "
                "a 21,5 cm y a 4 cm de la muñeca, y calcula:", 1.6, PH - 2.2, 17.8, "b")
    box(c, 1.6, y - 2.9, 17.8, 2.7)
    para(c, "<b>L = perímetro + 1,5 (holgura) + 1,57 (grosor EVA) − 2,0 (hueco)</b><br/>"
            "<b>ángulo = (L1 − L2) / 17,5</b> &nbsp;·&nbsp; <b>R2 = L2 / ángulo</b> &nbsp;·&nbsp; <b>R1 = R2 + 17,5</b>",
         1.9, y - 0.45, 17.2, "b")
    para(c, "El largo del brazal (17,5 cm) y el hueco de cierre se pueden cambiar; recalcula con los mismos pasos. "
            "L1 se calcula con el perímetro de arriba y L2 con el de abajo.", 1.9, y - 1.7, 17.2, "xs")
    y -= 3.4
    y = para(c, "Tabla de tallas orientativas (diferencia constante de 8 cm entre arriba y abajo)", 1.6, y, 17.8, "h2")
    rows = [["Talla", "Perímetro arriba / abajo", "L1 (arco sup.)", "L2 (arco inf.)", "R1", "R2", "Sector (ancho × alto)", "¿Cabe en 1 hoja A4?"]]
    for name, top in (("S", 24.0), ("M (base)", 26.0), ("L", 28.0), ("XL", 30.0)):
        cc = G.cone(top, top - 8.0)
        fits = cc["bw"] <= 28.1 and cc["bh"] <= 19.4
        rows.append([name, f"{f1(top)} / {f1(top - 8)}", f"{f1(cc['L1'])}", f"{f1(cc['L2'])}", f"{f1(cc['R1'])}", f"{f1(cc['R2'])}",
                     f"{f1(cc['bw'])} × {f1(cc['bh'])}", "Sí" if fits else "No: dividir (ver abajo)"])
    y = table(c, rows, 1.6, y - 0.1, [1.7, 3.0, 2.0, 2.0, 1.6, 1.6, 3.0, 2.9], pad=2.6)
    y = para(c, "Si el sector no cabe en una hoja A4: <b>divide el cuerpo por el eje central</b> en dos mitades iguales. Cada mitad cabe en una hoja; une las mitades con un bisel de 45° "
                "y una tira interior de refuerzo. Las cinchas, el puño y los ribetes se adaptan igual (ver fórmula).", 1.6, y - 0.2, 17.8, "s")
    y -= 0.4
    y = para(c, "Cómo dibujar el sector a mano (compás casero)", 1.6, y, 17.8, "h2")
    # figura del sector
    sc = 0.19
    cx, cy = 6.0, y - 1.0 - 0.0
    # apex arriba para dibujar el esquema: usar coordenadas del sector con ápice abajo; lo giramos 180° para que cuelgue del ápice
    apex_x, apex_y = 5.6, y - 0.9
    def Q(r, a):
        return (apex_x + r * sc * math.sin(a), apex_y - r * sc * math.cos(a))
    pts = [Q(R1, -TH / 2 + TH * i / 60) for i in range(61)] + [Q(R2, TH / 2 - TH * i / 60) for i in range(61)]
    p = c.beginPath()
    for i, (X, Y) in enumerate(pts):
        (p.moveTo if i == 0 else p.lineTo)(X * CM, Y * CM)
    p.close()
    c.saveState()
    c.setFillColor(CREAM)
    c.setStrokeColor(DARK)
    c.setLineWidth(1.2)
    c.drawPath(p, stroke=1, fill=1)
    c.setDash(3, 2)
    c.setStrokeColor(GUIDE)
    c.setLineWidth(0.8)
    for a in (-TH / 2, TH / 2):
        X0, Y0 = Q(0, a)
        X1, Y1 = Q(R2, a)
        c.line(X0 * CM, Y0 * CM, X1 * CM, Y1 * CM)
    c.restoreState()
    ax, ay = apex_x, apex_y
    c.setFillColor(ACCENT)
    c.circle(ax * CM, ay * CM, 0.12 * CM, stroke=0, fill=1)
    text(c, "ápice (chincheta)", ax + 0.3, ay + 0.15, 7, "LS-B", ACCENT)
    text(c, "R2", *Q(R2 / 2, -TH / 2 - 0.0), 7.5, "LS-B", GUIDE) if False else None
    mx, my = Q(R2 * 0.6, -TH / 2)
    text(c, f"R2 = {f1(R2)} cm", mx - 0.2, my, 7, "LS-B", GUIDE, "r")
    mx, my = Q((R1 + R2) / 2, TH / 2)
    text(c, f"R1 = R2 + 17,5 = {f1(R1)} cm", mx + 0.3, my, 7, "LS-B", GUIDE, "l")
    mx, my = Q(R1, 0)
    text(c, f"L1 = {f1(C['L1'])} cm", mx, my - 0.5, 7.5, "LS-B", DARK, "c")
    mx, my = Q(R2, 0)
    text(c, f"L2 = {f1(C['L2'])} cm", mx, my + 0.3, 7.5, "LS-B", DARK, "c")
    text(c, f"ángulo = {f1(math.degrees(TH))}°", ax + 0.45, ay - R2 * sc * 0.55, 7.5, "LS-B", GUIDE, "l")
    steps = [
        "Fija una chincheta (ápice) en una hoja grande de papel o cartulina, en una esquina con espacio para unos 60 cm de radio.",
        "Ata un hilo inextensible a la chincheta y un lápiz a la distancia R1; traza el <b>arco superior</b>. Repite con la distancia R2 para el <b>arco inferior</b>.",
        "Marca sobre el arco superior la longitud L1 (cinta flexible apoyada en el arco) y une los extremos con el ápice: son los dos <b>bordes rectos</b>.",
        "Corta entre ambos arcos. Comprueba que el borde inferior mide L2 y que los bordes rectos miden 17,5 cm.",
    ]
    yy = y - 0.1
    xs = 11.0
    for i, s in enumerate(steps, 1):
        p = Paragraph(s, ParagraphStyle("n2", parent=ST["s"], leftIndent=12, bulletIndent=0, bulletFontName="LS-B"), bulletText=f"{i}.")
        _, h = p.wrap(8.4 * CM, 1000)
        p.drawOn(c, xs * CM, yy * CM - h)
        yy -= h / CM + 0.2
    ybottom = min(yy, apex_y - R1 * sc - 0.5)
    para(c, "Con esta misma fórmula puedes recalcular el puño (radios R2 y R2 − 3) y ajustar la placa de mano (ancho M8 de tu mano + 0,8 cm).", 1.6, ybottom - 0.2, 17.8, "xs")


# ================================================================== main
def build():
    c = canvas.Canvas(OUT, pagesize=(PW * CM, PH * CM))
    c.setTitle("Braceras de Hipo · Patrones EVA 5 mm y tutorial")
    c.setAuthor("Guía de cosplay")
    c.setSubject("Patrones a escala 1:1 en A4 y tutorial para las braceras de Hipo (Cómo entrenar a tu dragón 2)")
    # Parte I
    page_cover(c); c.showPage()
    page_analysis(c); c.showPage()
    page_measures(c); c.showPage()
    page_materials(c); c.showPage()
    page_assembly(c); c.showPage()
    # Parte II (patrones, horizontales)
    for fn in (page_p1, page_p2, page_p3, page_p4):
        c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
        fn(c)
        c.showPage()
    # Parte III
    for fn in (page_tut1, page_tut2, page_tut3, page_sizes):
        c.setPageSize((PW * CM, PH * CM))
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT)


def build_print():
    """PDF solo con las 4 hojas A4 para imprimir en papel como guía."""
    out = OUT.replace("Patrones_y_Tutorial", "Guias_A4_imprimir")
    c = canvas.Canvas(out, pagesize=(G.PAGE_W * CM, G.PAGE_H * CM))
    c.setTitle("Braceras de Hipo · Guías A4 para imprimir (escala 1:1)")
    for fn in (page_p1, page_p2, page_p3, page_p4):
        c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", out)


if __name__ == "__main__":
    build()
    build_print()
