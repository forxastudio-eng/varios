#!/usr/bin/env python3
"""Compila las canciones de `fuentes/*.txt` (notación tipo ABC) a:
  - songs.json   (lo que lee la app: niveles 1-3 ya derivados)
  - midi/*.mid   (para escucharlas en un PC y comprobarlas de oído)
y valida que cada compás sume exactamente la duración del compás.

Uso:  python3 compilar.py            (compila todo)
      python3 compilar.py --solo-validar
"""
import json, re, struct, sys, glob, os
from fractions import Fraction as F

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
FUENTES = os.path.join(RAIZ, "fuentes")
MIDI_DIR = os.path.join(RAIZ, "midi")
SALIDA_JSON = os.path.join(RAIZ, "songs.json")
ASSETS = os.path.join(os.path.dirname(RAIZ), "android", "app", "src", "main", "assets", "songs.json")

# ---------- armaduras ----------
MAYORES = {"C": 0, "G": 1, "D": 2, "A": 3, "E": 4, "B": 5, "F#": 6, "C#": 7,
           "F": -1, "Bb": -2, "Eb": -3, "Ab": -4, "Db": -5, "Gb": -6, "Cb": -7}
MENORES = {"Am": 0, "Em": 1, "Bm": 2, "F#m": 3, "C#m": 4, "G#m": 5, "D#m": 6,
           "Dm": -1, "Gm": -2, "Cm": -3, "Fm": -4, "Bbm": -5, "Ebm": -6}
ORDEN_SOST = "FCGDAEB"
ORDEN_BEM = "BEADGCF"
BASE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def armadura(key):
    n = MAYORES.get(key, MENORES.get(key))
    if n is None:
        raise ValueError(f"tonalidad desconocida: {key}")
    acc = {}
    if n > 0:
        for l in ORDEN_SOST[:n]: acc[l] = 1
    elif n < 0:
        for l in ORDEN_BEM[:-n]: acc[l] = -1
    return acc


def parse_len(s):
    """'' -> 1 ; '2' -> 2 ; '/2' o '/' -> 1/2 ; '3/2' -> 3/2 ; '//' -> 1/4"""
    if not s:
        return F(1)
    m = re.fullmatch(r"(\d*)(/*)(\d*)", s)
    if not m:
        raise ValueError(f"longitud inválida: {s!r}")
    num = int(m.group(1)) if m.group(1) else 1
    barras = m.group(2)
    if not barras:
        return F(num)
    if m.group(3):
        return F(num, int(m.group(3)))
    return F(num, 2 ** len(barras))


NOTA_RE = re.compile(r"([_^=]*)([A-Ga-g])([,']*)(\d*/*\d*)")
TOKEN_RE = re.compile(r"""
    (?P<tripleta>\(3)
  | (?P<acorde>\[[^\]]+\])(?P<acorde_len>\d*/*\d*)
  | (?P<silencio>z)(?P<silencio_len>\d*/*\d*)
  | (?P<nota>[_^=]*[A-Ga-g][,']*\d*/*\d*)
  | (?P<ligadura>-)
  | (?P<barra>\|)
""", re.X)


class Voz:
    def __init__(self, nombre):
        self.nombre = nombre
        self.notas = []      # [inicio, dur, pitch] en negras (Fraction)
        self.compases = []   # duración de cada compás


