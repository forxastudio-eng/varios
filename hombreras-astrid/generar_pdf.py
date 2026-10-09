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
TOTAL = 9


def sz(p):
    w, h = G.size(p)
    return f"{f1(w)} × {f1(h)}"


def lames(v):
    cfg = G.VERS[v]
    return [G.lame(cfg["widths"], i, cfg["tip"]) for i in range(5)]


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


def draw_pauldron(c, ox, oy, sc, v="B", upto=5, hl=None, mirror=False, az=30, el=24, rivets=True, disc=True, ghost_rivets=False):
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
            tlist = [40, 68, 94] if (v == "A" or dphi >= 16) else [55, 90]
            for td in tlist:
                t = math.radians(td)
                if t < t0 + 0.15:
                    continue
                pc = G.sphere_pt(t, p_up + dphi * 0.55, rr + 0.3)
                X, Y = proj(pc)
                rad = 0.34 * sc * (1.6 if v == "A" else 1.3)
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
    text(c, "P (polo = disco)", P[0] + 0.25, P[1] + 0.1, 7.5, "LS-B", ACC)
    return proj


# ================================================================== PÁG 1 · FICHA
def page_ficha(c):
    c.setFillColor(HEAD)
    c.rect(0, (PH - 6.2) * CM, PW * CM, 6.2 * CM, stroke=0, fill=1)
    c.setFillColor(ACC)
    c.rect(0, (PH - 6.35) * CM, PW * CM, 0.15 * CM, stroke=0, fill=1)
    text(c, "FICHA TÉCNICA DE COSPLAY · PATRONES + TUTORIAL", 1.8, PH - 1.8, 9.5, "LS-B", colors.HexColor("#E8D3B8"))
    text(c, "HOMBRERAS DE ASTRID", 1.8, PH - 3.4, 32, "LS-B", colors.white)
    text(c, "Cómo entrenar a tu dragón 2", 1.8, PH - 4.35, 14, "LS-I", colors.HexColor("#D5DEE6"))
    text(c, "Goma EVA 5 mm · patrones 1:1 en A4 · 2 versiones · talla de referencia mujer 1,65 m", 1.8, PH - 5.3, 10, "LS", colors.white)
    # ilustración
    draw_pauldron(c, 5.4, PH - 12.6, 0.37, "B", mirror=True)
    draw_pauldron(c, 15.6, PH - 12.6, 0.37, "B")
    text(c, "Versión B · abanico con disco (izquierda y derecha)", PW / 2, PH - 16.3, 7.5, "LS-I", GRAYT, "c")
    y = PH - 16.8
    data = [["Dato", "Valor"],
            ["Pieza", "Par de hombreras (pauldrons) de láminas solapadas en teja que convergen en un disco frontal"],
            ["Piezas por hombrera", "5 láminas · versión B: + disco de 2 capas"],
            ["Medida de una lámina", f"≈ {sz(lames('A')[2])} cm (la mayor de B: {sz(lames('B')[4])} cm)"],
            ["Tamaño montada", f"≈ {f1(G.R * G.T_END + 3)} cm de delante a atrás · {f1(G.R * math.sin(G.T_END) * math.radians(75))} cm de alto atrás"],
            ["Goma EVA", "2 hojas A4 de 5 mm por versión (una por hombrera) + 1 de repuesto"],
            ["Técnicas", "Termoformado en cúpula (sobre un balón), montaje en tejas, textura martillada, pintura metálica"],
            ["Dificultad · tiempo", "Media · 6–8 h el par (sin contar secados)"],
            ["Fuera de la ficha", "Remaches, correas y hebillas de sujeción, calaveras de la falda (3.ª foto)"]]
    y = table(c, data, 1.6, y, [4.2, 13.6], pad=2.6)
    y -= 0.35
    box(c, 1.6, y - 2.15, 8.75, 2.15, fill=CREAM)
    text(c, "Versión A · 5 láminas (ref. 1)", 1.9, y - 0.6, 9, "LS-B", HEAD)
    para(c, "Cinco láminas iguales que acaban en punta; sin disco. Remaches grandes. Hoja <b>PA</b>.", 1.9, y - 0.85, 8.2, "s")
    box(c, 10.65, y - 2.15, 8.75, 2.15, fill=CREAM)
    text(c, "Versión B · abanico con disco (ref. 2)", 10.95, y - 0.6, 9, "LS-B", HEAD)
    para(c, "Dos láminas anchas abajo y tres gajos estrechos arriba, rematados con un disco doble. Hoja <b>PB</b>.", 10.95, y - 0.85, 8.2, "s")


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
    y = para(c, "Las tres fotos muestran la misma construcción: una <b>cúpula de láminas</b> que nacen todas de un mismo punto en la parte delantera del "
                "hombro y se abren hacia atrás como un abanico. Cada lámina pisa a la de debajo como una teja. La ref. 1 son 5 láminas iguales que "
                "terminan en punta; las refs. 2 y 3 usan láminas de ancho distinto rematadas con un <b>disco doble</b>.", 1.6, PH - 2.2, 17.8, "b")
    sc = 0.46
    ox, oy = 7.4, y - 6.6
    proj = draw_pauldron(c, ox, oy, sc, "B")
    cfg = G.VERS["B"]
    w = cfg["widths"]

    def on(i, td, frac=0.5, dr=0.3):
        p_up = G.PHI0 + sum(w[:i])
        return proj(G.sphere_pt(math.radians(td), p_up + w[i] * frac, G.R + dr))
    callout(c, 1, on(4, 70, 0.7), (ox + 7.6, oy - 3.0))
    callout(c, 2, on(1, 45, 0.5, 0.4), (ox - 1.0, oy + 5.0))
    callout(c, 3, proj((G.R + 1.2, 0, 0)), (ox - 4.6, oy - 2.4))
    callout(c, 4, on(3, 68, 0.55, 0.6), (ox + 7.6, oy + 1.2))
    callout(c, 5, on(3, 99, 0.95), (ox + 7.6, oy + 3.4))
    data = [["", "Qué se ve", "Cómo lo resolvemos"],
            ["1", "Láminas curvas que rodean el hombro; la inferior es la más ancha.", "Gajos de esfera (como la piel de una naranja) desarrollados en plano."],
            ["2", "Gajos estrechos arriba que convergen en el disco (ref. 2 y 3).", "Versión B: 3 gajos de 13° + láminas de 16° y 20°."],
            ["3", "Disco doble que tapa la convergencia.", "Disco Ø 6,6 + disco Ø 5,0 en dos capas."],
            ["4", "Remaches grandes y abombados, 2–3 por lámina.", "Fuera de la ficha; las hojas marcan dónde van."],
            ["5", "Cada lámina pisa a la de abajo, con el canto visible.", "Solape de teja de 1 cm marcado en el patrón."],
            ["—", "Textura martillada y arañazos (ref. 2 y 3).", "Acabado: bola de aluminio con calor + cortes."],
            ["—", "Calaveras pequeñas (ref. 3).", "Son de la falda de Astrid: no forman parte de las hombreras."]]
    yt = table(c, data, 1.6, oy - 5.0, [0.6, 8.2, 9.0], pad=2.4)
    yb = para(c, "Claves de construcción", 1.6, yt - 0.4, 17.8, "h2")
    yb = bullets(c, [
        "<b>Un solo punto de convergencia (polo):</b> todas las láminas nacen ahí. Es la parte delantera del hombro y se tapa con el disco (B) o con las puntas (A).",
        "<b>Izquierda y derecha:</b> las láminas son simétricas respecto a su eje salvo el solape. Para la hombrera derecha corta las mismas piezas y úsalas "
        "por la otra cara (si tu goma tiene una cara mejor, da la vuelta a la plantilla).",
        "<b>Se monta en tejas de abajo arriba</b> sobre un molde redondo (un balón de baloncesto tiene casi el mismo radio que la cúpula).",
    ], 1.6, yb, 17.8, "bul", 0.1)
    assert yb > 1.7, yb


