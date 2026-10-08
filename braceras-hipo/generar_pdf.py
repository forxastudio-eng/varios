"""Genera el PDF completo (patrones + tutorial) y el PDF de guías A4 de las braceras de Hipo."""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import geom as G  # noqa: E402
from dibujo import *  # noqa: E402,F401,F403
from dibujo import (ACCENT, BROWN, COL, CM, CREAM, DARK, GRAYT, GUIDE, LEATHER, LINE, PH, PW, ST, WARM,  # noqa: E402
                    box, bullets, callout, checklist, chrome, draw_arm, draw_bracer, draw_line, draw_poly, draw_section,
                    f1, f2, fig_bevel, fig_cut, fig_dagger, fig_heat, fig_hinge, fig_mold, fig_ring, fig_row, fig_slot, para,
                    path_pts, steps_block, table, text)
from reportlab.lib import colors  # noqa: E402
from reportlab.pdfgen import canvas  # noqa: E402
from reportlab.platypus import Paragraph  # noqa: E402
from shapely import affinity  # noqa: E402
from shapely.geometry import LineString, Polygon, box as sbox  # noqa: E402
from shapely.ops import unary_union  # noqa: E402

OUT = os.path.join(HERE, "Braceras_Hipo_Patrones_y_Tutorial.pdf")
OUT_PRINT = os.path.join(HERE, "Braceras_Hipo_Guias_A4_imprimir.pdf")
TOTAL = 18

SL = {y: G.strap_lengths(y) for y in G.STRAP_Y}
PANEL_TOP_W = G.R_at(G.PANEL_Y1, G.T) * G.panel_angle()
PANEL_BOT_W = G.R_at(G.PANEL_Y0, G.T) * G.panel_angle()


def sz(p):
    w, h = G.size(p)
    return f"{f1(w)} × {f1(h)}"


# ================================================================== MAQUETACIÓN DE LAS HOJAS
def layout_p1():
    b = G.base()
    w, h = G.size(b)
    return {"BASE": G.at(b, (G.PAGE_W - w) / 2, 0.65)}


def layout_p2():
    it = {}
    pn = G.panel()
    pw, ph = G.size(pn)
    it["P.izq"] = G.at(pn, 0.9, G.CONTENT_TOP - ph - 0.05)
    it["P.der"] = G.at(pn, 0.9 + pw + 0.55, G.CONTENT_TOP - ph - 0.05)
    # cinchas centrales
    rows = [("B1c", SL[G.STRAP_Y[0]]["central"], "B1c·daga", SL[G.STRAP_Y[0]]["central_daga"]),
            ("B2c", SL[G.STRAP_Y[1]]["central"], "B2c·daga", SL[G.STRAP_Y[1]]["central_daga"]),
            ("B3c", SL[G.STRAP_Y[2]]["central"], "B3c ", SL[G.STRAP_Y[2]]["central"])]
    y = 0.8
    for k1, l1, k2, l2 in reversed(rows):
        it[k1] = G.at(G.strap(l1), 0.9, y)
        it[k2] = G.at(G.strap(l2), 0.9 + l1 + 0.5, y)
        y += G.STRAP_W + 0.45
    # daga
    x0 = 0.9 + 2 * pw + 0.55 + 0.6
    top = G.CONTENT_TOP - 0.05
    sb, scv = G.sheath_base(), G.sheath_cover()
    it["G1"] = G.at(sb, x0, top - G.size(sb)[1])
    it["G2"] = G.at(scv, it["G1"].bounds[2] + 0.45, top - G.size(scv)[1])
    gx = it["G2"].bounds[2] + 0.45
    it["H1.1"] = G.at(G.grip(), gx, top - 4.4)
    it["H1.2"] = G.at(G.grip(), gx, top - 2 * 4.4 - 0.45)
    it["H2.1"] = G.at(G.crossguard(), x0, it["G1"].bounds[1] - 0.5 - 0.8)
    it["H2.2"] = G.at(G.crossguard(), x0, it["H2.1"].bounds[1] - 0.45 - 0.8)
    it["H3.1"] = G.at(G.pommel(), x0 + 3.45, it["H1.2"].bounds[1] - 0.45 - 1.3)
    it["H3.2"] = G.at(G.pommel(), x0 + 3.45 + 1.75, it["H1.2"].bounds[1] - 0.45 - 1.3)
    return it


def layout_p3():
    it = {}
    rows = [[("B1p", "lateral", 0), ("B3p", "lateral", 2), ("B3a", "medial", 2)],
            [("B1p", "lateral", 0), ("B3p", "lateral", 2), ("B3a", "medial", 2)],
            [("B2p", "lateral", 1), ("B1a", "medial", 0), ("B1a", "medial", 0)],
            [("B2p", "lateral", 1), ("B2a", "medial", 1), ("B2a", "medial", 1)]]
    y = 0.8
    count = {}
    for row in rows:
        x = 0.9
        for name, kind, k in row:
            ln = SL[G.STRAP_Y[k]][kind]
            count[name] = count.get(name, 0) + 1
            it[f"{name}.{count[name]}"] = G.at(G.strap(ln, tip=(kind == "lateral")), x, y)
            x += ln + 0.35
        y += G.STRAP_W + 0.42
    cf = G.cuff()
    c1 = G.at(cf, 0.9, G.CONTENT_TOP - G.size(cf)[1] - 0.05)
    it["PUÑO.1"] = c1
    it["PUÑO.2"] = affinity.translate(c1, 0, -(G.CUFF_Y1 - G.CUFF_Y0) - 0.45)
    return it


def layout_p4():
    g = G.guard_plate()
    w, h = G.size(g)
    y = G.CONTENT_TOP - h - 0.05
    return {"GA.1": G.at(g, 0.9, y), "GA.2": G.at(g, 0.9 + w + 0.6, y)}


def layout_p5():
    b = G.guard_seg_base()
    w, h = G.size(b)
    y = G.CONTENT_TOP - h - 0.05
    it = {"GB.1": G.at(b, 0.9, y), "GB.2": G.at(b, 0.9 + w + 0.6, y)}
    bd = G.guard_band()
    bw, bh = G.size(bd)
    x = 0.9 + 2 * w + 1.2
    it["BANDA.1"] = G.at(bd, x, G.CONTENT_TOP - bh - 0.05)
    it["BANDA.2"] = G.at(bd, x, G.CONTENT_TOP - 2 * bh - 0.5)
    return it


def layout_p6():
    it = {}
    x = 0.9
    for rep in (1, 2):
        for i in range(4):
            s = G.guard_strip(i)
            w, h = G.size(s)
            it[f"L{i + 1}.{rep}"] = G.at(s, x, G.CONTENT_TOP - h - 0.05)
            x += w + 0.45
    return it


# ================================================================== PORTADA
def page_cover(c):
    c.setFillColor(BROWN)
    c.rect(0, (PH - 7.6) * CM, PW * CM, 7.6 * CM, stroke=0, fill=1)
    c.setFillColor(ACCENT)
    c.rect(0, (PH - 7.75) * CM, PW * CM, 0.15 * CM, stroke=0, fill=1)
    text(c, "COSPLAY · PATRONES + TUTORIAL · VERSIÓN COMPLETA", 1.8, PH - 2.0, 9.5, "LS-B", colors.HexColor("#F0DCC4"))
    text(c, "BRACERAS DE HIPO", 1.8, PH - 3.7, 34, "LS-B", colors.white)
    text(c, "Cómo entrenar a tu dragón 2", 1.8, PH - 4.7, 15, "LS-I", colors.HexColor("#F0DCC4"))
    text(c, "4 piezas principales por bracera · goma EVA 5 mm · patrones 1:1 en A4", 1.8, PH - 5.65, 11, "LS", colors.white)
    text(c, "Talla de referencia: hombre de 1,75 m · complexión normal", 1.8, PH - 6.4, 10, "LS-B", colors.HexColor("#F0DCC4"))
    sc = 0.33
    draw_bracer(c, 15.3, PH - 8.4 - (G.BASE_Y1 + G.PEAK_H) * sc, sc, upto=6, variant="A")
    y = PH - 8.6
    box(c, 1.6, y - 10.3, 9.4, 10.3)
    text(c, "Las 4 piezas de cada bracera", 2.0, y - 0.75, 11, "LS-B", BROWN)
    pieces = [("1", "Base", "capa oscura envolvente con pico hacia el codo"),
              ("2", "Panel frontal", "cuero marrón con ranuras para las cinchas"),
              ("3", "Puño", "muñequera oscura de la que sale la guarda"),
              ("4", "Guarda de mano", "versión A placa única · versión B segmentada")]
    yy = y - 1.45
    for n, name, desc in pieces:
        c.setFillColor(ACCENT)
        c.circle(2.35 * CM, (yy + 0.1) * CM, 0.3 * CM, stroke=0, fill=1)
        text(c, n, 2.35, yy - 0.02, 9, "LS-B", colors.white, "c")
        text(c, name, 2.9, yy, 10, "LS-B", DARK)
        yy = para(c, desc, 2.9, yy - 0.2, 7.8, "s") - 0.55
    yy = para(c, "+ accesorios: 3 filas de cinchas (3 tramos cada una) y la daga con funda.", 2.0, yy + 0.1, 8.7, "s") - 0.3
    text(c, "Qué incluye", 2.0, yy - 0.3, 9.5, "LS-B", BROWN)
    bullets(c, ["Análisis de las referencias y despiece", "Medidas y cálculo para 1,75 m", "6 hojas de patrones A4 a escala 1:1",
                "Tutorial en 5 partes + tabla de tallas"], 2.0, yy - 0.5, 8.6, "buls", 0.02)
    y2 = y - 10.3 - 0.45
    box(c, 1.6, y2 - 4.7, 17.8, 4.7)
    text(c, "Cómo usar este PDF", 2.0, y2 - 0.75, 11, "LS-B", BROWN)
    steps = [
        "<b>Lee las págs. 2–6</b>: análisis, despiece, medidas, materiales y orden de montaje.",
        "<b>Imprime las hojas P1–P6</b> (págs. 7–12, o el PDF «Guías A4») en horizontal y al <b>100 % · tamaño real</b>.",
        "<b>Comprueba la regla de 10 cm</b> de cada hoja. Si no mide 10,0 cm, corrige la escala antes de seguir.",
        "Haz la <b>prueba en papel</b> con la base y el panel antes de cortar goma (tutorial, paso 1).",
        "Elige guarda <b>A</b> (hoja P4) o <b>B</b> (hojas P5 + P6) y sigue el tutorial de las págs. 13–17.",
    ]
    yy = y2 - 1.1
    for i, s in enumerate(steps, 1):
        p = Paragraph(s, ST["b"].clone("n", leftIndent=13, bulletIndent=1, bulletFontName="LS-B"), bulletText=f"{i}.")
        _, h = p.wrap(17.0 * CM, 1000)
        p.drawOn(c, 2.0 * CM, yy * CM - h)
        yy -= h / CM + 0.18
    y3 = y2 - 4.7 - 0.45
    box(c, 1.6, y3 - 2.6, 17.8, 2.6, fill=colors.HexColor("#F3F3F3"))
    text(c, "Fuera de este PDF (detalles)", 2.0, y3 - 0.7, 10.5, "LS-B", GRAYT)
    para(c, "Hebillas, anillas D y presillas · remaches y tachuelas · lazo del dedo · hoja metálica de la daga · costuras reales. "
            "Solo se indica <i>dónde van</i> (gris discontinuo en los dibujos).", 2.0, y3 - 1.0, 17.0, "s")