def compilar_voz(texto, nombre, L, key):
    """Devuelve (notas, duraciones_de_compas)."""
    texto = re.sub(r"%.*", "", texto)
    texto = re.sub(r"(\[\||\|\]|\|\||:\||\|:|::)", "|", texto)
    texto = texto.replace("\n", " ")
    sig = armadura(key)
    t = F(0)
    notas = []
    abiertas = {}            # pitch -> índice en notas (para ligaduras)
    compases, inicio_compas = [], F(0)
    acc_compas = {}          # (letra, octava) -> alteración dentro del compás
    tripleta = 0
    ligar_siguiente = set()
    ultimo_dur_idx = None
    pos = 0
    texto = texto.strip()
    while pos < len(texto):
        if texto[pos].isspace():
            pos += 1
            continue
        m = TOKEN_RE.match(texto, pos)
        if not m:
            raise ValueError(f"[{nombre}] símbolo no reconocido cerca de: {texto[pos:pos+15]!r}")
        pos = m.end()
        if m.group("tripleta"):
            tripleta = 3
            continue
        if m.group("barra"):
            if t > inicio_compas:
                compases.append(t - inicio_compas)
                inicio_compas = t
            acc_compas = {}
            continue
        if m.group("ligadura"):
            if ultimo_dur_idx:
                ligar_siguiente |= set(ultimo_dur_idx)
            continue
        # ---- evento con duración ----
        def dur_de(mult):
            nonlocal tripleta
            d = L * mult
            if tripleta:
                d = d * F(2, 3)
                tripleta -= 1
            return d

        def pitch_de(acc, letra, octs):
            octava = 4 if letra.isupper() else 5
            octava += octs.count("'") - octs.count(",")
            L_ = letra.upper()
            clave = (L_, octava)
            if acc:
                a = acc.count("^") - acc.count("_")
                if "=" in acc: a = 0
                acc_compas[clave] = a
            alt = acc_compas.get(clave, sig.get(L_, 0))
            return 12 * (octava + 1) + BASE[L_] + alt

        if m.group("silencio"):
            d = dur_de(parse_len(m.group("silencio_len")))
            t += d
            ultimo_dur_idx = None
            continue
        if m.group("acorde"):
            interior = m.group("acorde")[1:-1]
            dur_acorde = parse_len(m.group("acorde_len"))
            pits, lens = [], []
            for nm in NOTA_RE.finditer(interior):
                pits.append(pitch_de(nm.group(1), nm.group(2), nm.group(3)))
                lens.append(parse_len(nm.group(4)))
            mult = dur_acorde * (lens[0] if lens else 1)
            d = dur_de(mult)
            idxs = []
            for p in pits:
                notas.append([t, d, p]); idxs.append(len(notas) - 1)
            _poner_ligaduras(notas, idxs, ligar_siguiente, abiertas)
            ligar_siguiente = set()
            t += d
            ultimo_dur_idx = [notas[i][2] for i in idxs]
            continue
        # nota simple
        nm = NOTA_RE.fullmatch(m.group("nota"))
        p = pitch_de(nm.group(1), nm.group(2), nm.group(3))
        mult = parse_len(nm.group(4))
        d = dur_de(mult)
        notas.append([t, d, p])
        _poner_ligaduras(notas, [len(notas) - 1], ligar_siguiente, abiertas)
        ligar_siguiente = set()
        t += d
        ultimo_dur_idx = [p]
    if t > inicio_compas:
        compases.append(t - inicio_compas)
    return notas, compases


def _poner_ligaduras(notas, idxs, ligar, abiertas):
    for i in idxs:
        p = notas[i][2]
        if p in ligar and p in abiertas:
            j = abiertas[p]
            notas[j][1] += notas[i][1]
            notas[i] = None
            continue
        abiertas[p] = i


def aplicar_roto(texto):
    """Convierte 'a>b' en duraciones explícitas 3/2 y 1/2 antes de compilar."""
    patron = re.compile(r"((?:[_^=]*[A-Ga-gz][,']*)\d*/*\d*)\s*([<>])\s*((?:[_^=]*[A-Ga-gz][,']*)\d*/*\d*)")
    def mult(tok, factor):
        m = re.fullmatch(r"([_^=]*[A-Ga-gz][,']*)(\d*/*\d*)", tok)
        base, ln = m.group(1), m.group(2)
        v = parse_len(ln) * factor
        return f"{base}{v.numerator}/{v.denominator}"
    def rep(m):
        a, op, b = m.groups()
        fa, fb = (F(3, 2), F(1, 2)) if op == ">" else (F(1, 2), F(3, 2))
        return f"{mult(a, fa)} {mult(b, fb)}"
    prev = None
    while prev != texto:
        prev = texto
        texto = patron.sub(rep, texto, count=1)
    return texto


def leer_fuente(ruta):
    campos, voces, actual = {}, {}, None
    with open(ruta, encoding="utf-8") as f:
        for linea in f:
            linea = linea.rstrip("\n")
            if not linea.strip() or linea.lstrip().startswith("#"):
                continue
            m = re.match(r"^(RH|LH|MEL):\s*(.*)$", linea)
            if m:
                actual = m.group(1)
                voces[actual] = m.group(2)
                continue
            m = re.match(r"^([A-Za-z_]+):\s*(.*)$", linea)
            if m and not linea.startswith(" "):
                campos[m.group(1)] = m.group(2).strip()
                actual = None
                continue
            if actual:
                voces[actual] += " " + linea.strip()
    return campos, voces


