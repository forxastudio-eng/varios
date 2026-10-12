"""MusicXML (de Audiveris u otra fuente) → canción en el formato de la app (songs.json).

Reglas:
  · Partituras de piano (una parte con 2 pentagramas, o 2 partes): pentagrama superior = mano derecha, inferior = izquierda.
  · Más de dos partes (p. ej. coral SATB): la mitad superior de las voces = derecha, la inferior = izquierda.
  · Un solo pentagrama: solo mano derecha.
  · Se avisa de los compases irregulares (el OMR a veces lee mal una figura).
"""
from __future__ import annotations
import json
import os
from fractions import Fraction

from music21 import converter, key as m21key, meter, stream, tempo, note, chord

NOMBRES_MAYOR = ["C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"]
NOMBRES_MENOR = ["Cm", "C#m", "Dm", "Ebm", "Em", "Fm", "F#m", "Gm", "G#m", "Am", "Bbm", "Bm"]


def _nombre_tonalidad(k) -> str:
    pc = k.tonic.pitchClass
    return NOMBRES_MENOR[pc] if k.mode == "minor" else NOMBRES_MAYOR[pc]


def _redondea(x: float) -> float:
    return round(float(x) * 96) / 96


def _notas_de_parte(parte: stream.Stream, inicio_max: float | None = None):
    """Notas (inicio, duración, altura) en negras, con ligaduras unidas y sin notas de adorno."""
    p = parte.stripTies()
    salida = []
    for n in p.flatten().notes:
        if n.duration.isGrace or n.quarterLength <= 0:
            continue
        ini = float(n.offset)
        if inicio_max is not None and ini >= inicio_max:
            continue
        pitches = n.pitches if isinstance(n, chord.Chord) else [n.pitch]
        for pt in pitches:
            if 21 <= pt.midi <= 108:
                salida.append([ini, float(n.quarterLength), pt.midi])
    return sorted(salida, key=lambda a: (a[0], a[2]))