# ================================================================== PÁG 2 · ANÁLISIS
def page_analysis(c):
    chrome(c, 2, TOTAL, "PARTE I · ANÁLISIS", "Análisis de la pieza")
    y = para(c, "Mirando con detalle las dos referencias, cada bracera no es un tubo único sino <b>4 piezas grandes superpuestas</b>: "
                "una <b>base</b> oscura que envuelve el antebrazo y sube en un pico hacia el codo, un <b>panel</b> marrón encima, "
                "un <b>puño</b> oscuro en la muñeca y una <b>guarda</b> sobre el dorso de la mano. Las cinchas atraviesan el panel por ranuras "
                "y en el brazo izquierdo sujetan la daga.", 1.6, PH - 2.2, 17.8, "b")
    sc = 0.5
    ox, oy = 5.0, y - 0.9 - (G.BASE_Y1 + G.PEAK_H + 0.3) * sc
    P = draw_bracer(c, ox, oy, sc, upto=6, ghost=True, variant="A")
    tx = ox + 5.6 * sc
    callout(c, 1, P(4.2, 17.6), (tx, P(0, 19.5)[1]))
    callout(c, 2, P(2.2, 13.2), (tx, P(0, 13.2)[1]))
    callout(c, 3, P(3.5, 3.5), (tx, P(0, 3.5)[1]))
    callout(c, 4, P(3.3, -5.0), (tx, P(0, -5.0)[1]))
    callout(c, 5, P(5.0, G.STRAP_Y[1]), (tx, P(0, 10.0)[1]))
    callout(c, 6, P(G.DAGGER_X - 0.2, 16.5), (ox - 6.2 * sc, P(0, 16.5)[1]))
    callout(c, 6, P(G.DAGGER_X, 9.5), (ox - 6.2 * sc, P(0, 9.5)[1]))
    callout(c, 7, P(-5.0, G.STRAP_Y[0]), (ox - 6.2 * sc, P(0, 14.4)[1]), color=GRAYT)
    callout(c, 7, P(2.2, -8.3), (tx, P(0, -8.3)[1]), color=GRAYT)
    text(c, "Brazal izquierdo (con daga), vista exterior", ox, oy - 11.0 * sc - 0.3, 7.5, "LS-I", GRAYT, "c")
    data = [
        ["", "Qué se ve en las referencias", "Cómo lo resolvemos en EVA 5 mm", "Pieza"],
        ["1", "<b>Base</b> de cuero oscuro que envuelve todo el antebrazo; asoma sobre el panel y sube en un pico detrás de la funda (ref. 1) y en el lomo (ref. 2).",
         f"Cono desarrollado con hueco de cierre de {f1(G.GAP)} cm en el lado interior y pico de {f1(G.PEAK_H)} cm hacia el codo.", "1 ×2 (espejo)"],
        ["2", "<b>Panel</b> marrón en la cara exterior, esquinas redondeadas, costura en el borde; las cinchas entran y salen por ranuras (ref. 2).",
         f"Panel cónico de {f1(G.PANEL_W)} cm de ancho con 6 ranuras, formado sobre la base ya curvada.", "2 ×2"],
        ["3", "<b>Puño</b>: banda oscura en la muñeca; la guarda sale por debajo (ref. 1).",
         "Banda cónica de 3 cm: 2 cm pegados sobre la base y 1 cm de vuelo que tapa la bisagra.", "3 ×2"],
        ["4", "<b>Guarda de mano</b>. Ref. 1: placa con línea grabada. Ref. 2: base oscura + 4 láminas + banda.",
         "Dos versiones: <b>A</b> placa única · <b>B</b> segmentada. Unida al puño con bisagra flexible.", "4A / 4B ×2"],
        ["5", "Tres <b>cinchas</b> negras: puntas libres en el lado posterior, hebillas en el anterior.",
         "Cada fila en 3 tramos: anterior, posterior (con punta) y central, que entra en las ranuras.", "B1–B3"],
        ["6", "<b>Daga</b>: funda oscura con costura; guarda y pomo metálicos. Las cinchas 1 y 2 pasan por encima de la funda.",
         "Funda en 2 capas y empuñadura en 2 capas (10 mm). Cinchas centrales B1/B2 más largas en ese brazal.", "G, H"],
        ["7", "Hebillas, anillas, remaches, lazo del dedo.", "<b>Fuera del PDF</b> (gris discontinuo en el dibujo).", "—"],
    ]
    yt = table(c, data, 8.6, y - 0.25, [0.5, 4.2, 4.5, 1.6], pad=2.2)
    yb = min(yt, oy - 11.0 * sc - 0.6) - 0.35
    yb = para(c, "Observaciones de construcción", 1.6, yb, 17.8, "h2")
    yb = bullets(c, [
        "<b>Izquierda / derecha:</b> el pico (lado posterior) y las puntas de las cinchas cambian de lado. Los patrones se dibujan para el "
        "<b>brazo izquierdo</b>; para el derecho se da la vuelta a la plantilla de la base.",
        "<b>La base se ve:</b> en la ref. 1 la capa oscura asoma 2–4 cm por encima del panel y por los costados. Por eso el panel cubre solo un tercio del perímetro.",
        "<b>Capas:</b> base (5 mm) → puño y panel (+5) → cinchas (+5) → daga (+10). Unos 2,5 cm de altura total en la zona de la funda.",
        "<b>Todo cabe en A4:</b> la pieza mayor (base, " + sz(G.base()) + " cm) entra en una hoja de goma EVA de 21 × 29,7 cm sin empalmes.",
    ], 1.6, yb, 17.8)
    assert yb > 1.7, yb


