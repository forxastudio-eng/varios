#!/usr/bin/env python3
"""Extrae obras EXACTAS del corpus de music21 (dominio público) y las guarda como
`fuentes/NN-id.json`, el mismo formato que consume compilar.py.

Requiere:  pip install music21   (solo para regenerar; el APK no lo necesita)
Uso:       python3 importar_music21.py            (regenera todas las de MANIFIESTO)
"""
import json, os, sys
from fractions import Fraction as F
from music21 import corpus

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FUENTES = os.path.join(RAIZ, "fuentes")


def notas_de_parte(parte, t0, t1, transp):
    """Notas (inicio, dur, pitch) de una parte entre t0 y t1 (en negras), con ligaduras unidas."""
    p = parte.stripTies()
    out = []
    for n in p.flatten().notesAndRests:
        if n.isRest or n.duration.quarterLength <= 0 or n.duration.isGrace:
            continue
        ini = F(n.offset).limit_denominator(48)
        if ini < t0 or ini >= t1:
            continue
        dur = F(n.duration.quarterLength).limit_denominator(48)
        if ini + dur > t1:
            dur = t1 - ini
        pitches = n.pitches if n.isChord else [n.pitch]
        for pt in pitches:
            out.append([float(ini - t0), float(dur), pt.midi + transp])
    return out


