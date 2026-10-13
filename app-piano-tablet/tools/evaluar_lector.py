#!/usr/bin/env python3
"""Mide la precisión del lector de partituras de la app (módulo omr) con piezas de las que se conoce la partitura exacta.

1. Se generan las páginas (PNG) de cada pieza a partir de un PDF:  pdftoppm -r 150 -png pieza.pdf carpeta/p
2. Se lee cada carpeta con la prueba BenchTest:
     OMR_MODELS=… OMR_BENCH_DIR=carpeta_de_piezas OMR_BENCH_OUT=salida ./gradlew :omr:test --tests '*BenchTest*'
3. Este script compara cada salida/<pieza>.json con el MusicXML original de la pieza.
Uso: evaluar_lector.py salida carpeta_con_musicxml_originales
"""
import json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'servidor'))
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'servidor', 'tests'))
import conversion, metricas

def main(salida, originales):
    filas = []
    for f in sorted(os.listdir(salida)):
        if not f.endswith('.json'):
            continue
        pieza = f[:-5]
        leida = json.load(open(os.path.join(salida, f)))['canciones'][0]
        xml = os.path.join(originales, pieza + '.musicxml')
        if not os.path.exists(xml):
            continue
        ver = conversion.convertir(xml)['cancion']
        V = [(n[0], n[2]) for n in ver['mdNotas'] + ver['miNotas']]
        E = [(n[0], n[2]) for n in leida['mdNotas'] + leida['miNotas']]
        m = metricas.comparar(V, E)
        filas.append((pieza, m))
        print(f"{pieza:24s} contenido {m['contenido']:.3f}  ritmo {m['ritmo']:.3f}  estricta {m['estricta']:.3f}  ({m['notas_verdad']} → {m['notas_leidas']} notas)")
    if filas:
        print("media: contenido %.3f  ritmo %.3f" % (sum(m['contenido'] for _, m in filas) / len(filas), sum(m['ritmo'] for _, m in filas) / len(filas)))

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