# ================================================================== PÁG 3 · DESPIECE
def page_parts(c):
    chrome(c, 3, TOTAL, "PARTE I · ANÁLISIS", "Las 4 piezas principales")
    y = para(c, "Cada pieza principal se dibuja aquí en plano y a escala reducida (las medidas son las reales). Todas siguen el mismo cono del "
                "antebrazo, por eso encajan una sobre otra sin arrugas.", 1.6, PH - 2.2, 17.8, "b")
    cards = [
        ("1 · BASE", G.base(), COL["base"], 0.23,
         [f"Medida: {sz(G.base())} cm · hoja P1 (×2)", f"Abarca de 3 a {f1(G.BASE_Y1)} cm sobre la muñeca, +{f1(G.PEAK_H)} cm de pico.",
          f"Deja un hueco de {f1(G.GAP)} cm en el lado interior.", "Brazo derecho: plantilla del revés."]),
        ("2 · PANEL FRONTAL", G.panel(), COL["panel"], 0.38,
         [f"Medida: {sz(G.panel())} cm · hoja P2 (×2)", f"Ancho {f1(PANEL_TOP_W)} cm arriba y {f1(PANEL_BOT_W)} cm abajo.",
          "6 ranuras de 0,5 × 1,6 cm para las cinchas.", "Costura grabada a 0,45 cm del borde."]),
        ("3 · PUÑO", G.cuff(), COL["cuff"], 0.33,
         [f"Medida: {sz(G.cuff())} cm · hoja P3 (×2)", "Alto 3 cm: 2 pegados sobre la base y 1 cm de vuelo.",
          "Mismo hueco de cierre que la base.", "Tapa la bisagra de la guarda."]),
        ("4 · GUARDA DE MANO", None, COL["guard"], 0.33,
         [f"A · placa única {sz(G.guard_plate())} cm · hoja P4", f"B · base {sz(G.guard_seg_base())} + banda + 4 láminas · P5 + P6",
          "Va del puño a los nudillos.", "Bisagra flexible: la muñeca se mueve."]),
    ]
    cw, ch = 8.75, 8.3
    y0 = y - 0.35
    for i, (title, poly, fill, sc, lines) in enumerate(cards):
        col, row = i % 2, i // 2
        x = 1.6 + col * (cw + 0.3)
        yt = y0 - row * (ch + 0.3)
        box(c, x, yt - ch, cw, ch)
        text(c, title, x + 0.35, yt - 0.62, 10, "LS-B", BROWN)
        area_top = yt - 0.95
        if poly is not None:
            w, h = G.size(poly)
            px = x + cw / 2 - w * sc / 2
            py = area_top - h * sc
            ps = G.at(poly, 0, 0)
            draw_poly(c, ps, fill=fill, stroke=DARK, lw=0.6, ox=px, oy=py, sc=sc)
            bottom = py
        else:
            ga = G.at(G.guard_plate(), 0, 0)
            gb = G.at(G.guard_seg_base(), 0, 0)
            w, h = G.size(ga)
            px = x + cw / 2 - w * sc - 0.35
            py = area_top - h * sc
            draw_poly(c, ga, fill=COL["guard"], stroke=DARK, lw=0.6, ox=px, oy=py, sc=sc)
            gx = x + cw / 2 + 0.35
            off = G.guard_seg_base().bounds
            draw_poly(c, gb, fill=COL["guard_base"], stroke=DARK, lw=0.6, ox=gx, oy=py, sc=sc)
            for k in range(4):
                s = affinity.translate(G.guard_strip(k), -off[0], -off[1])
                draw_poly(c, s, fill=COL["guard"], stroke=DARK, lw=0.4, ox=gx, oy=py, sc=sc)
            bd = affinity.translate(G.guard_band(), -off[0], -off[1])
            draw_poly(c, bd, fill=COL["guard"], stroke=DARK, lw=0.4, ox=gx, oy=py, sc=sc)
            text(c, "A", px + w * sc / 2, py - 0.4, 8, "LS-B", BROWN, "c")
            text(c, "B", gx + w * sc / 2, py - 0.4, 8, "LS-B", BROWN, "c")
            bottom = py - 0.4
        yy = yt - ch + 2.3
        bullets(c, lines, x + 0.3, yy, cw - 0.6, "buls", 0.0)
    yb = y0 - 2 * ch - 0.3 - 0.45
    yb = para(c, "Corte transversal: cómo se apilan las capas", 1.6, yb, 17.8, "h2")
    sc = 0.47
    cx, cy = 5.0, yb - 3.15
    r_in = draw_section(c, cx, cy, sc)
    text(c, "exterior (panel)", cx, cy + (r_in + 1.9) * sc, 7, "LS-B", BROWN, "c")
    text(c, f"interior: hueco {f1(G.GAP)} cm", cx, cy - (r_in + 1.2) * sc, 7, "LS-B", ACCENT, "c")
    text(c, "punta libre →", cx - 2.0, cy - (r_in + 1.4) * sc, 6.5, "LS-I", GRAYT, "r")
    data = [["Capa", "Pieza", "Altura sobre el brazo"],
            ["1", "Base (1)", "5 mm"],
            ["2", "Puño (3) o panel (2)", "10 mm"],
            ["3", "Cinchas: tramos laterales sobre la base, central sobre el panel", "10 / 15 mm"],
            ["4", "Daga (funda 2 capas, empuñadura 2 capas), solo brazo izquierdo", "≈ 25 mm"],
            ["—", "Guarda (4): va aparte, unida al vuelo del puño con bisagra", "5–10 mm"]]
    table(c, data, 9.6, yb - 0.2, [1.0, 6.4, 2.4], pad=2.4)


# ================================================================== PÁG 4 · MEDIDAS
def page_measures(c):
    chrome(c, 4, TOTAL, "PARTE I · ANÁLISIS", "Medidas para 1,75 m y cálculo del patrón")
    y = para(c, "Medidas de partida para un hombre de <b>1,75 m y complexión normal</b> (valores antropométricos medios). Mídelas siempre "
                "<b>sobre la manga</b> que llevarás. Si las tuyas difieren, usa la fórmula de abajo o la tabla de la página 18.", 1.6, PH - 2.2, 17.8, "b")
    draw_arm(c, 4.5, 10.6, 0.46)
    X0 = 9.0
    data = [["Cód.", "Medida", "Valor"],
            ["M1", "Estatura de referencia", "175 cm"],
            ["M2", f"Perímetro del antebrazo a {f1(G.BASE_Y1)} cm de la muñeca (borde superior de la base)", f"{f1(G.skin(G.BASE_Y1))} cm"],
            ["M3", f"Perímetro del antebrazo a {f1(G.BASE_Y0)} cm de la muñeca (borde inferior de la base)", f"{f1(G.skin(G.BASE_Y0))} cm"],
            ["M4", "Perímetro de la muñeca", "17,0 cm"],
            ["M5", "Largo de la base (lado interior) · + pico hacia el codo", f"{f1(G.H)} + {f1(G.PEAK_H)} cm"],
            ["M6", "Puño: alto total / zona pegada sobre la base", "3,0 / 2,0 cm"],
            ["M7", "Largo muñeca → nudillos", "10,0 cm"],
            ["M8", "Ancho del dorso a la altura de los nudillos", "8,6 cm"],
            ["M9", "Holgura sobre el perímetro (manga + movimiento)", f"+{f1(G.EASE)} cm"],
            ["M10", "Hueco de cierre en el lado interior", f"{f1(G.GAP)} cm"],
            ["M11", "Compensación por grosor: π × 0,5 cm por capa", "+1,57 cm"]]
    yt = table(c, data, X0, y - 0.3, [1.1, 7.3, 2.0], pad=2.3)
    yb = para(c, "Cálculo de la base (pieza 1)", X0, yt - 0.45, 10.4, "h3") - 0.1
    form = [
        f"Perímetro neutro abajo  C0 = M3 + M9 + M11 = <b>{f1(G.C0)} cm</b>",
        f"Perímetro neutro arriba  C1 = M2 + M9 + M11 = <b>{f1(G.C1)} cm</b>",
        f"Ángulo del desarrollo  θ = (C1 − C0) / M5 = <b>{f1(math.degrees(G.THETA))}°</b>",
        f"Radio inferior  R0 = C0 / θ = <b>{f1(G.R0)} cm</b> · superior R0 + {f1(G.H)} = <b>{f1(G.R0 + G.H)} cm</b>",
        f"Se resta el hueco de cierre ({f1(G.GAP)} cm) con dos cortes paralelos a la costura y se suma el pico.",
        f"Resultado: <b>{sz(G.base())} cm</b> → cabe en una hoja A4.",
    ]
    hb = 4.4
    box(c, X0, yb - hb, 10.4, hb)
    yy = yb - 0.25
    for f in form:
        yy = para(c, f, X0 + 0.3, yy, 9.9, "s") - 0.15
    yb = para(c, "Panel, puño y cinchas usan el mismo cono, desplazado 0,5 cm hacia fuera por cada capa que tienen debajo "
                 "(+π × 0,5 cm de perímetro por capa). Así cada pieza abraza a la anterior sin holguras ni arrugas.",
              X0, yb - hb - 0.25, 10.4, "xs")
    yb -= 0.35
    box(c, X0, yb - 5.5, 10.4, 5.5, fill=WARM, stroke=ACCENT)
    text(c, "Cómo tomar tus medidas", X0 + 0.3, yb - 0.65, 10, "LS-B", ACCENT)
    bullets(c, ["Cinta métrica flexible, <b>con la manga del traje puesta</b>, brazo relajado y ligeramente flexionado.",
                f"Marca con rotulador de piel los puntos a {f1(G.BASE_Y0)} cm y a {f1(G.BASE_Y1)} cm del pliegue de la muñeca y mide el perímetro en cada uno.",
                "Mano abierta: largo muñeca → nudillos y ancho a la altura de los nudillos.",
                "Si tu brazo es muy musculado o muy delgado, cambia M2 y M3 en la fórmula o usa la tabla de tallas (pág. 18)."],
            X0 + 0.3, yb - 1.0, 9.8, "buls", 0.12)