def importar(m):
    s = corpus.parse(m["corpus"])
    partes = list(s.parts)
    ref = partes[0]
    medidas = [x for x in ref.getElementsByClass("Measure")]
    desde, hasta = m["compases"]
    sel = [x for x in medidas if desde <= x.number <= hasta]
    if not sel:
        raise SystemExit(f"{m['id']}: sin compases en {desde}-{hasta}")
    t0 = F(sel[0].offset).limit_denominator(48)
    t1 = F(sel[-1].offset + sel[-1].duration.quarterLength).limit_denominator(48) if False else \
        F(sel[-1].offset).limit_denominator(48) + F(sel[-1].duration.quarterLength).limit_denominator(48)
    ts = ref.recurse().getElementsByClass("TimeSignature")[0]
    por_compas = float(ts.barDuration.quarterLength)
    inicios = [float(F(x.offset).limit_denominator(48) - t0) for x in sel]
    pickup = 0.0
    if sel[0].duration.quarterLength < por_compas - 1e-6 and sel[0].number in (0, 1):
        pickup = float(sel[0].duration.quarterLength)
    saltar = F(m.get("saltar", 0)).limit_denominator(48)
    if saltar:
        # recortamos el silencio inicial: el primer compás pasa a ser una anacrusa
        t0 += saltar
        inicios = [max(0.0, x - float(saltar)) for x in inicios]
        pickup = float(sel[0].duration.quarterLength - saltar)
    transp = m.get("transponer", 0)
    def junta(idxs):
        todas = []
        for i in idxs:
            todas += notas_de_parte(partes[i], t0, t1, transp)
        return sorted(todas, key=lambda n: (n[0], n[2]))
    out = {k: v for k, v in m.items() if k not in ("corpus", "compases", "RH", "LH", "MEL", "transponer", "saltar")}
    out["meter"] = f"{ts.numerator}/{ts.denominator}"
    out["pickup"] = pickup
    out["total"] = float(t1 - t0)
    out["inicios"] = inicios
    out["RH"] = junta(m["RH"])
    out["LH"] = junta(m["LH"])
    if m.get("MEL"):
        out["MEL"] = junta(m["MEL"])
    out["origen"] = f"music21 corpus: {m['corpus']}, compases {desde}-{hasta}"
    ruta = os.path.join(FUENTES, f"{m['orden']:02d}-{m['id']}.json")
    with open(ruta, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print("→", os.path.basename(ruta), f"{len(sel)} compases, RH {len(out['RH'])} LH {len(out['LH'])} notas")


MANIFIESTO = [
    dict(orden=7, id="preludio-do", corpus="bach/bwv846", compases=(1, 34), RH=[0], LH=[1],
         title="Preludio en Do mayor", composer="Johann Sebastian Bach", era="Barroco",
         work="El clave bien temperado I, BWV 846 (1722)", key="C", tempo=72, difficulty=3,
         tip="Cada compás repite el mismo dibujo de 8 notas. Aprende un compás cada vez.",
         fun="Gounod le puso encima una melodía y nació su famosa «Ave Maria»."),
    dict(orden=8, id="sonata-facil", corpus="mozart/k545/movement1_exposition", compases=(1, 12), RH=[0], LH=[1],
         title="Sonata fácil (inicio)", composer="Wolfgang Amadeus Mozart", era="Clasicismo",
         work="Sonata n.º 16 en Do mayor, K. 545 (1788)", key="C", tempo=116, difficulty=3,
         tip="La izquierda toca un patrón de 4 notas que se repite (bajo de Alberti). Empieza solo con la derecha.",
         fun="Mozart la anotó en su catálogo como «una pequeña sonata para principiantes»."),
    dict(orden=9, id="la-donna-e-mobile", corpus="verdi/laDonnaEMobile", compases=(1, 35), RH=[0], LH=[1],
         title="La donna è mobile", composer="Giuseppe Verdi", era="Romanticismo",
         work="Rigoletto, acto III (1851)", key="Bb", tempo=120, difficulty=3,
         tip="Es un vals rápido: cuenta 1 por compás. La izquierda hace «pum-ta-ta».",
         fun="Verdi la guardó en secreto hasta el estreno para que nadie la tarareara antes."),
    dict(orden=10, id="el-tilo", corpus="schubert/Lindenbaum", compases=(8, 24), saltar=2.5, RH=[0], LH=[2],
         title="El tilo (Der Lindenbaum)", composer="Franz Schubert", era="Romanticismo",
         work="Winterreise, D. 911 (1827)", key="E", tempo=72, difficulty=2,
         tip="Una melodía muy cantable. Toca la derecha como si cantaras «Am Brunnen vor dem Tore».",
         fun="Es una de las canciones más populares de Alemania."),
    dict(orden=11, id="coral-wachet-auf", corpus="bach/bwv140.7", compases=(0, 17), RH=[0, 1], LH=[2, 3], MEL=[0],
         title="Despertad, nos llama la voz", composer="Johann Sebastian Bach", era="Barroco",
         work="Cantata BWV 140, coral final (1731)", key="Eb", tempo=76, difficulty=2,
         tip="Empieza con el nivel 1: solo la melodía. Luego añade el bajo.",
         fun="También se la conoce como «Sleepers, Wake!»."),
    dict(orden=12, id="coral-castillo-fuerte", corpus="bach/bwv80.8", compases=(0, 14), RH=[0, 1], LH=[2, 3], MEL=[0],
         title="Castillo fuerte es nuestro Dios", composer="Johann Sebastian Bach", era="Barroco",
         work="Cantata BWV 80, coral (1730)", key="D", tempo=84, difficulty=2,
         tip="Es una melodía solemne; mantén las notas largas hasta que se acabe la barra.",
         fun="La melodía es de Martín Lutero (1529)."),
    dict(orden=13, id="coral-estrella-manana", corpus="bach/bwv1.6", compases=(0, 20), RH=[1, 2], LH=[3, 4], MEL=[1],
         title="Qué hermosa brilla la estrella de la mañana", composer="Johann Sebastian Bach", era="Barroco",
         work="Cantata BWV 1, coral final (1725)", key="F", tempo=72, difficulty=2,
         tip="Empieza por el nivel 1 y escucha cómo la melodía sube y baja con calma.",
         fun="Una de las melodías de coral más queridas de Bach."),
    dict(orden=17, id="coral-gracias-dios", corpus="bach/bwv386", compases=(0, 99), RH=[0, 1], LH=[2, 3], MEL=[0],
         title="Ahora den gracias a Dios (Nun danket alle Gott)", composer="Johann Sebastian Bach", era="Barroco",
         work="Coral BWV 386", key="G", tempo=76, difficulty=2,
         tip="Una melodía muy conocida en su versión de himno. Mantén las notas largas hasta que se acaben.",
         fun="Es uno de los himnos luteranos más cantados en el mundo."),
    dict(orden=14, id="coral-oh-cabeza", corpus="bach/bwv244.54", compases=(0, 14), RH=[0, 1], LH=[2, 3], MEL=[0],
         title="Oh cabeza ensangrentada", composer="Johann Sebastian Bach", era="Barroco",
         work="Pasión según San Mateo, BWV 244 (1727)", key="Dm", tempo=60, difficulty=2,
         tip="Tonalidad menor, ritmo lento. Respira entre frases.",
         fun="Bach usa esta misma melodía cinco veces en la Pasión."),
]

if __name__ == "__main__":
    for m in MANIFIESTO:
        importar(m)