def compilar_json(ruta):
    d = json.load(open(ruta, encoding="utf-8"))
    for obl in ("id", "title", "composer", "era", "key", "meter", "tempo", "difficulty", "tip"):
        if obl not in d:
            raise ValueError(f"{os.path.basename(ruta)}: falta el campo '{obl}'")
    fr = lambda x: F(x).limit_denominator(48)
    inicios = [fr(x) for x in d["inicios"]] + [fr(d["total"])]
    compases = [inicios[i + 1] - inicios[i] for i in range(len(inicios) - 1)]
    res = {}
    for v in ("RH", "LH", "MEL"):
        if v in d:
            res[v] = ([[fr(a), fr(b), int(p)] for a, b, p in d[v]], compases)
    errores = []
    for v, (notas, _) in res.items():
        for n in notas:
            if not (21 <= n[2] <= 108):
                errores.append(f"{d['id']} {v}: nota fuera del piano ({n[2]})")
    c = {k: (str(v) if not isinstance(v, (list, dict)) else v) for k, v in d.items()}
    return c, res, fr(d["pickup"]), pulsos_compas(d["meter"]), errores


def pulsos_compas(meter):
    n, d = map(int, meter.split("/"))
    return F(n * 4, d)


def compilar_cancion(ruta):
    if ruta.endswith(".json"):
        return compilar_json(ruta)
    c, voces = leer_fuente(ruta)
    for obl in ("id", "title", "composer", "era", "key", "meter", "tempo", "difficulty", "tip"):
        if obl not in c:
            raise ValueError(f"{os.path.basename(ruta)}: falta el campo '{obl}'")
    L = F(c.get("L", "1/4"))      # longitud por defecto, fracción de redonda
    L_negras = L * 4
    por_compas = pulsos_compas(c["meter"])
    pickup = F(c.get("pickup", "0"))
    errores = []
    res = {}
    for v in ("RH", "LH", "MEL"):
        if v not in voces:
            if v == "MEL":
                continue
            raise ValueError(f"{c['id']}: falta la voz {v}")
        texto = aplicar_roto(voces[v])
        notas, compases = compilar_voz(texto, v, L_negras, c["key"])
        notas = [n for n in notas if n]
        res[v] = (notas, compases)
        for i, dur in enumerate(compases):
            esperado = pickup if (i == 0 and pickup > 0) else por_compas
            es_ultimo = i == len(compases) - 1
            if dur != esperado:
                if es_ultimo and pickup > 0 and dur == por_compas - pickup:
                    continue
                errores.append(f"{c['id']} {v}: compás {i + (0 if pickup > 0 else 1)} dura {dur} (debe durar {esperado})")
    if "MEL" in res and len(res["MEL"][1]) != len(res["RH"][1]):
        errores.append(f"{c['id']}: MEL tiene {len(res['MEL'][1])} compases y RH {len(res['RH'][1])}")
    if len(res["RH"][1]) != len(res["LH"][1]):
        errores.append(f"{c['id']}: RH tiene {len(res['RH'][1])} compases y LH {len(res['LH'][1])}")
    return c, res, pickup, por_compas, errores


def bajo_simple(lh, compases_inicios, fin):
    """Nivel 2: una sola nota de bajo por compás (la más grave del primer ataque), sostenida el compás."""
    bajo = []
    for (ini, fin_c) in compases_inicios:
        en_compas = [n for n in lh if ini <= n[0] < fin_c]
        if not en_compas:
            continue
        primero = min(n[0] for n in en_compas)
        grave = min(n[2] for n in en_compas if n[0] == primero)
        bajo.append([ini if primero - ini < F(1, 2) else primero, fin_c - (ini if primero - ini < F(1, 2) else primero), grave])
    return bajo


def redondea(x):
    return round(float(x) * 96) / 96


def a_json(c, res, pickup, por_compas):
    rh, comp_rh = res["RH"]
    lh, _ = res["LH"]
    # límites de cada compás
    inicios, t = [], F(0)
    for i, d in enumerate(comp_rh):
        inicios.append((t, t + d))
        t += d
    total = t
    bajo = bajo_simple(lh, inicios, total)
    todas = rh + lh
    pitches = [n[2] for n in todas]
    if "MEL" in res:
        pitches += [n[2] for n in res["MEL"][0]]
    bpm = int(c["tempo"])
    seg = float(total) * 60 / bpm
    mel = res.get("MEL", (None,))[0]
    salida = {
        "id": c["id"], "titulo": c["title"], "compositor": c["composer"], "epoca": c["era"],
        "obra": c.get("work", ""), "tonalidad": c["key"], "tonalidadOriginal": c.get("original_key", c["key"]),
        "compas": c["meter"], "bpm": bpm, "dificultad": int(c["difficulty"]),
        "consejo": c["tip"], "curiosidad": c.get("fun", ""),
        "fuente": "partitura" if c.get("origen") else "transcripcion",
        "pulso": float(F(3, 2) if c["meter"] in ("6/8", "12/8", "9/8") else F(1)),
        "pulsosCompas": float(por_compas), "anacrusa": float(pickup),
        "compases": len(comp_rh), "pulsosTotales": redondea(total), "duracionSeg": round(seg, 1),
        "notaMin": min(pitches), "notaMax": max(pitches),
        "inicioCompases": [redondea(a) for a, _ in inicios],
        "mdNotas": [[redondea(n[0]), redondea(n[1]), n[2]] for n in sorted(rh, key=lambda n: (n[0], n[2]))],
        "miNotas": [[redondea(n[0]), redondea(n[1]), n[2]] for n in sorted(lh, key=lambda n: (n[0], n[2]))],
        "bajoNotas": [[redondea(n[0]), redondea(n[1]), n[2]] for n in sorted(bajo, key=lambda n: (n[0], n[2]))],
    }
    if mel:
        salida["melNotas"] = [[redondea(n[0]), redondea(n[1]), n[2]] for n in sorted(mel, key=lambda n: (n[0], n[2]))]
    return salida