# ================================================================== PÁG 5 · MATERIALES Y PIEZAS
def page_materials(c):
    chrome(c, 5, TOTAL, "PARTE I · ANÁLISIS", "Materiales, herramientas y lista de piezas")
    y = para(c, "Materiales", 1.6, PH - 2.2, 8.7, "h2")
    y = bullets(c, [
        "<b>Goma EVA 5 mm, hojas A4: 5 hojas</b> con guarda A o <b>6</b> con guarda B (compra una más de repuesto). Densidad media-alta.",
        "Cemento de contacto, con pincel o espátula.",
        "Papel A4 para imprimir y cartulina para plantillas reutilizables.",
        "Imprimación flexible (Plasti Dip o vinílica/PVA diluida), acrílicos y barniz mate.",
        "Para la bisagra de la guarda: elástico plano de 3 cm o polipiel (8 cm por guarda).",
        "Para el cierre: hebillas o velcro (fuera del PDF).",
    ], 1.6, y, 8.7, "buls", 0.08)
    y2 = para(c, "Herramientas", 10.7, PH - 2.2, 8.7, "h2")
    y2 = bullets(c, [
        "Cúter o bisturí con cuchillas nuevas, regla metálica y tapete de corte.",
        "Sacabocados o cúter fino para las ranuras del panel.",
        "Pistola de calor; botella o tubo de Ø 6–9 cm; cinta de carrocero.",
        "Lija 120/240 o herramienta rotativa con fresa de lijado.",
        "Pirograbador o rueda de costura para las líneas grabadas (opcional).",
        "Pinceles, esponja; guantes y mascarilla (ventila al usar cemento).",
    ], 10.7, y2, 8.7, "buls", 0.08)
    y = min(y, y2) - 0.3
    y = para(c, "Lista completa de piezas", 1.6, y, 17.8, "h2")
    s1, s2, s3 = (SL[v] for v in G.STRAP_Y)
    data = [["ID", "Pieza", "Cant.", "Medida (cm)", "Hoja", "Notas"],
            ["1", "Base", "2", sz(G.base()), "P1", "Izq. tal cual · der. del revés"],
            ["2", "Panel frontal", "2", sz(G.panel()), "P2", "Con 6 ranuras"],
            ["3", "Puño", "2", sz(G.cuff()), "P3", "Banda cónica de 3 cm"],
            ["4A", "Guarda · placa única", "2", sz(G.guard_plate()), "P4", "Versión A"],
            ["4B", "Guarda · base", "2", sz(G.guard_seg_base()), "P5", "Versión B"],
            ["4B", "Guarda · banda", "2", sz(G.guard_band()), "P5", "Versión B"],
            ["4B", "Guarda · láminas L1–L4", "2 de cada", "≈ 2,0 × 10–11", "P6", "Versión B"],
            ["B·a", "Cincha tramo anterior B1a/B2a/B3a", "2 de cada", f"{f1(s1['medial'])} / {f1(s2['medial'])} / {f1(s3['medial'])} × 1,4", "P3", "Va a la hebilla"],
            ["B·p", "Cincha tramo posterior B1p/B2p/B3p", "2 de cada", f"{f1(s1['lateral'])} / {f1(s2['lateral'])} / {f1(s3['lateral'])} × 1,4", "P3", "Con punta libre"],
            ["B·c", "Cincha central B1c/B2c/B3c", "1 / 1 / 2", f"{f1(s1['central'])} / {f1(s2['central'])} / {f1(s3['central'])} × 1,4", "P2", "Entra en las ranuras"],
            ["B·c", "Cincha central larga B1c·daga/B2c·daga", "1 / 1", f"{f1(s1['central_daga'])} / {f1(s2['central_daga'])} × 1,4", "P2", "Pasa sobre la funda"],
            ["G1/G2", "Funda: base / tapa", "1 / 1", f"{sz(G.sheath_base())} / {sz(G.sheath_cover())}", "P2", "Solo brazo izq."],
            ["H1–H3", "Empuñadura / guarda / pomo", "2 / 2 / 2", "4,4 × 1,4 · 3,0 × 0,8 · Ø 1,3", "P2", "2 capas cada uno"]]
    y = table(c, data, 1.6, y - 0.1, [1.2, 5.1, 1.6, 4.4, 1.1, 4.4], pad=2.0)
    y = para(c, "Distribución de las hojas de goma EVA", 1.6, y - 0.4, 17.8, "h2")
    data2 = [["Hoja", "Contenido", "Hojas de EVA"],
             ["P1", "1 · base (se traza dos veces: izquierda tal cual y derecha con la plantilla del revés)", "2"],
             ["P2", "2 · paneles ×2 + cinchas centrales ×6 + daga completa", "1"],
             ["P3", "3 · puños ×2 + cinchas anteriores ×6 + cinchas posteriores ×6", "1"],
             ["P4", "4A · guardas de placa única ×2", "1 (versión A)"],
             ["P5 + P6", "4B · bases ×2 y bandas ×2 (P5) + láminas ×8 (P6)", "2 (versión B)"]]
    y = table(c, data2, 1.6, y - 0.1, [1.8, 12.7, 3.3], pad=2.3)
    assert y > 1.7, y


# ================================================================== PÁG 6 · ORDEN DE MONTAJE
def page_assembly(c):
    chrome(c, 6, TOTAL, "PARTE I · ANÁLISIS", "Orden de montaje por capas")
    y = para(c, "Cada tarjeta añade una capa (la nueva lleva contorno naranja). Este orden oculta las uniones y te deja pegar siempre sobre goma "
                "limpia, sin sellar ni pintar.", 1.6, PH - 2.2, 17.8, "b")
    cards = [("1 · Base", "base", "Corta, termoforma el cono y abre ligeramente el pico. Prueba el cierre con cinta."),
             ("2 · Puño", "cuff", "Pega 2 cm del puño sobre el borde inferior de la base; deja 1 cm de vuelo hacia la mano."),
             ("3 · Panel", "panel", "Corta las ranuras, forma el panel sobre la base y pégalo centrado en la guía."),
             ("4 · Cinchas", "straps", "Tramos anterior y posterior sobre la base; tramo central entrando en las ranuras."),
             ("5 · Daga", "dagger", "Solo brazo izquierdo: funda bajo las cinchas centrales B1·B2; empuñadura sobre B3."),
             ("6 · Guarda", "guard", "Une la guarda (A o B) al vuelo del puño con la bisagra de elástico o polipiel.")]
    cw, ch = 5.8, 10.1
    y0 = y - 0.35
    sc = 0.235
    for i, (title, layer, desc) in enumerate(cards):
        col, row = i % 3, i // 3
        x = 1.6 + col * (cw + 0.2)
        yt = y0 - row * (ch + 0.3)
        box(c, x, yt - ch, cw, ch)
        text(c, title, x + 0.3, yt - 0.6, 9.5, "LS-B", BROWN)
        draw_bracer(c, x + cw / 2, yt - 1.0 - (G.BASE_Y1 + G.PEAK_H + 0.2) * sc, sc, upto=i + 1, hl=layer)
        p = Paragraph(desc, ST["s"])
        _, h = p.wrap((cw - 0.6) * CM, 1000)
        p.drawOn(c, (x + 0.3) * CM, (yt - ch + 0.3) * CM)
    yb = y0 - 2 * ch - 0.3 - 0.4
    yb = para(c, "Reglas de oro del montaje", 1.6, yb, 17.8, "h2")
    yb = bullets(c, ["Prueba en seco cada capa antes de poner cemento: el cemento de contacto no se puede recolocar.",
                     "Forma (calienta) cada pieza antes de pegarla; el panel se forma usando la base ya curvada como molde.",
                     "Sella y pinta después de montar las capas, pero antes de añadir hebillas y remaches."], 1.6, yb, 17.8, "bul", 0.08)
    assert yb > 1.7, yb


# ================================================================== HOJAS DE PATRONES
def ruler(c, x0=18.7, y=20.0, vertical=False):
    c.saveState()
    c.setStrokeColor(DARK)
    c.setLineWidth(0.8)
    if not vertical:
        c.line(x0 * CM, y * CM, (x0 + 10) * CM, y * CM)
        for i in range(11):
            h = 0.3 if i % 5 == 0 else 0.17
            c.line((x0 + i) * CM, y * CM, (x0 + i) * CM, (y + h) * CM)
    else:
        c.line(x0 * CM, y * CM, x0 * CM, (y + 10) * CM)
        for i in range(11):
            h = 0.3 if i % 5 == 0 else 0.17
            c.line(x0 * CM, (y + i) * CM, (x0 + h) * CM, (y + i) * CM)
    c.restoreState()
    if not vertical:
        text(c, "0", x0, y - 0.3, 6.5, "LS", DARK, "c")
        text(c, "5", x0 + 5, y - 0.3, 6.5, "LS", DARK, "c")
        text(c, "10 cm", x0 + 10, y - 0.3, 6.5, "LS-B", DARK, "c")
        text(c, "Regla de control: debe medir 10,0 cm", x0 - 0.3, y + 0.05, 7, "LS", DARK, "r")
    else:
        text(c, "0", x0 + 0.45, y - 0.08, 6.5, "LS", DARK)
        text(c, "5", x0 + 0.45, y + 4.92, 6.5, "LS", DARK)
        text(c, "10 cm", x0 + 0.45, y + 9.92, 6.5, "LS-B", DARK)
        text(c, "Regla de control: 10,0 cm", x0 - 0.12, y + 5.0, 6.5, "LS", DARK, "c", 90)


def pattern_header(c, code, title):
    text(c, f"HOJA {code} · {title}", 0.8, 20.05, 9.5, "LS-B", BROWN)
    text(c, "Imprimir al 100 % (tamaño real) · A4 horizontal · línea negra gruesa = corte · discontinuas = guías", 0.8, 19.72, 6.6, "LS", GRAYT)
    ruler(c)


def notes(c, x, y, w, lines, title=None, size=7.2, lh=0.46):
    n = len(lines) + (1 if title else 0)
    h = n * lh + 0.4
    box(c, x, y - h, w, h, fill=CREAM)
    yy = y - 0.5
    if title:
        text(c, title, x + 0.3, yy, 8, "LS-B", BROWN)
        yy -= lh
    for ln in lines:
        text(c, ln, x + 0.3, yy, size, "LS", DARK)
        yy -= lh
    return y - h


def label(c, poly, s, size=7.5, color=DARK, font="LS-B", dx=0.0, dy=0.0, rot=0):
    p = poly.representative_point()
    text(c, s, p.x + dx, p.y - size * 0.012 + dy, size, font, color, "c", rot)


