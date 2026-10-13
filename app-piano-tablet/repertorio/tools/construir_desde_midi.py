#!/usr/bin/env python3
"""Construye una partitura EXACTA (`fuentes/NN-id.json`) a partir de varias versiones MIDI independientes de la misma obra.

Por qué así: una sola transcripción de memoria, o un solo MIDI de internet, puede tener notas equivocadas. Aquí se parte de
una versión base, se alinea acorde a acorde con las demás y cada nota solo se queda si la apoya la mayoría de las fuentes
independientes. Después se corrige el desfase de tiempos, se cuantiza a una rejilla de compás y se separan las manos.

Las fuentes salen del conjunto «ADL Piano MIDI» (github.com/lucasnfe/adl-piano-midi). Los MIDI no se guardan en este
repositorio: solo el resultado (las notas de obras de dominio público) y este script, que explica cómo se obtuvo.

Uso:  python3 construir_desde_midi.py RUTA/adl-piano-midi.zip           (necesita: pip install mido)
"""
import io, json, math, os, re, sys, unicodedata, zipfile
from collections import defaultdict
from fractions import Fraction as F

import mido
from mido.midifiles import meta as _meta

AQUI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(AQUI, "..", "..", "servidor", "tests"))
import metricas                                                    # noqa: E402  (agrupación y alineación de acordes)

for _k in range(16):                                               # armaduras mal formadas no deben romper la lectura
    for _m in (0, 1):
        _meta._key_signature_decode.setdefault((_k, _m), "C")

FUENTES = os.path.join(AQUI, "..", "fuentes")
Z = "adl-piano-midi/"

PIEZAS = [
    dict(
        id="para-elisa", salida="01-para-elisa.json", orden=1,
        title="Para Elisa", composer="Ludwig van Beethoven", era="Clasicismo",
        work="Bagatela en La menor, WoO 59 (1810)", key="Am", meter="3/8", tempo=76, difficulty=2,
        tip="Las dos primeras notas (E y D#) se repiten como un balanceo. Empieza con el nivel 1 y a poca velocidad.",
        fun="Nadie sabe con certeza quién era «Elisa»: el manuscrito original se perdió.",
        base=Z + "Classical/Classical/Ludwig Van Beethoven/Fur Elise.mid",
        grupos=[[Z + "Classical/Classical/Murray Perahia/Bagatelle in A minor for Piano_ WoO 59_ F#U00fcr Elise.mid"]],
        tolerancias={"base": 0.06, "Perahia": 0.35},   # en negras de cada versión (una interpretación humana «rompe» más los acordes)
        escala=F(1, 2),         # en el MIDI la «negra» es la corchea real (compás 3/4 = 3/8): se divide entre 2
        compas=3,               # duración del compás en negras del MIDI
        rejilla=24,             # subdivisiones por negra del MIDI (cubre tresillos y fusas)
        anacrusa=True,          # la obra empieza con 2 semicorcheas antes del primer compás
        manos="pistas",         # pista 1 = mano derecha, pista 2 = izquierda
    ),
    dict(
        id="claro-de-luna", salida="02-claro-de-luna.json", orden=2,
        title="Claro de luna (1.er movimiento)", composer="Ludwig van Beethoven", era="Clasicismo",
        work="Sonata n.º 14 en Do sostenido menor, Op. 27 n.º 2, «Quasi una fantasia» (1801)", key="C#m", meter="2/2", tempo=54, difficulty=3,
        tip="La mano derecha repite un arpegio de tres notas (un tresillo). Toca muy suave y despacio: es un Adagio. Empieza con el nivel 1.",
        fun="El apodo «Claro de luna» no es de Beethoven: lo inventó el crítico Ludwig Rellstab años después de su muerte.",
        base=Z + "Classical/Classical/Daniel Barenboim/Piano Sonata No 14 in C sharp minor Op 27 No 2 Moonlight (1999 Digital Remaster) First movement Adagio sostenuto.mid",
        grupos=[[Z + "Unknown/Unknown/Evelyne Dubourg/Piano Sonata No 14 in C-Sharp Minor_ Op 27_2 I Adagio Sostenuto.mid"],
                [Z + "Unknown/Unknown/David Bryan/Sonata Quasi Una Fantasia_ Op27_ No2 Moonlight 1st Movement.mid",
                 Z + "Classical/Classical/Andr#U00e1s Schiff/Sonata No 14 c-sharp minor op 272 Adagio sostenuto.mid"]],
        # Barenboim tiene los tiempos exactos en la rejilla pero algunas duraciones rotas; Dubourg tiene duraciones sensatas
        # (aunque sus tiempos van desfasados): los tiempos salen de la primera y las duraciones largas de la segunda.
        duraciones_de="Evelyne Dubourg",
        tolerancias={"base": 0.04, "Dubourg": 0.05, "Sonata Quasi": 0.10, "Sonata No 14 c-sharp": 0.10},
        escala=F(1), compas=4, rejilla=12, anacrusa=False,
        manos="grave_largo",    # bajo = notas graves largas; las notas cortas de registro medio son el arpegio de la derecha
    ),
]