# ---------- MIDI ----------
def vlq(n):
    out = [n & 0x7F]
    n >>= 7
    while n:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    return bytes(reversed(out))


def pista_midi(notas, canal, ppq, nombre, tempo_us=None):
    ev = []
    for ini, dur, p in notas:
        ev.append((int(round(float(ini) * ppq)), 1, p))
        ev.append((int(round(float(ini + dur) * ppq)) - 1, 0, p))
    ev.sort(key=lambda e: (e[0], e[1]))
    data = bytearray()
    nb = nombre.encode()
    data += b"\x00\xFF\x03" + vlq(len(nb)) + nb
    if tempo_us:
        data += b"\x00\xFF\x51\x03" + tempo_us.to_bytes(3, "big")
    data += b"\x00" + bytes([0xC0 | canal, 0])  # piano
    ultimo = 0
    for t, on, p in ev:
        data += vlq(t - ultimo)
        data += bytes([(0x90 if on else 0x80) | canal, p, 80 if on else 0])
        ultimo = t
    data += b"\x00\xFF\x2F\x00"
    return b"MTrk" + struct.pack(">I", len(data)) + bytes(data)


def escribir_midi(ruta, c, res):
    ppq = 480
    tempo = int(60_000_000 / int(c["tempo"]))
    pistas = [pista_midi(res["RH"][0], 0, ppq, "Mano derecha", tempo),
              pista_midi(res["LH"][0], 1, ppq, "Mano izquierda")]
    cab = b"MThd" + struct.pack(">IHHH", 6, 1, len(pistas), ppq)
    with open(ruta, "wb") as f:
        f.write(cab + b"".join(pistas))


NEGRAS = {1, 3, 6, 8, 10}


def main():
    solo_validar = "--solo-validar" in sys.argv
    rutas = sorted(glob.glob(os.path.join(FUENTES, "*.txt")) + glob.glob(os.path.join(FUENTES, "*.json")))
    canciones, errores_tot, ids = [], [], set()
    print(f"{'#':>2} {'id':28} {'cmp':>3} {'seg':>5} {'rango':>9} {'dif'} tonalidad")
    for i, ruta in enumerate(rutas, 1):
        try:
            c, res, pickup, por_compas, errs = compilar_cancion(ruta)
        except Exception as e:
            errores_tot.append(f"{os.path.basename(ruta)}: {e}")
            continue
        if c["id"] in ids:
            errs.append(f"id duplicado: {c['id']}")
        ids.add(c["id"])
        errores_tot += errs
        if errs:
            continue
        j = a_json(c, res, pickup, por_compas)
        j["orden"] = int(os.path.basename(ruta).split("-")[0])
        canciones.append(j)
        print(f"{i:>2} {j['id']:28} {j['compases']:>3} {j['duracionSeg']:>5} "
              f"{j['notaMin']:>4}-{j['notaMax']:<4} {j['dificultad']}   {j['tonalidad']} (orig. {j['tonalidadOriginal']})")
        if not solo_validar:
            os.makedirs(MIDI_DIR, exist_ok=True)
            escribir_midi(os.path.join(MIDI_DIR, f"{i:02d}-{c['id']}.mid"), c, res)
    if errores_tot:
        print("\nERRORES:")
        for e in errores_tot:
            print("  ✗", e)
        sys.exit(1)
    print(f"\n{len(canciones)} canciones OK")
    if not solo_validar:
        with open(SALIDA_JSON, "w", encoding="utf-8") as f:
            json.dump({"version": 1, "canciones": canciones}, f, ensure_ascii=False, separators=(",", ":"))
        os.makedirs(os.path.dirname(ASSETS), exist_ok=True)
        with open(ASSETS, "w", encoding="utf-8") as f:
            json.dump({"version": 1, "canciones": canciones}, f, ensure_ascii=False, separators=(",", ":"))
        print("→", SALIDA_JSON, "y", ASSETS)


if __name__ == "__main__":
    main()