def page_p1(c, n=None):
    items = layout_p1()
    B = items["BASE"]
    ob = G.base()
    dx, dy = B.bounds[0] - ob.bounds[0], B.bounds[1] - ob.bounds[1]

    def T_(geom_):
        return affinity.translate(geom_, dx, dy)

    def pt(x, y):
        return (x + dx, y + dy)

    G.check(items, "P1", top_limit=None)
    text(c, "HOJA P1 · 1 · BASE ×2", 0.8, 20.05, 9.5, "LS-B", BROWN)
    text(c, "Imprimir al 100 % · A4 horizontal · EVA 5 mm", 0.8, 19.72, 6.6, "LS", GRAYT)
    ruler(c, 0.9, 1.0, vertical=True)
    draw_poly(c, B, stroke=DARK, lw=1.6)
    # eje
    draw_line(c, [pt(0, G.R_at(G.BASE_Y0)), pt(0, G.R_at(G.BASE_Y1) + 2.0)], colors.HexColor("#B5B5B5"), 0.5, (6, 3, 1, 3))
    # zona del puño
    arc = LineString([G.pol(G.R_at(G.CUFF_Y1), -G.THETA / 2 - 0.1 + i * (G.THETA + 0.2) / 120) for i in range(121)])
    seg = arc.intersection(ob)
    for g in getattr(seg, "geoms", [seg]):
        draw_line(c, list(T_(g).coords), colors.HexColor("#7A7A7A"), 0.8, (2, 2))
    mx, my = pt(*G.arc_xy(-7.2, (G.BASE_Y0 + G.CUFF_Y1) / 2 - 0.15))
    text(c, "zona del puño (3): se pega encima, 2 cm", mx, my, 6.8, "LS-I", GRAYT, "c")
    # panel proyectado
    pan = G.to_base(G.panel_outline())
    draw_poly(c, T_(pan), stroke=BROWN, lw=1.0, dash=(5, 2.5))
    for sl in G.slots():
        draw_poly(c, T_(G.to_base(sl)), stroke=BROWN, lw=0.6)
    # cinchas (tramos sobre la base)
    for k, y in enumerate(G.STRAP_Y, 1):
        band = G.ring_piece(y - G.STRAP_W / 2, y + G.STRAP_W / 2, 0.0).difference(pan.buffer(0.02))
        for g in getattr(band, "geoms", [band]):
            draw_poly(c, T_(g), stroke=GUIDE, lw=0.8, dash=(3.5, 2))
        lx, ly = pt(*G.arc_xy(-(G.circ_at(y) - G.GAP) / 2 + 1.0, y - 0.13))
        text(c, f"B{k}a", lx, ly, 7, "LS-B", GUIDE)
        rx, ry = pt(*G.arc_xy((G.circ_at(y) - G.GAP) / 2 - 1.0, y - 0.13))
        text(c, f"B{k}p →", rx, ry, 7, "LS-B", GUIDE, "r")
    # daga
    parts = G.dagger_parts()
    for key in ("sheath", "guard", "grip", "pommel"):
        poly = parts[key]
        pts = [pt(*G.arc_xy(G.DAGGER_X + x, G.DAGGER_Y0 + yy)) for x, yy in poly.exterior.coords]
        path_pts(c, pts, None, ACCENT, 1.0, dash=(4, 2.5))
    dx_, dy_ = pt(*G.arc_xy(G.DAGGER_X - 1.6, G.DAGGER_Y0 + 13.6))
    text(c, "daga", dx_, dy_, 6.8, "LS-B", ACCENT, "r")
    dx_, dy_ = pt(*G.arc_xy(G.DAGGER_X - 1.6, G.DAGGER_Y0 + 13.0))
    text(c, "(solo izq.)", dx_, dy_, 6.3, "LS-I", ACCENT, "r")
    # textos principales
    tx, ty = pt(*G.arc_xy(G.PEAK_C, G.BASE_Y1 + G.PEAK_H - 1.0))
    text(c, "PICO ↑ hacia el codo (posterior)", tx, ty, 7.5, "LS-B", DARK, "c")
    tx, ty = pt(*G.arc_xy(0, G.BASE_Y0 + 0.35))
    text(c, "↓ MUÑECA · borde inferior", tx, ty, 7.5, "LS-B", DARK, "c")
    mx, my = pt(*G.arc_xy(-6.4, 10.0))
    text(c, "1 · BASE", mx, my + 0.3, 14, "LS-B", BROWN, "c")
    text(c, "cortar 2 · capa oscura", mx, my - 0.25, 8, "LS", DARK, "c")
    mx2, my2 = pt(*G.arc_xy(6.6, 10.0))
    text(c, "IZQUIERDA: plantilla tal cual", mx2, my2 + 0.3, 7.8, "LS-B", DARK, "c")
    text(c, "DERECHA: plantilla del revés", mx2, my2 - 0.15, 7.8, "LS-B", DARK, "c")
    text(c, "(el pico y las puntas cambian de lado)", mx2, my2 - 0.6, 6.8, "LS-I", GRAYT, "c")
    # bordes de cierre
    for sgn, s_lab in ((-1, "BORDE ANTERIOR · hebillas (fuera del PDF)"), (1, "BORDE POSTERIOR · puntas de las cinchas")):
        ang = math.degrees(math.atan2(math.cos(G.THETA / 2), sgn * math.sin(G.THETA / 2)))
        s = sgn * ((G.circ_at(11.5) - G.GAP) / 2 - 0.55)
        X, Y = pt(*G.arc_xy(s, 11.6))
        text(c, s_lab, X, Y, 6.6, "LS-I", GRAYT, "c", ang if sgn < 0 else ang - 180)
    # leyenda
    lx = 23.9
    for i, (col_, dash_, lab) in enumerate([(BROWN, (5, 2.5), "panel (2) y ranuras"), (GUIDE, (3.5, 2), "tramos de cincha B·a / B·p"),
                                            (ACCENT, (4, 2.5), "daga (brazo izq.)"), (colors.HexColor("#7A7A7A"), (2, 2), "borde del puño")]):
        yy = 19.3 - i * 0.45
        draw_line(c, [(lx, yy + 0.08), (lx + 0.9, yy + 0.08)], col_, 1.0, dash_)
        text(c, lab, lx + 1.1, yy, 6.8, "LS", DARK)
    text(c, "Leyenda de guías", lx, 19.75, 7.2, "LS-B", BROWN)
    # comprobaciones de espacio libre
    boxes = [("titulo", sbox(0.8, 19.6, 9.5, 20.4)), ("regla", sbox(0.8, 0.9, 1.9, 11.1)), ("leyenda", sbox(23.8, 17.7, 28.9, 20.1))]
    G.check(items, "P1-textos", top_limit=None, boxes=boxes)


def page_p2(c):
    items = layout_p2()
    G.check(items, "P2")
    pattern_header(c, "P2", "2 · PANELES ×2 + CINCHAS CENTRALES + DAGA")
    for k, p in items.items():
        draw_poly(c, p, stroke=DARK, lw=1.3)
    po = G.panel()
    for k in ("P.izq", "P.der"):
        P = items[k]
        ox, oy = P.bounds[0] - po.bounds[0], P.bounds[1] - po.bounds[1]
        stitch = G.panel_outline().buffer(-0.45)
        draw_poly(c, stitch, stroke=GUIDE, lw=0.6, dash=(1.5, 1.8), ox=ox, oy=oy)
        for y in G.STRAP_Y:   # guía de la cincha central entre ranuras
            half = G.R_at(y, G.T) * G.panel_angle() / 2 - G.SLOT_IN
            for off in (-G.STRAP_W / 2, G.STRAP_W / 2):
                r = G.R_at(y, G.T) + off
                pts = [G.pol(r, (-half + 2 * half * i / 30) / r) for i in range(31)]
                draw_line(c, pts, colors.HexColor("#9A9A9A"), 0.5, (2, 2), ox, oy)
        cx, cy = G.arc_xy(0, (G.PANEL_Y0 + G.PANEL_Y1) / 2 + 0.4, G.T)
        text(c, "2 · PANEL", cx + ox, cy + oy + 0.1, 10, "LS-B", BROWN, "c")
        text(c, "izquierdo (con daga)" if k == "P.izq" else "derecho", cx + ox, cy + oy - 0.35, 7, "LS", DARK, "c")
        tx, ty = G.arc_xy(0, G.PANEL_Y1 - 0.65, G.T)
        text(c, "↑ codo", tx + ox, ty + oy, 6.8, "LS-I", GRAYT, "c")
        text(c, "ranuras = cortar (huecos)", cx + ox, cy + oy - 0.8, 6.3, "LS-I", GRAYT, "c")
        if k == "P.izq":
            parts = G.dagger_parts()
            for key in ("sheath", "guard", "grip", "pommel"):
                pts = [G.arc_xy(G.DAGGER_X + x, G.DAGGER_Y0 + yy, G.T) for x, yy in parts[key].exterior.coords]
                clip = Polygon(pts).intersection(G.panel_outline())
                if not clip.is_empty:
                    draw_poly(c, clip, stroke=ACCENT, lw=0.9, dash=(4, 2.5), ox=ox, oy=oy)
            tx, ty = G.arc_xy(G.DAGGER_X, G.PANEL_Y1 - 1.3, G.T)
            text(c, "guía daga", tx + ox, ty + oy, 6.3, "LS-B", ACCENT, "c")
    for k, p in items.items():
        if k.startswith("B"):
            ln = G.size(p)[0]
            name = k.strip()
            text(c, f"{name} · {f1(ln)} × 1,4", p.bounds[0] + 0.35, p.bounds[1] + 0.5, 6.8, "LS-B", DARK)
        elif k[0] in "GH":
            label(c, p, k.split(".")[0], 6.5 if k[0] == "H" else 7.5)
    G1 = items["G2"]
    draw_poly(c, G1.buffer(-0.22), stroke=GUIDE, lw=0.5, dash=(1.5, 1.5))
    x0 = items["G1"].bounds[0]
    notes(c, 22.9, min(items["H2.2"].bounds[1], items["H3.1"].bounds[1]) - 0.35, 6.2,
          ["G1 base y G2 tapa de la funda (1 de cada).", "H1 empuñadura, H2 guarda, H3 pomo:", "2 capas de cada (10 mm).",
           "B·c entran 0,6 cm en las ranuras.", "B1c·daga y B2c·daga: brazo izq.", "(pasan por encima de la funda)."],
          "Daga y cinchas centrales")


def page_p3(c):
    items = layout_p3()
    G.check(items, "P3")
    pattern_header(c, "P3", "3 · PUÑOS ×2 + CINCHAS ANTERIORES Y POSTERIORES")
    for k, p in items.items():
        draw_poly(c, p, stroke=DARK, lw=1.3)
    for k in ("PUÑO.1", "PUÑO.2"):
        P = items[k]
        cf = G.cuff()
        ox, oy = P.bounds[0] - cf.bounds[0], P.bounds[1] - cf.bounds[1]
        arc = LineString([G.pol(G.R_at(G.BASE_Y0, G.T), -G.THETA / 2 - 0.1 + i * (G.THETA + 0.2) / 120) for i in range(121)]).intersection(cf)
        for g in getattr(arc, "geoms", [arc]):
            draw_line(c, list(g.coords), ACCENT, 0.9, (4, 2), ox, oy)
        cx, cy = G.arc_xy(0, 4.1, G.T)
        text(c, "3 · PUÑO", cx + ox, cy + oy - 0.1, 9, "LS-B", BROWN, "c")
        cx, cy = G.arc_xy(-6.5, 3.6, G.T)
        text(c, "zona pegada sobre la base (2 cm)", cx + ox, cy + oy, 6.5, "LS-I", GRAYT, "c")
        cx, cy = G.arc_xy(6.5, 2.35, G.T)
        text(c, "vuelo 1 cm (bisagra) ↓", cx + ox, cy + oy, 6.5, "LS-I", ACCENT, "c")
    for k, p in items.items():
        if k.startswith("B"):
            name = k.split(".")[0]
            kind = "posterior · punta →" if name.endswith("p") else "anterior · a la hebilla"
            text(c, f"{name} {kind} · {f1(G.size(p)[0])} cm", p.bounds[0] + 0.3, p.bounds[1] + 0.5, 6.5, "LS-B", DARK)
    notes(c, 23.2, items["PUÑO.1"].bounds[3], 5.9,
          ["Puño: 2 iguales.", "La línea naranja marca el", "borde inferior de la base.",
           "Cinchas ×12: B·a (anterior)", "y B·p (posterior, con punta)."], "Notas")