# ---------------------------------------------------------------------------------------------------- lectura de MIDI
def normaliza(s):
    """Minúsculas sin acentos ni signos; las letras acentuadas que `unzip` muestra como #U00fc se convierten antes."""
    s = re.sub(r"#U([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), s)
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]", "", s.lower())


def abrir(zf, nombre):
    """Lee el archivo del zip por su nombre exacto o, si no está, por su nombre de archivo sin signos."""
    try:
        return zf.read(nombre)
    except KeyError:
        pass
    objetivo = normaliza(os.path.basename(nombre))
    for m in zf.namelist():
        if normaliza(os.path.basename(m)) == objetivo:
            return zf.read(m)
    raise FileNotFoundError(nombre)


def leer(data):
    """Notas (inicio, duración, altura, pista) con los tiempos en TICKS convertidos a negras (no en segundos): el rubato
    codificado en el mapa de tempos no mueve las notas de su sitio en el compás."""
    mid = mido.MidiFile(file=io.BytesIO(data))
    tpb = mid.ticks_per_beat
    out = []
    for ti, tr in enumerate(mid.tracks):
        t = 0
        abiertas = defaultdict(list)
        for m in tr:
            t += m.time
            if m.type == "note_on" and m.velocity > 0:
                abiertas[m.note].append(t)
            elif m.type in ("note_off", "note_on") and abiertas.get(m.note):
                s = abiertas[m.note].pop(0)
                out.append((s / tpb, (t - s) / tpb, m.note, ti))
    return sorted(out)


def indice_de_acorde(notas, tol):
    """Para cada nota (en orden de inicio) el número de acorde al que pertenece, agrupando igual que metricas.acordes."""
    idx, cur, inicio = [], -1, None
    for n in notas:
        if inicio is None or abs(n[0] - inicio) > tol:
            cur += 1
            inicio = n[0]
        idx.append(cur)
    return idx


# ---------------------------------------------------------------------------------------------------- votación
def votar(pieza, fuentes):
    """Compara la base con las demás fuentes. Devuelve (acordes_base, quitar, anadir) con las decisiones por mayoría."""
    base = fuentes[pieza["base"]]
    tol = pieza["tolerancias"]
    tol_base = tol["base"]
    gb = metricas.acordes([(a, p) for a, d, p, t in base], tol=tol_base)
    seq_b = [c for _, c in gb]
    otras = {}
    for g in pieza["grupos"]:
        for n in g:
            clave = next(k for k in tol if k != "base" and normaliza(k) in normaliza(n))
            otras[n] = (metricas.acordes([(a, p) for a, d, p, t in fuentes[n]], tol=tol[clave]), clave)
    al = {n: dict(metricas._alinear(seq_b, [c for _, c in ga])) for n, (ga, _) in otras.items()}

    def apoya(grupo, i, p):
        """True/False si algún miembro del grupo tiene (o no) la nota en el acorde alineado; None si no hay pareja."""
        pares = [(al[n][i], n) for n in grupo if i in al[n]]
        if not pares:
            return None
        return any(p in otras[n][0][j][1] for j, n in pares)

    quitar, anadir = set(), []
    for i, (t, c) in enumerate(gb):
        # un acorde que NINGUNA de las demás fuentes tiene (y hay al menos 2 independientes) solo lo apoya la base: se descarta
        if len(pieza["grupos"]) >= 2 and all(all(i not in al[n] for n in g) for g in pieza["grupos"]):
            for p in sorted(c):
                quitar.add((i, p))
            continue
        for p in sorted(c):
            a_favor = 1 + sum(1 for g in pieza["grupos"] if apoya(g, i, p) is True)
            en_contra = sum(1 for g in pieza["grupos"] if apoya(g, i, p) is False)
            if en_contra > a_favor:
                quitar.add((i, p))
        candidatas = set()
        for g in pieza["grupos"]:
            for n in g:
                if i in al[n]:
                    candidatas |= otras[n][0][al[n][i]][1] - c
        for p in sorted(candidatas):
            # mayoría estricta: la base cuenta 1 voto en contra, así que hacen falta al menos 2 fuentes a favor
            if len(pieza["grupos"]) >= 2 and all(apoya(g, i, p) is True for g in pieza["grupos"]):
                anadir.append((t, p))
    return gb, quitar, anadir, al, otras


