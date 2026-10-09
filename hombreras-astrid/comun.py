"""Utilidades comunes de dibujo (reportlab) para las fichas de cosplay."""
import math

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
FOOTER = [""]


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
    text(c, FOOTER[0], 1.6, 0.85, 7.5, "LS", GRAYT)
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



# ================================================================== bloques
def figbox(c, x, yt, w, h, title):
    box(c, x, yt - h, w, h, fill=colors.white)
    text(c, title, x + w / 2, yt - 0.5, 8, "LS-B", BROWN, "c")


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
