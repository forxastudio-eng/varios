"""Mide la precisión real del OMR: partituras del corpus de music21 → MusicXML → PDF (LilyPond) → Audiveris → notas,
y compara con las notas originales. Se ejecuta en el trabajo de CI «omr-eval» (necesita lilypond y Audiveris).
"""
import os, subprocess, sys, tempfile, time, json, shutil
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from music21 import corpus
import conversion, omr

PIEZAS = ["bach/bwv846", "mozart/k545/movement1_exposition", "verdi/laDonnaEMobile", "schubert/Lindenbaum", "bach/bwv66.6", "bach/bwv269",
          "bach/bwv140.7", "bach/bwv80.8", "bach/bwv1.6", "bach/bwv244.54"]
SALIDA = sys.argv[1] if len(sys.argv) > 1 else "omr_eval_salida"
os.makedirs(SALIDA, exist_ok=True)


def f1(est, ver, tol=0.06):
    usados = set(); tp = 0
    for v in sorted(ver):
        mejor, md = None, 1e9
        for i, e in enumerate(est):
            if i in usados or e[1] != v[1]:
                continue
            d = abs(e[0] - v[0])
            if d <= tol and d < md:
                mejor, md = i, d
        if mejor is not None:
            usados.add(mejor); tp += 1
    p = tp / len(est) if est else 0; r = tp / len(ver) if ver else 0
    return p, r, (2 * p * r / (p + r) if p + r else 0)


def diagnostico(nombre, verdad, leido):
    """Separa los tipos de error: octava, cuadrícula de tiempo, notas que faltan o sobran."""
    ver = sorted((round(n[0], 3), n[2], round(n[1], 3)) for n in verdad["mdNotas"] + verdad["miNotas"])
    est = sorted((round(n[0], 3), n[2], round(n[1], 3)) for n in leido["mdNotas"] + leido["miNotas"])
    print(f"  [{nombre}] notas: verdad {len(ver)} · leído {len(est)} · pulso/compás verdad {verdad['pulsosCompas']}/{verdad['compas']} leído {leido['pulsosCompas']}/{leido['compas']} · bpm {verdad['bpm']}/{leido['bpm']} · tonalidad {verdad['tonalidad']}/{leido['tonalidad']}")
    for etiqueta, f in (("clase de nota (sin octava)", lambda n: (n[0], n[1] % 12)), ("solo instante", lambda n: (n[0], 0)), ("solo altura", lambda n: (0, n[1]))):
        a = [f(n) for n in est]; b = [f(n) for n in ver]
        from collections import Counter
        ca, cb = Counter(a), Counter(b)
        tp = sum((ca & cb).values())
        pr = tp / max(1, len(a)); re = tp / max(1, len(b))
        print(f"     {etiqueta}: P {pr:.2f} R {re:.2f}")
    print("     verdad[:10]:", ver[:10])
    print("     leído [:10]:", est[:10])
    print("     pitch mín/máx verdad", min(n[1] for n in ver), max(n[1] for n in ver), "leído", min(n[1] for n in est) if est else None, max(n[1] for n in est) if est else None)


def pdf_desde_musicxml(xml, dest):
    ly = os.path.join(dest, "p.ly")
    subprocess.run(["musicxml2ly", "-o", ly, xml], check=True, capture_output=True)
    subprocess.run(["lilypond", "-o", os.path.join(dest, "p"), ly], check=True, capture_output=True)
    return os.path.join(dest, "p.pdf")


filas = []
for nombre in PIEZAS:
    d = tempfile.mkdtemp(prefix="eval_")
    try:
        xml = os.path.join(d, "orig.musicxml")
        try:
            corpus.parse(nombre).write("musicxml", fp=xml)
            verdad = conversion.convertir(xml)["cancion"]
        except Exception as e:   # noqa: BLE001
            filas.append((nombre, "ERROR corpus: " + str(e)[:80].replace("\n", " "), 0, 0, 0, 0, 0)); continue
        try:
            pdf = pdf_desde_musicxml(xml, d)
        except Exception as e:   # noqa: BLE001
            filas.append((nombre, "ERROR LilyPond", 0, 0, 0, 0, 0)); continue
        shutil.copy(pdf, os.path.join(SALIDA, nombre.replace("/", "_") + ".pdf"))
        t0 = time.time()
        try:
            salida = omr.ejecutar_audiveris(pdf, os.path.join(d, "out"), lambda p, m: None, omr.contar_paginas_pdf(pdf))
            leido = conversion.convertir(salida)["cancion"]
            shutil.copy(salida, os.path.join(SALIDA, nombre.replace("/", "_") + os.path.splitext(salida)[1]))
        except Exception as e:   # noqa: BLE001
            filas.append((nombre, "ERROR OMR: " + str(e)[:80].replace("\n", " "), 0, 0, 0, 0, 0)); continue
        dt = time.time() - t0
        ver = [(n[0], n[2]) for n in verdad["mdNotas"] + verdad["miNotas"]]
        est = [(n[0], n[2]) for n in leido["mdNotas"] + leido["miNotas"]]
        diagnostico(nombre, verdad, leido)
        p, r, f = f1(est, ver)
        ver_r = [(n[0], n[2]) for n in verdad["mdNotas"]]; est_r = [(n[0], n[2]) for n in leido["mdNotas"]]
        _, _, fr = f1(est_r, ver_r)
        filas.append((nombre, "ok", len(ver), p, r, f, dt, leido["compases"], verdad["compases"], leido["compas"] == verdad["compas"]))
    finally:
        shutil.rmtree(d, ignore_errors=True)

print("\n%-36s %6s %6s %6s %6s %7s  compases  compás" % ("pieza", "notas", "P", "R", "F1", "tiempo"))
tot = []
for f in filas:
    if f[1] != "ok":
        print("%-36s %s" % (f[0], f[1])); continue
    print("%-36s %6d %6.3f %6.3f %6.3f %6.0fs  %3d/%3d   %s" % (f[0], f[2], f[3], f[4], f[5], f[6], f[7], f[8], "ok" if f[9] else "distinto"))
    tot.append(f[5])
if tot:
    print("\nF1 medio de notas (pitch + posición ±0,06 pulsos): %.3f sobre %d piezas" % (sum(tot) / len(tot), len(tot)))
json.dump([list(map(str, f)) for f in filas], open(os.path.join(SALIDA, "resultados.json"), "w"))