# ---------------------------------------------------------------------------------------------------- construcción
def desfase_de(tiempos, rejilla):
    """Desfase (en negras) de toda la versión respecto de la rejilla ideal: media circular de la fase de cada nota."""
    s = sum(math.sin(2 * math.pi * ((t * rejilla) % 1.0)) for t in tiempos)
    c = sum(math.cos(2 * math.pi * ((t * rejilla) % 1.0)) for t in tiempos)
    return math.atan2(s, c) / (2 * math.pi) / rejilla


def asignar_duraciones(pieza, notas, fuentes, al, otras, rej):
    """Duración de cada nota: la de la fuente de duraciones si es una nota larga (>= 0,6 negras); si no, la de la base si
    es creíble (>= 0,2: las duraciones de unos pocos milisegundos son un artefacto de los MIDI); si no, un tresillo."""
    clave = pieza["duraciones_de"]
    nombre = next(n for n in otras if normaliza(clave) in normaliza(n))
    tol = pieza["tolerancias"][next(k for k in pieza["tolerancias"] if k != "base" and normaliza(k) in normaliza(nombre))]
    ordenada = sorted(fuentes[nombre], key=lambda n: (n[0], n[2], n[3]))
    gidx = indice_de_acorde(ordenada, tol)
    por_acorde = defaultdict(dict)
    for n, g in zip(ordenada, gidx):
        por_acorde[g].setdefault(n[2], n[1])
    sin_pareja = 0
    for n in notas:
        g = n[4]
        d_otra = por_acorde.get(al[nombre].get(g), {}).get(n[2]) if g is not None else None
        d_base = n[1]
        if d_base is not None and d_base >= 0.6:
            n[1] = d_otra if (d_otra is not None and d_otra >= 0.6) else d_base       # nota larga: la otra fuente afina el valor
        elif d_base is not None and d_base >= 0.2:
            n[1] = d_base                                                                 # nota corta creíble
        elif d_otra is not None and d_otra >= 0.2:
            n[1] = min(d_otra, 1.0) if d_otra < 0.6 else 1.0 / 3                          # la duración de la base está rota
        else:
            n[1] = 1.0 / 3
            sin_pareja += 1
    return sin_pareja


def construir(pieza, zf, verbose=True):
    rej = pieza["rejilla"]
    todos = [pieza["base"]] + [n for g in pieza["grupos"] for n in g]
    fuentes = {n: leer(abrir(zf, n)) for n in todos}
    base = fuentes[pieza["base"]]
    gb, quitar, anadir, al, otras = votar(pieza, fuentes)
    ajenos = []
    if verbose:
        print(f"[{pieza['id']}] base: {os.path.basename(pieza['base'])[:50]} · {len(base)} notas · {len(gb)} acordes")
        for g in pieza["grupos"]:
            for n in g:
                print(f"   contraste: {os.path.basename(n)[:50]} · {len(fuentes[n])} notas")
        print(f"   votación por mayoría → quitar {len(quitar)} nota(s), añadir {len(anadir)} nota(s) y {len(ajenos)} acorde(s) completos")
        for i, p in sorted(quitar):
            t = gb[i][0]
            print(f"     quitar  compás {int(t // pieza['compas']) + 1:3d} tiempo {t % pieza['compas']:5.2f} nota {metricas_nombre(p)}")
        for t, p in anadir:
            print(f"     añadir  compás {int(t // pieza['compas']) + 1:3d} tiempo {t % pieza['compas']:5.2f} nota {metricas_nombre(p)}")
        for t, c in ajenos:
            print(f"     añadir acorde compás {int(t // pieza['compas']) + 1:3d} tiempo {t % pieza['compas']:5.2f}: {' '.join(metricas_nombre(p) for p in c)}")

    d = desfase_de([n[0] for n in base], rej)
    if not pieza["anacrusa"]:
        # el desfase solo se conoce módulo una subdivisión: se elige el que deja el primer ataque en el tiempo 0
        d += round((min(n[0] for n in base) - d) * rej) / rej
    if verbose:
        print(f"   desfase de tiempos de la base: {d * 1000:+.1f} milésimas de negra (se corrige)")
    orden = sorted(base, key=lambda n: (n[0], n[2], n[3]))
    gidx = indice_de_acorde(orden, pieza["tolerancias"]["base"])
    notas = []
    for n, g in zip(orden, gidx):
        if (g, n[2]) in quitar:
            continue
        notas.append([n[0] - d, n[1], n[2], n[3], g])
    for t, p in anadir:
        notas.append([t - d, None, p, -1, None])
    for t, c in ajenos:
        for p in c:
            notas.append([t - d, None, p, -1, None])
    if pieza.get("duraciones_de"):
        asignar_duraciones(pieza, notas, fuentes, al, otras, rej)
    for n in notas:                                  # cuantización a la rejilla
        n[0] = round(n[0] * rej) / rej
        if n[1] is not None:
            n[1] = max(1.0 / rej, round(n[1] * rej) / rej)
    graves = sorted(n[0] for n in notas if n[2] < 52 and n[1] is not None)
    for n in notas:                                  # las notas añadidas: duran hasta el siguiente ataque grave
        if n[1] is None:
            sig = [g for g in graves if g > n[0] + 1e-6]
            n[1] = (sig[0] - n[0]) if sig else 1.0
    por_altura = defaultdict(list)                   # una misma altura no puede sonar encima de sí misma
    for n in notas:
        por_altura[n[2]].append(n)
    for ns in por_altura.values():
        ns.sort(key=lambda x: x[0])
        for a, b in zip(ns, ns[1:]):
            if a[0] + a[1] > b[0] + 1e-9:
                a[1] = max(1.0 / rej, b[0] - a[0])
    vistos, limpias = set(), []                      # sin duplicados exactos
    for n in sorted(notas, key=lambda x: (x[0], x[2])):
        k = (round(n[0] * rej), n[2])
        if k not in vistos:
            vistos.add(k)
            limpias.append(n)
    notas = limpias

    if pieza["manos"] == "pistas":
        pistas = sorted({n[3] for n in notas if n[3] >= 0})
        der = [n for n in notas if n[3] == pistas[0] or (n[3] < 0 and n[2] >= 60)]
    else:
        # bajo (izquierda): notas muy graves, o notas largas hasta B3; el resto (el arpegio de registro medio y la melodía) es de la derecha
        der = [n for n in notas if not (n[2] <= 47 or (n[2] <= 59 and n[1] >= 0.9))]
    ids = {id(n) for n in der}
    izq = [n for n in notas if id(n) not in ids]
    return der, izq