# ================================================================== PÁG 3 · GEOMETRÍA Y MEDIDAS
def page_geometry(c):
    chrome(c, 3, TOTAL, "MEDIDAS", "Geometría y medidas")
    y = para(c, f"La hombrera es un casquete esférico de radio <b>R = {f1(G.R)} cm</b> (fibra neutra). Las láminas son <b>gajos</b> entre meridianos "
                f"que salen del polo P, igual que la piel de una naranja, y se extienden {round(math.degrees(G.T_END))}° desde el polo hasta el borde trasero.",
             1.6, PH - 2.2, 17.8, "b")
    draw_gores_diagram(c, 5.3, y - 4.4, 3.6)
    text(c, "5 gajos (versión B) sobre la esfera", 5.3, y - 8.6, 7.5, "LS-I", GRAYT, "c")
    # gajo en plano
    X0 = 10.2
    yy = para(c, "Desarrollo en plano de un gajo", X0, y - 0.1, 9.2, "h3")
    gl = G.lame([30, 30, 30, 30, 30], 1, 0.6)
    w, h = G.size(gl)
    sc = 8.6 / w
    draw_poly(c, G.at(gl, 0, 0), fill=colors.HexColor("#C9CFD5"), stroke=DARK, lw=0.8, ox=X0, oy=yy - 0.4 - h * sc, sc=sc)
    para(c, "x = R · t (arco desde el polo)<br/>ancho(t) = R · sen t · Δφ<br/>+ 1 cm de solape en el borde superior", X0, yy - 0.6 - h * sc, 9.2, "s")
    y = y - 9.2
    data = [["Cód.", "Medida (mujer 1,65 m, complexión normal)", "Valor"],
            ["M1", "Arco del hombro de delante a atrás, a la altura del deltoides, sobre la ropa", "≈ 24 cm"],
            ["M2", "Altura cubierta en la parte trasera (de cerca del cuello al brazo)", f"{f1(G.R * math.sin(G.T_END) * math.radians(75))} cm"],
            ["M3", "Perímetro del brazo en el deltoides (correa de sujeción)", "≈ 29 cm"],
            ["R", "Radio de la cúpula: R = (M1 − 2) / 1,745", f"{f1(G.R)} cm"],
            ["Δφ", "Ángulo total de las 5 láminas: Δφ = M2 / (0,985 · R)", "75°"],
            ["OV", "Solape de teja (borde superior de cada lámina, salvo la de arriba)", f"{f1(G.OV)} cm"]]
    y = table(c, data, 1.6, y, [1.3, 13.6, 2.9], pad=2.4)
    y = para(c, "Reparto de las láminas (de arriba abajo)", 1.6, y - 0.4, 17.8, "h3") - 0.1
    rows = [["Versión", "L1 (arriba)", "L2", "L3", "L4", "L5 (abajo)", "Punta"]]
    for v in ("A", "B"):
        cfg = G.VERS[v]
        rows.append([f"{v} · {cfg['name']}"] + [f"{d}° · {f1(G.size(p)[1])} cm" for d, p in zip(cfg["widths"], lames(v))] +
                    ["en punta" if v == "A" else "bajo el disco"])
    y = table(c, rows, 1.6, y, [4.4, 2.3, 2.1, 2.1, 2.1, 2.4, 2.4], pad=2.3)
    y -= 0.35
    box(c, 1.6, y - 3.9, 17.8, 3.9, fill=WARM, stroke=ACC)
    text(c, "Cómo tomar las medidas", 1.95, y - 0.65, 10, "LS-B", ACC)
    bullets(c, ["Con la camisa y el chaleco de cuero puestos, mide con cinta flexible desde el pliegue delantero de la axila, pasando por encima "
                "del hombro, hasta el pliegue trasero (<b>M1</b>), a unos 4 cm por debajo del hueso del hombro.",
                "Mide en la espalda desde unos 3 cm del cuello hasta la mitad del brazo (<b>M2</b>).",
                "Si tus medidas cambian más de 1 cm, recalcula R con la fórmula o usa la tabla de tallas (pág. 9)."],
            1.95, y - 1.0, 17.1, "buls", 0.1)