def page_p4(c):
    items = layout_p4()
    G.check(items, "P4")
    pattern_header(c, "P4", "4A · GUARDA DE MANO · PLACA ÚNICA ×2")
    g = G.guard_plate()
    for k, P in items.items():
        draw_poly(c, P, stroke=DARK, lw=1.4)
        ox, oy = P.bounds[0] - g.bounds[0], P.bounds[1] - g.bounds[1]
        draw_poly(c, g.buffer(-0.6), stroke=GUIDE, lw=0.7, dash=(3, 2.5), ox=ox, oy=oy)
        hinge = LineString([(-5, G.GUARD_TOP - 1.0), (5, G.GUARD_TOP - 1.0)]).intersection(g)
        draw_line(c, list(hinge.coords), ACCENT, 0.9, (4, 2), ox, oy)
        text(c, "bisagra: zona bajo el puño (1 cm)", ox, G.GUARD_TOP - 0.6 + oy, 6.5, "LS-B", ACCENT, "c")
        text(c, "4A · GUARDA", ox, -3.5 + oy, 10, "LS-B", BROWN, "c")
        text(c, "placa única · cortar 2", ox, -4.0 + oy, 7.2, "LS", DARK, "c")
        text(c, "línea azul: grabado a 0,6 cm", ox, -4.5 + oy, 6.5, "LS-I", GUIDE, "c")
        text(c, "↑ muñeca", ox, 0.8 + oy, 6.8, "LS-I", GRAYT, "c")
        text(c, "↓ nudillos", ox, -9.1 + oy, 6.8, "LS-I", GRAYT, "c")
        text(c, sz(g) + " cm", ox, -5.0 + oy, 6.5, "LS", GRAYT, "c")
    notes(c, 20.6, 19.4, 8.0,
          ["Simétrica: sirve para los dos brazos.", "Curva la placa a lo ancho sobre el", "dorso de la mano (botella Ø 5–6 cm).",
           "La franja superior (naranja) queda", "bajo el vuelo del puño y lleva la", "bisagra pegada por dentro.",
           "Remaches y lazo del dedo: fuera."], "Versión A · notas")


def page_p5(c):
    items = layout_p5()
    G.check(items, "P5")
    pattern_header(c, "P5", "4B · GUARDA SEGMENTADA · BASES ×2 + BANDAS ×2")
    gb = G.guard_seg_base()
    for k, P in items.items():
        draw_poly(c, P, stroke=DARK, lw=1.4)
    for k in ("GB.1", "GB.2"):
        P = items[k]
        ox, oy = P.bounds[0] - gb.bounds[0], P.bounds[1] - gb.bounds[1]
        for i in range(4):
            draw_poly(c, G.guard_strip(i), stroke=GUIDE, lw=0.6, dash=(3, 2), ox=ox, oy=oy)
            p = G.guard_strip(i).representative_point()
            text(c, f"L{i + 1}", p.x + ox, p.y + oy - 1.5, 6.5, "LS-B", GUIDE, "c")
        draw_poly(c, G.guard_band(), stroke=ACCENT, lw=0.7, dash=(4, 2), ox=ox, oy=oy)
        text(c, "4B · BASE", ox, -4.6 + oy, 9, "LS-B", BROWN, "c")
        text(c, "(oscura) cortar 2", ox, -5.05 + oy, 6.8, "LS", DARK, "c")
        text(c, "banda", ox, 1.55 + oy, 6.5, "LS-B", ACCENT, "c")
    for k in ("BANDA.1", "BANDA.2"):
        label(c, items[k], "BANDA (marrón)", 7)
    notes(c, items["BANDA.1"].bounds[0], items["BANDA.2"].bounds[1] - 0.4, 8.0,
          ["Base oscura: azul = posición de las", "láminas L1–L4 (hoja P6).", "Naranja = posición de la banda.",
           "Orden: base → láminas → banda.", "La banda tapa el inicio de las", "láminas y lleva la bisagra."], "Versión B · notas")


def page_p6(c):
    items = layout_p6()
    G.check(items, "P6")
    pattern_header(c, "P6", "4B · LÁMINAS DE LA GUARDA SEGMENTADA ×8")
    for k, P in items.items():
        draw_poly(c, P, stroke=DARK, lw=1.3)
        label(c, P, k.split(".")[0], 8, rot=0, dy=2.5)
        text(c, f"{f1(G.size(P)[1])} cm", P.representative_point().x, P.representative_point().y - 1.0, 6, "LS", GRAYT, "c")
        text(c, "↑", P.representative_point().x, P.bounds[3] - 0.6, 7, "LS-B", GRAYT, "c")
    notes(c, 0.9, min(p.bounds[1] for p in items.values()) - 0.6, 14.5,
          ["Cortar 2 juegos (L1, L2, L3, L4 por cada guarda). La flecha ↑ indica el extremo de la muñeca (bajo la banda).",
           "Bisela un poco los bordes largos y redondea la punta con lija: así se marca la separación entre láminas.",
           "Pega cada lámina sobre su guía azul de la base (hoja P5), dejando 2–3 mm de base oscura visible entre ellas."],
          "Notas", lh=0.5)