def convertir(ruta: str, titulo: str | None = None, id_: str = "mi-partitura", bpm_defecto: int = 90,
              expandir_repeticiones: bool = False) -> dict:
    """Devuelve {'cancion': dict_en_formato_songs_json, 'avisos': [str]}."""
    avisos: list[str] = []
    s = converter.parse(ruta)
    if expandir_repeticiones:
        try:
            s = s.expandRepeats()
        except Exception as e:   # noqa: BLE001
            avisos.append(f"No se pudieron expandir las repeticiones: {e}")
    partes = list(s.parts) if hasattr(s, "parts") and len(s.parts) else [s]
    if not partes:
        raise ValueError("La partitura no contiene pentagramas")

    # compás, tonalidad y tempo
    ts = None
    for e in s.recurse().getElementsByClass(meter.TimeSignature):
        ts = e
        break
    num, den = (ts.numerator, ts.denominator) if ts else (4, 4)
    por_compas = float(num * 4 / den)
    tonalidad = None
    for e in s.recurse().getElementsByClass(m21key.KeySignature):
        try:
            tonalidad = _nombre_tonalidad(e.asKey())
        except Exception:   # noqa: BLE001
            pass
        break
    if tonalidad is None:
        try:
            tonalidad = _nombre_tonalidad(s.analyze("key"))
        except Exception:   # noqa: BLE001
            tonalidad = "C"
    bpm = bpm_defecto
    for e in s.recurse().getElementsByClass(tempo.MetronomeMark):
        try:
            q = e.getQuarterBPM()
            if q:
                bpm = int(round(q)); break
        except Exception:   # noqa: BLE001
            pass

    # compases (de la primera parte)
    primera = partes[0]
    medidas = list(primera.getElementsByClass(stream.Measure))
    if not medidas:
        raise ValueError("No se encontraron compases")
    inicios = [float(m.offset) for m in medidas]
    anacrusa = 0.0
    d0 = float(medidas[0].duration.quarterLength)
    if len(medidas) > 1 and d0 < por_compas - 1e-6:
        # una anacrusa verdadera es el compás «0» o se completa con el último compás; si no, es un compás mal leído
        completa = abs(float(medidas[-1].duration.quarterLength) + d0 - por_compas) < 0.01
        if medidas[0].number == 0 or completa:
            anacrusa = d0
    total = float(medidas[-1].offset + medidas[-1].duration.quarterLength)
    # compases irregulares
    for i, m in enumerate(medidas):
        d = float(m.duration.quarterLength)
        esperado = anacrusa if (i == 0 and anacrusa > 0) else por_compas
        ultimo = i == len(medidas) - 1
        if abs(d - esperado) > 0.01 and not (ultimo and (anacrusa > 0 and abs(d - (por_compas - anacrusa)) < 0.01 or d < esperado)):
            avisos.append(f"Compás {m.number}: dura {d:g} pulsos y debería durar {esperado:g}. Revisa esa parte.")

    # manos
    if len(partes) == 1:
        derecha, izquierda = partes, []
    elif len(partes) == 2:
        derecha, izquierda = partes[:1], partes[1:]
    else:
        mitad = (len(partes) + 1) // 2
        derecha, izquierda = partes[:mitad], partes[mitad:]
    rh: list = []
    for p in derecha:
        rh += _notas_de_parte(p)
    lh: list = []
    for p in izquierda:
        lh += _notas_de_parte(p)
    rh.sort(key=lambda a: (a[0], a[2])); lh.sort(key=lambda a: (a[0], a[2]))
    if not rh and not lh:
        raise ValueError("No se detectó ninguna nota")

    # nivel 2: una nota de bajo por compás; nivel 1: melodía (la nota más aguda de cada ataque si hay acordes)
    bajo = []
    fin_c = inicios[1:] + [total]
    for a, b in zip(inicios, fin_c):
        dentro = [n for n in lh if a <= n[0] < b]
        if not dentro:
            continue
        primero = min(n[0] for n in dentro)
        grave = min(n[2] for n in dentro if n[0] == primero)
        ini = a if primero - a < 0.5 else primero
        bajo.append([ini, b - ini, grave])
    politonica = any(len([m for m in rh if abs(m[0] - n[0]) < 1e-3]) > 1 for n in rh)
    melodia = None
    if politonica:
        melodia, vistos = [], set()
        for n in sorted(rh, key=lambda a: (a[0], -a[2])):
            k = round(n[0], 3)
            if k not in vistos:
                vistos.add(k); melodia.append(n)
        melodia.sort(key=lambda a: a[0])
        for i, n in enumerate(melodia):
            if i + 1 < len(melodia) and n[0] + n[1] > melodia[i + 1][0]:
                n[1] = max(0.1, melodia[i + 1][0] - n[0])

    todas = rh + lh
    nombre = titulo or (s.metadata.title if getattr(s, "metadata", None) and s.metadata.title else None) or os.path.splitext(os.path.basename(ruta))[0]
    dens = len(todas) / max(1.0, total * 60.0 / bpm)
    dificultad = 1 if dens < 1.5 else 2 if dens < 3.5 else 3 if dens < 6 else 4
    fmt = lambda l: [[_redondea(a), _redondea(b), int(p)] for a, b, p in l]   # noqa: E731
    cancion = {
        "id": id_, "titulo": str(nombre), "compositor": "Partitura importada", "epoca": "", "obra": "",
        "tonalidad": tonalidad, "tonalidadOriginal": tonalidad, "compas": f"{num}/{den}", "bpm": int(bpm),
        "dificultad": dificultad, "consejo": "Leída de una partitura. Empieza por el nivel 1 y baja la velocidad.",
        "curiosidad": "", "fuente": "pdf", "pulso": 1.5 if den == 8 and num in (6, 9, 12) else 1.0,
        "pulsosCompas": por_compas, "anacrusa": _redondea(anacrusa), "compases": len(medidas),
        "pulsosTotales": _redondea(total), "duracionSeg": round(total * 60.0 / bpm, 1),
        "notaMin": min(n[2] for n in todas), "notaMax": max(n[2] for n in todas),
        "inicioCompases": [_redondea(x) for x in inicios],
        "mdNotas": fmt(rh), "miNotas": fmt(lh), "bajoNotas": fmt(bajo), "orden": 1000,
    }
    if melodia is not None:
        cancion["melNotas"] = fmt(melodia)
    return {"cancion": cancion, "avisos": avisos}


def documento(cancion: dict) -> dict:
    return {"version": 1, "canciones": [cancion]}


if __name__ == "__main__":
    import sys
    r = convertir(sys.argv[1])
    print(json.dumps({"avisos": r["avisos"], "compases": r["cancion"]["compases"], "notas": len(r["cancion"]["mdNotas"]) + len(r["cancion"]["miNotas"])}, ensure_ascii=False))