# ================================================================== PÁG 4 · DESPIECE Y MONTAJE
def page_parts(c):
    chrome(c, 4, TOTAL, "DESPIECE", "Piezas y orden de montaje")
    y = para(c, "Cada hombrera tiene 5 láminas (L1 arriba, junto al cuello · L5 abajo, sobre el brazo). Se cortan dos juegos iguales, uno por hombrera.",
             1.6, PH - 2.2, 17.8, "b")
    for col, v in enumerate(("A", "B")):
        x = 1.6 + col * 9.05
        box(c, x, y - 10.3, 8.75, 10.0)
        text(c, f"Versión {v} · {G.VERS[v]['name']}", x + 0.3, y - 0.95, 9.5, "LS-B", HEAD)
        yy = y - 1.4
        sc = 0.27
        for i, p in enumerate(lames(v)):
            q = G.at(p, 0, 0)
            w, h = G.size(q)
            draw_poly(c, q, fill=STEEL[i], stroke=DARK, lw=0.5, ox=x + 0.4, oy=yy - h * sc, sc=sc)
            text(c, f"{G.VERS[v]['ids'][i]} · {sz(p)}", x + 0.6 + w * sc, yy - h * sc / 2 - 0.1, 7, "LS-B", DARK)
            yy -= h * sc + 0.2
        if v == "B":
            for k, d in enumerate((G.DISC_D, G.DISC2_D)):
                cx = x + 1.4 + k * 2.6
                c.setFillColor(colors.HexColor("#9AA3AB") if k == 0 else colors.HexColor("#B4BBC2"))
                c.setStrokeColor(DARK)
                c.circle(cx * CM, (yy - 1.0) * CM, d / 2 * sc * CM, stroke=1, fill=1)
                text(c, f"D{k + 1} Ø {f1(d)}", cx + 1.25 - k * 0.3, yy - 1.1, 7, "LS-B", DARK)
    y -= 10.7
    y = para(c, "Orden de montaje (versión B; la A igual pero sin disco)", 1.6, y, 17.8, "h2")
    cards = [("1 · L5 sobre el molde", 1, "Forma L5 sobre el balón; es la base de la teja."),
             ("2 · L4 sobre L5", 2, "Su canto inferior pisa 1 cm de L5. Pega y presiona."),
             ("3 · L3", 3, "Igual, siguiendo la línea de solape marcada."),
             ("4 · L2 y L1", 5, "Los gajos superiores cierran la cúpula hacia el cuello."),
             ("5 · Disco", 6, "D1 + D2 pegados sobre el polo tapan las puntas.")]
    cw = 3.4
    for k, (title, upto, desc) in enumerate(cards):
        x = 1.6 + k * (cw + 0.2)
        box(c, x, y - 6.6, cw, 6.4)
        text(c, title, x + 0.2, y - 0.6, 7.8, "LS-B", HEAD)
        draw_pauldron(c, x + cw / 2, y - 3.0, 0.15, "B", upto=min(upto, 5), hl=("disc" if upto == 6 else 5 - min(upto, 5)), rivets=False,
                      disc=(upto == 6))
        p = Paragraph(desc, ST["xs"])
        _, h = p.wrap((cw - 0.4) * CM, 1000)
        p.drawOn(c, (x + 0.2) * CM, (y - 6.4) * CM + 0.0)
    y -= 7.0
    y = para(c, "Lista de piezas y hojas", 1.6, y, 17.8, "h3") - 0.1
    y = table(c, [["Versión", "Piezas por hombrera", "Hojas de EVA (par)", "Hoja de patrón"],
                  ["A", "A1–A5 (5 láminas)", "2", "PA (imprimir una vez, cortar en 2 hojas)"],
                  ["B", "B1–B5 (5 láminas) + D1 + D2", "2", "PB (imprimir una vez, cortar en 2 hojas)"]],
              1.6, y, [2.0, 6.0, 3.6, 6.2], pad=2.4)
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