# ================================================================== TUTORIAL
def page_tut1(c):
    chrome(c, 13, TOTAL, "PARTE III · TUTORIAL 1/5", "Preparación y corte")
    steps = [
        (1, "Prueba en papel", "Imprime P1 y P2, recorta la base y el panel y pruébatelos con cinta sobre la manga, con el pico hacia el codo. "
            f"Los bordes de la base deben quedar a unos <b>{f1(G.GAP)} cm</b> en el lado interior del antebrazo y el panel centrado en la cara exterior. "
            "Si queda justo o sobra, recalcula con la tabla de la pág. 18 antes de cortar goma."),
        (2, "Imprime a escala real", "Imprime las hojas P1–P6 en <b>«tamaño real» / 100 %</b>, en horizontal y sin «ajustar a página». Mide la regla de control "
            "(10 cm). Usa el PDF «Guías A4» si solo quieres imprimir las plantillas."),
        (3, "Recorta las plantillas", "Recorta por la línea negra gruesa, <b>incluidas las ranuras del panel</b> (con cúter). Las líneas discontinuas son guías. "
            "Pegar las plantillas sobre cartulina te deja unas reutilizables."),
        (4, "Traza sobre la goma", "Traza sobre la cara lisa con rotulador fino o punzón. Base: una vez tal cual (izquierda) y otra con la plantilla "
            "<b>del revés</b> (derecha). Marca con un punzón las guías del panel, de las cinchas y de la daga."),
        (5, "Corta", "Usa una cuchilla nueva y haz 2–3 pasadas suaves con la hoja a 90°. Para las ranuras del panel, corta primero los dos lados largos y luego los cortos, "
            "o usa un sacabocados de 5 mm en cada extremo y une los dos agujeros con el cúter."),
        (6, "Bisela", "Rebaja a 45° los extremos de las cinchas centrales (para que entren en las ranuras), el borde superior del puño y los bordes del panel "
            "si quieres un canto suave. <b>No biseles</b> los bordes de cierre de la base."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = fig_row(c, y - 0.05, [fig_cut, fig_slot, fig_ring]) - 0.35
    items = ["1 · base izq.", "1 · base der. (revés)", "2 · panel ×2", "3 · puño ×2", "4A guarda ×2  o  4B ×2", "4B láminas ×8 (si B)",
             "B·a ×6", "B·p ×6", "B·c ×6", "G1 · G2", "H1 ×2 · H2 ×2 · H3 ×2", "Ranuras y guías marcadas"]
    y = checklist(c, 1.6, y, 17.8, "Lista de control del corte", items, cols=3) - 0.4
    box(c, 1.6, y - 3.4, 17.8, 3.4, fill=WARM, stroke=ACCENT)
    text(c, "Consejos de profesional", 2.0, y - 0.65, 10, "LS-B", ACCENT)
    bullets(c, ["Haz primero una bracera completa en cartulina: detectarás errores de talla en 20 minutos.",
                "Cambia o parte la cuchilla en cuanto note que «arrastra»: la goma densa la desafila rápido.",
                "Corta las ranuras antes de formar el panel: en plano es mucho más fácil y preciso.",
                "Escribe con lápiz en el reverso de cada pieza su ID y si es izquierda o derecha."], 2.0, y - 1.0, 17.0, "buls", 0.06)
    assert y - 3.4 > 1.7, y


def page_tut2(c):
    chrome(c, 14, TOTAL, "PARTE III · TUTORIAL 2/5", "Termoformado de las 4 piezas")
    steps = [
        (7, "Base (1)", "Calienta con la pistola a 15–20 cm, siempre en movimiento, hasta que la goma esté flexible (15–20 s por zona). "
            "Enróllala sobre una botella o tubo, con el borde ancho hacia arriba, y sujétala con cinta 1–2 min hasta que se enfríe. "
            "Después calienta solo el pico y ábrelo un poco hacia fuera para que no se clave en el codo al doblarlo."),
        (8, "Puño (3)", "Fórmalo igual, más cerrado. Tiene que abrazar la base por fuera: pruébalo encima del borde inferior de la base ya formada."),
        (9, "Panel (2)", "Calienta el panel y apóyalo, todavía tibio, sobre la base ya formada en su posición (guía marrón de P1). "
            "La base hace de molde y el panel adopta la misma curva. Sujétalo con cinta hasta que se enfríe."),
        (10, "Guarda (4)", "<b>A:</b> curva la placa a lo ancho sobre una botella de Ø 5–6 cm y dale una ligera curva hacia los nudillos. "
             "<b>B:</b> forma la base oscura igual que la placa A; las láminas se forman de una en una, cada una con la curva de su zona del dorso."),
        (11, "Prueba en seco", "Pon todo en su sitio con cinta de carrocero. Comprueba que doblas el codo sin que el pico moleste, "
             f"que el hueco de cierre ronda los {f1(G.GAP)} cm y que la guarda cubre hasta los nudillos sin tocar los dedos al cerrar el puño."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = fig_row(c, y - 0.05, [fig_heat, fig_mold, fig_ring]) - 0.4
    box(c, 1.6, y - 4.3, 17.8, 4.3, fill=WARM, stroke=ACCENT)
    text(c, "Consejos de termoformado", 2.0, y - 0.65, 10, "LS-B", ACCENT)
    bullets(c, ["Si la goma brilla o hace burbujas, la pistola está demasiado cerca: aléjala y muévela más.",
                "Calienta las dos caras: la curva aguanta mejor y la superficie no se marca.",
                "Nunca te pongas una pieza caliente: espera a que esté tibia para probarla sobre el brazo.",
                "Una pieza mal formada se puede recalentar todas las veces que haga falta antes de sellarla.",
                "Forma siempre antes de pegar: una pieza pegada en plano tira de las uniones al curvarla."],
            2.0, y - 1.0, 17.0, "buls", 0.08)
    assert y - 4.3 > 1.7, y


def page_tut3(c):
    chrome(c, 15, TOTAL, "PARTE III · TUTORIAL 3/5", "Montaje: base, puño, panel, cinchas y daga")
    steps = [
        (12, "Pega con cemento de contacto", "Capa fina en las dos superficies; espera <b>5–10 min</b> a que no se pegue al dedo y une. "
             "No se puede recolocar: presenta primero en seco y marca con lápiz."),
        (13, "Puño sobre la base", "Pega el puño por fuera, cubriendo los 2 cm inferiores de la base (línea naranja de P3). El cm inferior queda "
             "<b>libre</b> (vuelo): ahí irá la bisagra de la guarda. Alinea los bordes de cierre."),
        (14, "Panel sobre la base", "Pega el panel siguiendo la guía marrón de P1, centrado en la cara exterior y por encima del puño. "
             "Antes de pegar, comprueba que las ranuras coinciden con las guías azules de las cinchas."),
        (15, "Tramos anteriores y posteriores (B·a, B·p)", "Pégalos sobre la base en las guías azules, desde el borde del panel hasta el borde de cierre. "
             "Los posteriores sobresalen 2 cm con su punta (lado del pico). En los anteriores irán las hebillas."),
        (16, "Tramos centrales (B·c)", "Bisela las puntas, pon cemento en la punta y en la pared de la ranura y mete cada extremo 0,6 cm en su ranura. "
             "Así parece que la cincha pasa por debajo del panel. En el brazo izquierdo usa B1c·daga y B2c·daga, más largas."),
        (17, "Daga (brazo izquierdo)", "Pega G2 centrada sobre G1, y H1, H2 y H3 en parejas. Monta la funda en la guía naranja <b>antes</b> de las centrales B1c y B2c, "
             "que pasan por encima y la sujetan. La empuñadura va <b>encima</b> de la B3c."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = fig_row(c, y - 0.05, [fig_bevel, fig_slot, fig_dagger]) - 0.4
    y = para(c, "Tiempos orientativos para un par de braceras", 1.6, y, 17.8, "h3") - 0.1
    y = table(c, [["Fase", "Tiempo", "Notas"],
                  ["Prueba en papel, impresión y plantillas", "1 h", "Reutilizables"],
                  ["Trazado, corte, ranuras y biselado", "3–4 h", "Las curvas de la base y las ranuras llevan más tiempo"],
                  ["Termoformado y prueba en seco", "1 h", "Con ayuda para sujetar es más fácil"],
                  ["Montaje de capas, cinchas y daga", "3 h + secados", "5–10 min de secado del cemento por unión"],
                  ["Guarda (A o B) y bisagra", "1–2 h", "La versión B lleva el doble de tiempo"],
                  ["Sellado, pintura y barniz", "4 h + secados", "Repártelo en dos días"]],
              1.6, y, [6.6, 2.8, 8.4], pad=2.2)
    assert y > 1.7, y


def page_tut4(c):
    chrome(c, 16, TOTAL, "PARTE III · TUTORIAL 4/5", "Guarda de mano: versiones A y B")
    y = para(c, "Las dos versiones se unen al puño igual. La guarda <b>no se pega rígida</b> al brazal: cruza la muñeca y tiene que poder moverse.", 1.6, PH - 2.2, 17.8, "b")
    # dibujos A y B
    sc = 0.55
    yt = y - 0.3
    box(c, 1.6, yt - 9.2, 8.75, 9.2, fill=colors.white)
    box(c, 10.65, yt - 9.2, 8.75, 9.2, fill=colors.white)
    text(c, "Versión A · placa única (película)", 2.0, yt - 0.6, 9.5, "LS-B", BROWN)
    text(c, "Versión B · segmentada (ref. 2)", 11.05, yt - 0.6, 9.5, "LS-B", BROWN)
    ga = G.guard_plate()
    ox, oy = 1.6 + 8.75 / 2, yt - 1.2 - G.GUARD_TOP * sc
    draw_poly(c, ga, fill=COL["guard"], stroke=DARK, lw=0.7, ox=ox, oy=oy, sc=sc)
    draw_poly(c, ga.buffer(-0.6), stroke=colors.HexColor("#D8A877"), lw=0.6, dash=(1.5, 1.5), ox=ox, oy=oy, sc=sc)
    ox2 = 10.65 + 8.75 / 2
    draw_poly(c, G.guard_seg_base(), fill=COL["guard_base"], stroke=DARK, lw=0.7, ox=ox2, oy=oy, sc=sc)
    for i in range(4):
        draw_poly(c, G.guard_strip(i), fill=COL["guard"], stroke=DARK, lw=0.5, ox=ox2, oy=oy, sc=sc)
    draw_poly(c, G.guard_band(), fill=colors.HexColor("#9C6A40"), stroke=DARK, lw=0.5, ox=ox2, oy=oy, sc=sc)
    for x0 in (ox, ox2):
        c.saveState(); c.setStrokeColor(GRAYT); c.setDash(1.5, 1.5)
        for x, yy in ((-2.2, -8.3), (2.2, -8.3), (0, -9.4)) if x0 == ox else ((-3.3, -8.2), (-1.1, -9.2), (1.1, -9.4), (3.3, -8.6)):
            c.circle((x0 + x * sc) * CM, (oy + yy * sc) * CM, 0.12 * CM, stroke=1, fill=0)
        c.restoreState()
    text(c, "remaches: fuera del PDF (gris)", ox, yt - 8.85, 6.5, "LS-I", GRAYT, "c")
    text(c, "base oscura + 4 láminas + banda", ox2, yt - 8.85, 6.5, "LS-I", GRAYT, "c")
    y = yt - 9.5
    steps = [
        (18, "Versión A", "Forma la placa, graba la línea a 0,6 cm del borde (pirograbador o cúter + calor) y lija los cantos hasta redondearlos."),
        (19, "Versión B", "Forma la base oscura. Pega las láminas L1–L4 sobre sus guías azules (hoja P5), de dentro afuera, dejando 2–3 mm de base visible entre ellas. "
             "Pega encima la banda (guía naranja), que tapa el arranque de las láminas."),
        (20, "Bisagra", "Corta una tira de elástico plano o polipiel de 3 × 8 cm. Pega 4 cm por la cara interior de la guarda, en la franja superior, y los otros 4 cm por dentro del "
             "vuelo del puño. La guarda queda colgando bajo el puño y se mueve con la muñeca."),
        (21, "Sujeción a la mano", "Un lazo de elástico al dedo corazón o una tira por la palma, pegados por dentro de la guarda cerca de los nudillos, "
             "evitan que la guarda se levante (fuera del PDF)."),
    ]
    y = steps_block(c, y, steps)
    y = fig_row(c, y - 0.05, [fig_hinge, fig_bevel, fig_heat])
    assert y > 1.7, y


def page_tut5(c):
    chrome(c, 17, TOTAL, "PARTE III · TUTORIAL 5/5", "Sellado, pintura, cierre y soluciones")
    steps = [
        (22, "Sella", "Lija suave (240), limpia el polvo y aplica 2–3 capas finas de Plasti Dip o de vinílica/PVA diluida 1:1, con 20–30 min de secado entre capas. "
             "Sella también el interior de las ranuras."),
        (23, "Pinta", "Imprimación negra y acrílico en capas finas. La base oscura <b>no</b> es negra pura: un gris marrón oscuro con dry brush más claro da sensación de cuero."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = table(c, [["Pieza", "Color base", "Código", "Acabado"],
                  ["1 · Base y 4B base", "Gris marrón muy oscuro", "#3A3836", "Dry brush #5E5751 en aristas y pico"],
                  ["2 · Panel, 4A y láminas 4B", "Marrón cuero", "#8A5530", "Dry brush #B07A4E; lavado oscuro en la costura"],
                  ["3 · Puño", "Negro grafito", "#2A2928", "Dry brush gris #55555A"],
                  ["Cinchas", "Negro", "#1A1A1A", "Aristas gastadas en gris"],
                  ["Funda G1/G2", "Gris oscuro", "#4A4A4D", "Costura gris claro #8A8A90"],
                  ["Empuñadura H1", "Marrón oscuro", "#5A3820", "Cordón negro"],
                  ["Guarda H2 y pomo H3", "Plata", "#B8B8BC", "Base negra + plata; lavado negro"]],
              1.6, y - 0.05, [4.6, 4.0, 2.0, 7.2], pad=2.1)
    y -= 0.35
    steps2 = [
        (24, "Envejece y protege", "Lavado de marrón muy oscuro en juntas, ranuras y costuras; retira el exceso con un trapo. Después, dos capas de barniz mate."),
        (25, "Cierre y herrajes (fuera del PDF)", "Hebillas en los tramos anteriores B·a y correa o las puntas de los B·p para cerrar el hueco interior. "
             "Remaches en el pico y la guarda, anillas D en las puntas. Si no quieres hebillas, pon velcro por dentro de los bordes de cierre."),
    ]
    y = steps_block(c, y, steps2)
    y = para(c, "Problemas frecuentes", 1.6, y - 0.05, 17.8, "h3")
    y = table(c, [["Problema", "Causa", "Solución"],
                  ["El pico se clava al doblar el codo", "Pico poco abierto o muy alto", "Recalienta y ábrelo hacia fuera; o rebaja 1 cm con lija"],
                  ["El panel hace bolsa", "Formado en plano, no sobre la base", "Recalienta y fórmalo sobre la base"],
                  ["La cincha central se sale de la ranura", "Poco cemento en la pared", "Bisela más la punta y pon cemento en la ranura y en la punta"],
                  ["La guarda se levanta", "Sin sujeción a la mano", "Añade lazo de dedo o tira de palma"],
                  ["Se agrieta la pintura", "Sin sellar o capas gruesas", "Sella y pinta en capas finas"]],
              1.6, y - 0.05, [5.2, 5.0, 7.6], pad=2.0)
    y = checklist(c, 1.6, y - 0.3, 17.8, "Lista de control final",
                  ["Cierra con ~3 cm de hueco", "El codo dobla sin rozar el pico", "La guarda sigue a la muñeca",
                   "Cinchas firmes en las ranuras", "Daga fija bajo B1c/B2c", "Barniz seco antes de los herrajes"], cols=2)
    assert y > 1.7, y


# ================================================================== PÁG 18 · TALLAS
def base_for(d):
    old = G.R0
    G.R0 = (G.C0 + d) / G.THETA
    b = G.base()
    G.R0 = old
    return b


def page_sizes(c):
    chrome(c, 18, TOTAL, "PARTE III · ANEXO", "Adaptar el patrón a otra talla")
    y = para(c, f"Mide tu antebrazo <b>sobre la manga</b> a {f1(G.BASE_Y0)} cm y a {f1(G.BASE_Y1)} cm del pliegue de la muñeca y calcula:", 1.6, PH - 2.2, 17.8, "b")
    box(c, 1.6, y - 1.75, 17.8, 1.55)
    para(c, "<b>C = perímetro + 1,5 (holgura) + 1,57 (grosor)</b> &nbsp;·&nbsp; <b>θ = (C1 − C0) / 15,5</b> &nbsp;·&nbsp; <b>R0 = C0 / θ</b> &nbsp;·&nbsp; <b>R1 = R0 + 15,5</b><br/>"
            f"Hueco de cierre: dos cortes paralelos a la costura, a {f1(G.GAP / 2)} cm de ella. Pico: súmalo con la tabla de alturas de abajo.",
         1.9, y - 0.4, 17.2, "s")
    y -= 2.15
    y = para(c, "Tallas orientativas (mismo largo; perímetros desplazados)", 1.6, y, 17.8, "h2")
    rows = [["Talla", "Perímetro a 3 / 18,5 cm", "C0 / C1", "R0 / R1", "Base (ancho × alto)", "¿Cabe en 1 hoja A4?"]]
    for name, d in (("S", -1.5), ("M (esta guía)", 0.0), ("L", 1.5), ("XL", 3.0)):
        b = base_for(d)
        w, h = G.size(b)
        fits = w <= 28.5 and h <= 19.8
        rows.append([name, f"{f1(G.skin(G.BASE_Y0) + d)} / {f1(G.skin(G.BASE_Y1) + d)}", f"{f1(G.C0 + d)} / {f1(G.C1 + d)}",
                     f"{f1((G.C0 + d) / G.THETA)} / {f1((G.C0 + d) / G.THETA + G.H)}", f"{f1(w)} × {f1(h)}",
                     "Sí" if fits else "No: baja 1 cm el pico o divide la base"])
    y = table(c, rows, 1.6, y - 0.1, [2.6, 3.6, 2.6, 2.6, 3.0, 3.4], pad=2.4)
    y = para(c, "Si no cabe: reduce el pico o divide la base por la línea central, bajo el panel, que tapa la unión. Une las mitades con cemento y una tira interior. "
                "Panel, puño y cinchas se recalculan con el mismo θ y el radio desplazado (+7,3 cm por cada capa de 5 mm).", 1.6, y - 0.2, 17.8, "s")
    y -= 0.35
    y = para(c, "Altura del pico sobre el borde superior (para dibujarlo a mano)", 1.6, y, 17.8, "h2")
    ss = list(range(-8, 12, 2))
    y = table(c, [["Arco desde el eje (cm)"] + [f"{s:+d}" for s in ss], ["Altura del pico (cm)"] + [f1(G.peak(s)) for s in ss]],
              1.6, y - 0.1, [3.8] + [1.4] * len(ss), zebra=False, pad=2.4)
    y = para(c, "El eje es el centro de la cara exterior; los valores positivos van hacia el lado posterior (codo). Mide el arco sobre el borde superior "
                "con cinta flexible y marca la altura en perpendicular. Une los puntos con una curva suave.", 1.6, y - 0.15, 17.8, "xs")
    y -= 0.35
    y = para(c, "Cómo dibujar la base a mano (compás de cuerda)", 1.6, y, 17.8, "h2")
    y = bullets(c, [
        "Clava una chincheta (ápice) en papel grande y traza con hilo y lápiz dos arcos de radio R0 y R1.",
        "Marca el ángulo θ midiendo sobre el arco R0 la longitud C0, y une los extremos con el ápice: es la costura.",
        f"Traza dos rectas paralelas a esa costura, a {f1(G.GAP / 2)} cm hacia dentro: son los bordes de cierre.",
        "Suma el pico con la tabla de alturas y redondea las esquinas 2–3 mm.",
    ], 1.6, y, 17.8, "bul", 0.08, mark="→")
    # esquema del desarrollo
    sc = 0.13
    full = Polygon([G.pol(G.R0 + G.H, -G.THETA / 2 + G.THETA * i / 60) for i in range(61)] +
                   [G.pol(G.R0, G.THETA / 2 - G.THETA * i / 60) for i in range(61)])
    ax, ay = 10.5, y - 0.6
    flip = lambda p: affinity.scale(p, 1, -1, origin=(0, 0))
    draw_poly(c, flip(full), stroke=colors.HexColor("#9A9A9A"), lw=0.6, dash=(3, 2), ox=ax, oy=ay, sc=sc)
    draw_poly(c, flip(G.base()), fill=colors.HexColor("#D9D4CF"), stroke=DARK, lw=0.9, ox=ax, oy=ay, sc=sc)
    for sg in (-1, 1):
        X, Y = G.pol(G.R0 + G.H, sg * G.THETA / 2)
        draw_line(c, [(ax, ay), (ax + X * sc, ay - Y * sc)], GUIDE, 0.6, (3, 2))
    c.setFillColor(ACCENT)
    c.circle(ax * CM, ay * CM, 0.1 * CM, stroke=0, fill=1)
    text(c, "ápice", ax + 0.25, ay - 0.05, 7, "LS-B", ACCENT)
    text(c, f"R0 = {f1(G.R0)}", ax - 1.2, ay - G.R0 * sc * 0.55, 7, "LS-B", GUIDE, "r")
    text(c, f"θ = {f1(math.degrees(G.THETA))}°", ax, ay - 1.6, 7, "LS-B", GUIDE, "c")
    text(c, "gris discontinuo: sector completo · relleno: base con hueco de cierre y pico (hacia abajo en este esquema)",
         ax, ay - (G.R0 + G.H + G.PEAK_H) * sc - 0.45, 6.6, "LS-I", GRAYT, "c")
    assert ay - (G.R0 + G.H + G.PEAK_H) * sc - 0.45 > 1.7, ay


# ================================================================== main
def pattern_pages():
    return (page_p1, page_p2, page_p3, page_p4, page_p5, page_p6)


def build():
    c = canvas.Canvas(OUT, pagesize=(PW * CM, PH * CM))
    c.setTitle("Braceras de Hipo · Versión completa · Patrones EVA 5 mm y tutorial")
    c.setSubject("4 piezas principales por bracera · patrones 1:1 en A4 · tutorial")
    for fn in (page_cover, page_analysis, page_parts, page_measures, page_materials, page_assembly):
        c.setPageSize((PW * CM, PH * CM))
        fn(c)
        c.showPage()
    for fn in pattern_pages():
        c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
        fn(c)
        c.showPage()
    for fn in (page_tut1, page_tut2, page_tut3, page_tut4, page_tut5, page_sizes):
        c.setPageSize((PW * CM, PH * CM))
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT)


def build_print():
    c = canvas.Canvas(OUT_PRINT, pagesize=(G.PAGE_W * CM, G.PAGE_H * CM))
    c.setTitle("Braceras de Hipo · Guías A4 para imprimir (escala 1:1)")
    for fn in pattern_pages():
        c.setPageSize((G.PAGE_W * CM, G.PAGE_H * CM))
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT_PRINT)


if __name__ == "__main__":
    build()
    build_print()