_N = "C C# D D# E F F# G G# A A# B".split()


def metricas_nombre(p):
    return f"{_N[p % 12]}{p // 12 - 1}"


def melodia(der):
    """Voz superior de la mano derecha teniendo en cuenta las notas que siguen sonando (una nota larga de arriba tapa
    a las notas más graves que empiezan mientras suena)."""
    der = sorted(der, key=lambda n: (n[0], -n[2]))
    mel = []
    for n in der:
        sonando = [m for m in der if m[0] < n[0] - 1e-9 and m[0] + m[1] > n[0] + 1e-9]
        simultaneas = [m for m in der if abs(m[0] - n[0]) < 1e-9]
        if all(n[2] >= m[2] for m in sonando) and n[2] >= max(m[2] for m in simultaneas):
            mel.append(n)
    return mel


def a_fuente(pieza, der, izq):
    s = float(pieza["escala"])
    comp = pieza["compas"] * s                       # compás en negras reales
    todas = der + izq
    primero = min(n[0] for n in todas) * s
    fin = max(n[0] + n[1] for n in todas) * s
    if pieza["anacrusa"]:
        t0 = primero                                  # el tiempo 0 es el primer ataque
        pickup = round(comp - (primero % comp), 6)    # lo que queda del compás desde el primer ataque
    else:
        t0, pickup = 0.0, 0.0
    fin -= t0
    inicios = [0.0] if pickup else []
    x = pickup
    while x < fin - 1e-6:
        inicios.append(round(x, 6))
        x += comp
    total = round(inicios[-1] + comp, 6) if inicios else round(comp, 6)
    t = lambda x: round(x * s - t0, 6)
    f = lambda ns: [[t(n[0]), round(n[1] * s, 6), n[2]] for n in sorted(ns, key=lambda n: (n[0], n[2]))]
    out = {k: pieza[k] for k in ("id", "title", "composer", "era", "work", "key", "meter", "tempo", "difficulty", "tip", "fun")}
    out.update(dict(pickup=pickup, total=total, inicios=inicios, RH=f(der), LH=f(izq), MEL=f(melodia(der)),
                    origen="Reconstruida por consenso de versiones MIDI independientes (ADL Piano MIDI); obra de dominio público"))
    return out


def main(zip_path):
    zf = zipfile.ZipFile(zip_path)
    for pieza in PIEZAS:
        der, izq = construir(pieza, zf)
        fuente = a_fuente(pieza, der, izq)
        ruta = os.path.join(FUENTES, pieza["salida"])
        with open(ruta, "w", encoding="utf-8") as f:
            json.dump(fuente, f, ensure_ascii=False, separators=(",", ":"))
        print(f"   → {os.path.basename(ruta)}: {len(fuente['inicios'])} compases · RH {len(fuente['RH'])} · LH {len(fuente['LH'])} · "
              f"MEL {len(fuente['MEL'])} notas · total {fuente['total']} negras")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1])