def layout(v):
    cfg = G.VERS[v]
    pieces = [(cfg["ids"][i], p) for i, p in enumerate(lames(v))][::-1]
    placed, left = G.drop_pack(pieces, x_cands="left")
    assert not left, left
    return placed


def _pack_left(pieces):
    return G.drop_pack(pieces, x_cands="left")


def pattern_page(c, v):
    cfg = G.VERS[v]
    code = "PA" if v == "A" else "PB"
    text(c, f"HOJA {code} · VERSIÓN {v} · LÁMINAS {cfg['ids'][0]}–{cfg['ids'][-1]}" + (" + DISCO" if v == "B" else ""), 0.8, 20.05, 9.5, "LS-B", HEAD)
    text(c, "Imprimir al 100 % · A4 horizontal · negro = corte · naranja = borde de la lámina superior (solape) · azul = remaches (opcional)"
            + (" · gris = borde del disco" if v == "B" else "") + " · cortar 2 juegos", 0.8, 19.72, 6.4, "LS", GRAYT)
    ruler(c)
    pieces = [(cfg["ids"][i], p, i) for i, p in enumerate(lames(v))][::-1]
    placed, occupied, discs = _layout(v)
    for key, base, i in pieces:
        P, rot = placed[key]
        cen = base.centroid
        rb = affinity.rotate(base, rot, origin=cen) if rot else base
        dx, dy = P.bounds[0] - rb.bounds[0], P.bounds[1] - rb.bounds[1]

        def tf(x, y):
            if rot:
                x, y = 2 * cen.x - x, 2 * cen.y - y
            return (x + dx, y + dy)
        draw_poly(c, P, stroke=DARK, lw=1.4)
        widths = cfg["widths"]
        dphi = math.radians(widths[i])
        t0 = cfg["tip"] / G.R
        ts = [t0 + (G.T_END - t0) * k / 60 for k in range(61)]
        if i > 0:   # línea de solape: zona que queda bajo la lámina superior
            pts = [tf(G.R * t, G.R * math.sin(t) * dphi / 2) for t in ts[4:]]
            ln = LineString(pts).intersection(P.buffer(-0.15))
            for g in getattr(ln, "geoms", [ln]):
                if not g.is_empty:
                    draw_line(c, list(g.coords), ACC, 0.9, (4, 2))
        # eje
        draw_line(c, [tf(G.R * t0 + 0.6, 0), tf(G.R * G.T_END - 0.6, 0)], colors.HexColor("#B5B5B5"), 0.5, (6, 3, 1, 3))
        # remaches (guía)
        for x, y in G.rivets(widths, i, cfg["tip"], version=v):
            X, Y = tf(x, y)
            c.saveState()
            c.setStrokeColor(GUIDE)
            c.setLineWidth(0.6)
            c.setDash(1.5, 1.5)
            c.circle(X * CM, Y * CM, 0.45 * CM, stroke=1, fill=0)
            c.restoreState()
        if v == "B":   # borde del disco
            r = G.DISC_D / 2
            pts = [tf(r, y) for y in [-2.5 + 5 * k / 10 for k in range(11)]]
            ln = LineString(pts).intersection(P)
            if not ln.is_empty:
                draw_line(c, list(ln.coords), colors.HexColor("#7A7A7A"), 0.7, (2, 2))
        # textos
        mx, my = tf(G.R * 0.95, 0)
        text(c, f"{key}", mx, my - 0.12, 10, "LS-B", HEAD, "c")
        ex, ey = tf(G.R * 1.35, 0)
        text(c, f"{'arriba · cuello' if i == 0 else ('abajo · brazo' if i == 4 else 'lámina ' + str(i + 1))} · {sz(base)} cm", ex, ey - 0.1, 6.6, "LS", DARK, "c")
        tx, ty = tf(G.R * t0 + (1.8 if v == "A" else 2.0), 0)
        text(c, "← polo" if not rot else "polo →", tx + (0.0 if not rot else 0.0), ty - 0.1, 6.3, "LS-I", GRAYT, "c")
        bx, by = tf(G.R * G.T_END - 1.4, 0)
        text(c, "atrás", bx, by - 0.1, 6.3, "LS-I", GRAYT, "c")
        if i > 0:
            ux, uy = tf(G.R * 1.9, G.R * math.sin(1.9) * dphi / 2 + 0.45)
            text(c, "solape ↑" if not rot else "solape ↓", ux, uy - 0.1, 6.0, "LS-B", ACC, "c")
    for k, (dc, _) in discs.items():
        draw_poly(c, dc, stroke=DARK, lw=1.4)
        if k == "D1":
            draw_poly(c, Point(dc.centroid.x, dc.centroid.y).buffer(G.DISC2_D / 2), stroke=GUIDE, lw=0.6, dash=(2, 2))
        text(c, k, dc.centroid.x, dc.centroid.y - 0.12, 9, "LS-B", HEAD, "c")
        text(c, f"Ø {f1(G.DISC_D if k == 'D1' else G.DISC2_D)}", dc.centroid.x, dc.centroid.y - 0.55, 6.3, "LS", GRAYT, "c")


_CACHE = {}


def _layout(v):
    """Coloca las 5 láminas (y los discos en B) buscando el orden que deja sitio a todo."""
    if v in _CACHE:
        return _CACHE[v]
    import itertools
    cfg = G.VERS[v]
    L = [(cfg["ids"][i], p) for i, p in enumerate(lames(v))]
    for order in itertools.permutations(range(5)):
        placed, left = G.drop_pack([L[i] for i in order])
        if left:
            continue
        occ = [p for p, _ in placed.values()]
        discs = {}
        if v == "B":
            discs, dl = G.drop_pack([("D1", G.disc(G.DISC_D)), ("D2", G.disc(G.DISC2_D))], x_cands="grid", rots=(0,), blocked=occ)
            if dl:
                continue
        _CACHE[v] = (placed, occ, discs)
        return _CACHE[v]
    raise RuntimeError("no caben las piezas")


def page_pa(c):
    pattern_page(c, "A")


def page_pb(c):
    pattern_page(c, "B")


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
    text(c, "balón de baloncesto (R ≈ 12 cm) + film", x + 2.85, yt - 3.95, 6.6, "LS", DARK, "c")


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
    chrome(c, 7, TOTAL, "TUTORIAL 1/2", "Corte, termoformado y montaje en tejas")
    steps = [
        (1, "Imprime y comprueba", "Imprime la hoja PA o PB al 100 % (tamaño real) en horizontal y mide la regla de 10 cm. Recorta las plantillas por la línea gruesa."),
        (2, "Prueba en papel", "Antes de cortar goma, pega las 5 plantillas de papel con cinta sobre el hombro (con la ropa del traje puesta) siguiendo los solapes. "
            "El polo va delante del hombro y la lámina L5 sobre el brazo. Si queda pequeña o grande, recalcula R (pág. 9)."),
        (3, "Traza y corta", "Traza dos juegos sobre la cara lisa y corta con cuchilla nueva a 90°, en varias pasadas. Marca con punzón la línea de solape y los remaches."),
        (4, "Bisela", "Rebaja ligeramente a 45° el canto inferior visible de cada lámina, que es el que se ve como borde de teja, "
            "y lija las puntas que convergen en el polo para que no se amontonen."),
        (5, "Termoforma en cúpula", "Calienta cada lámina con la pistola, siempre en movimiento, y estírala sobre un <b>balón de baloncesto</b> (radio ≈ 12 cm) "
            "o un bol grande, cubierto con film. Presiona con las palmas hasta que copie la curva y deja enfriar 1–2 min."),
        (6, "Monta en tejas, de abajo arriba", "Con L5 sobre el balón, pega L4 de forma que su canto inferior pise la franja de solape de L5 (línea naranja), "
            "luego L3, L2 y L1. Todas las puntas apuntan al mismo polo. Usa cemento de contacto, deja secar 5–10 min y presiona."),
        (7, "Disco (versión B)", "Pega D2 centrado sobre D1, forma el conjunto ligeramente abombado y pégalo sobre el polo, tapando las puntas. "
            "En la versión A, recorta las puntas para que acaben en una sola punta limpia."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    for k, fn in enumerate((fig_ball, fig_shingle, fig_tips)):
        fn(c, 1.6 + k * 6.05, y - 0.05)
    y -= 4.75
    box(c, 1.6, y - 4.0, 17.8, 4.0, fill=WARM, stroke=ACC)
    text(c, "Consejos de profesional", 1.95, y - 0.65, 10, "LS-B", ACC)
    bullets(c, ["Forma <b>todas</b> las láminas antes de pegar: una lámina pegada en plano tira del solape y se despega.",
                "Si las láminas se abren al enfriar, recalienta el conjunto ya pegado sobre el balón y sujétalo con cinta.",
                "Pega primero la zona central de cada solape y después los extremos: así corriges pequeñas diferencias.",
                "Mantén las dos hombreras a la vista y monta la derecha como espejo de la izquierda (el polo siempre delante)."],
            1.95, y - 1.0, 17.1, "buls", 0.08)
    assert y - 4.0 > 1.7, y


def page_tut2(c):
    chrome(c, 8, TOTAL, "TUTORIAL 2/2", "Detalles, acabado metálico y sujeción")
    steps = [
        (8, "Textura martillada", "Haz una bola de papel de aluminio bien apretada, calienta la superficie 5 s y presiona la bola dando golpecitos. "
            "Para un martillado más marcado, usa la punta redonda de un bolígrafo o una pirograbadora con punta de bola."),
        (9, "Arañazos y golpes", "Haz cortes cortos y en diagonal con el cúter (1–2 mm de profundidad) y ábrelos con un golpe de calor. Pocos y bien colocados, como en la ref. 3."),
        (10, "Remaches (fuera de la ficha)", "Van en las marcas azules de cada lámina. Puedes usar medias bolas de madera o de plástico, foam clay o tachuelas de tapicería."),
        (11, "Sella", "Lija suave y aplica 3 capas finas de Plasti Dip o vinílica/PVA diluida 1:1, dejando secar 20–30 min entre capas."),
        (12, "Pinta de metal", "Imprimación negra y luego plata en <b>dry brush</b> (pincel casi seco) sobre las zonas altas. Un lavado de negro diluido en los huecos "
             "de la textura y en las juntas da el aspecto de acero viejo. Termina con barniz mate o satinado."),
        (13, "Sujeción (fuera de la ficha)", "Correa de cuero o elástico por dentro del borde trasero, que abrace el brazo (M3), y otra correa o broche hacia el peto. "
             "Pega una tira de EVA de refuerzo por dentro, donde se fije la correa."),
    ]
    y = steps_block(c, PH - 2.2, steps)
    y = table(c, [["Zona", "Color", "Código", "Técnica"],
                  ["Base de todas las piezas", "Negro mate", "#1E1E1E", "Imprimación en spray"],
                  ["Láminas y disco", "Plata / acero", "#A9B0B6", "Dry brush en capas, de menos a más"],
                  ["Remaches y canto de las láminas", "Plata clara", "#D3D8DC", "Toques en los puntos de luz"],
                  ["Huecos y juntas", "Lavado negro-marrón", "#2B2620", "Diluido 1:4, retirar el exceso"]],
              1.6, y - 0.1, [5.2, 3.6, 2.3, 6.7], pad=2.2)
    y = para(c, "Problemas frecuentes", 1.6, y - 0.4, 17.8, "h3") - 0.1
    y = table(c, [["Problema", "Causa", "Solución"],
                  ["Las láminas no convergen en un punto", "Pegadas sin seguir el eje", "Monta sobre el balón con una marca en el polo y alinea cada eje"],
                  ["Queda «plana» o con picos", "Formado insuficiente", "Recalienta sobre el balón y presiona con las palmas"],
                  ["Se ven huecos entre láminas", "Solape sin pegar en los extremos", "Pega toda la franja naranja"],
                  ["Los dos lados no son iguales", "Derecha montada igual que la izquierda", "Usa las láminas por la otra cara (espejo)"]],
              1.6, y, [5.6, 5.0, 7.2], pad=2.1)
    y = checklist(c, 1.6, y - 0.35, 17.8, "Lista de control final",
                  ["2 juegos de 5 láminas cortados", "Todas formadas sobre el balón", "Solapes pegados en toda su franja",
                   "Polo cubierto (disco o punta limpia)", "Textura, sellado y pintura", "Correas y remaches añadidos"], cols=2)
    assert y > 1.7, y


# ================================================================== PÁG 9 · TALLAS
def page_sizes(c):
    chrome(c, 9, TOTAL, "ANEXO", "Adaptar a otra talla")
    y = para(c, "Todas las piezas escalan con el radio de la cúpula. Mide <b>M1</b> (arco del hombro de delante a atrás) y calcula "
                "<b>R = (M1 − 2) / 1,745</b>. El factor de escala es <b>R / 12,5</b>: imprime las hojas PA/PB con ese porcentaje "
                "(p. ej. R = 13,5 → 108 %) y comprueba la regla, que medirá 10 cm × el factor.", 1.6, PH - 2.2, 17.8, "b")
    rows = [["Talla orientativa", "M1 (cm)", "R (cm)", "Escala de impresión", "Lámina mayor (cm)", "¿Caben 5 láminas en 1 A4?"]]
    for name, r in (("Mujer 1,55–1,60", 11.5), ("Mujer 1,65 (esta ficha)", 12.5), ("Mujer 1,72 / hombre delgado", 13.5), ("Hombre 1,75 normal", 14.0)):
        k = r / G.R
        big = lames("B")[4]
        w, h = G.size(big)
        scaled = [(f"L{i}", affinity.scale(p, k, k, origin=(0, 0))) for i, p in enumerate(lames("B"))]
        _, left = G.drop_pack(scaled)
        rows.append([name, f1(r * 1.745 + 2), f1(r), f"{round(k * 100)} %", f"{f1(w * k)} × {f1(h * k)}",
                     "Sí" if not left else f"No: {len(left)} lámina(s) a otra hoja"])
    y = table(c, rows, 1.6, y - 0.3, [4.6, 1.8, 1.6, 2.9, 3.2, 3.7], pad=2.6)
    y = para(c, "Si al escalar no caben las 5 láminas en una hoja, imprime la hoja dos veces y reparte las láminas en dos hojas de goma "
                "(L1–L3 en una y L4–L5 + disco en otra).", 1.6, y - 0.25, 17.8, "s")
    y -= 0.4
    y = para(c, "Ajustes finos", 1.6, y, 17.8, "h2")
    y = bullets(c, ["<b>Más alta o más baja:</b> cambia el ancho de las láminas (Δφ) sin tocar R. Cada 1° en todas las láminas son unos 1,1 cm más de altura atrás.",
                    "<b>Más larga de delante a atrás:</b> alarga todas las láminas por el extremo trasero; la curva se mantiene.",
                    "<b>Más ceñida al hombro:</b> reduce R 0,5–1 cm y vuelve a formar sobre un balón algo más pequeño (fútbol, R ≈ 11 cm)."],
                1.6, y, 17.8, "bul", 0.1)
    y -= 0.3
    y = para(c, "Fórmula de cada lámina (para dibujarla a mano)", 1.6, y, 17.8, "h2")
    y = para(c, "Dibuja un eje recto. Desde el polo, a cada distancia x = R · t a lo largo del eje, marca a cada lado medio ancho "
                "<b>R · sen(t) · Δφ / 2</b> (con Δφ en radianes) y suma 1 cm al lado superior (salvo en L1). Une los puntos con una curva suave "
                "y corta el extremo trasero en recto a x = R · 1,745.", 1.6, y - 0.1, 17.8, "b")
    rows = [["t (grados)"] + [str(d) for d in (10, 20, 40, 60, 80, 100)],
            ["x desde el polo (cm)"] + [f1(G.R * math.radians(d)) for d in (10, 20, 40, 60, 80, 100)],
            ["Medio ancho, lámina de 15° (cm)"] + [f1(G.R * math.sin(math.radians(d)) * math.radians(15) / 2) for d in (10, 20, 40, 60, 80, 100)],
            ["Medio ancho, lámina de 20° (cm)"] + [f1(G.R * math.sin(math.radians(d)) * math.radians(20) / 2) for d in (10, 20, 40, 60, 80, 100)]]
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
    for fn in (page_pa, page_pb):
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
    for fn in (page_pa, page_pb):
        fn(c)
        c.showPage()
    c.save()
    print("PDF generado:", OUT_PRINT)


if __name__ == "__main__":
    build()
