#!/usr/bin/env python3
"""Afina el banco de sonidos (piano.bin).

Cada zona del banco es una muestra de piano estirada a 4-6 teclas. En el soundfont original esas muestras no están
afinadas entre sí: teclas vecinas de zonas distintas llegan a diferir hasta 20 centésimas, y suena a «nota falsa».

Método: se toca cada tecla con el motor real (tools/host_test/audit_afinacion), se mide su frecuencia, se ajusta una
curva suave a las 88 medidas (esa curva contiene la inarmonicidad natural del piano y el sesgo del medidor) y se corrige
la afinación de cada zona para que sus teclas queden sobre esa curva. Se repite hasta que converge.

Uso: afinar_piano.py piano.bin   (compila audit_afinacion si hace falta)
"""
import os, re, struct, subprocess, sys
import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
AUDIT = "/tmp/audit_afinacion"


def compilar_audit():
    fuentes = [os.path.join(AQUI, "host_test", "audit_afinacion.cpp"), os.path.join(AQUI, "..", "android", "app", "src", "main", "cpp", "engine.cpp")]
    subprocess.run(["g++", "-O2", "-std=c++17", "-o", AUDIT] + fuentes, check=True)


def medir(ruta):
    out = subprocess.run([AUDIT, ruta], capture_output=True, text=True).stdout
    cents = {}
    for linea in out.splitlines():
        m = re.match(r"\s*(\d+)\s+([\d.]+)\s+([\d.]+)\s+([+-][\d.]+)", linea)
        if m:
            cents[int(m.group(1))] = float(m.group(4))
    return np.array([cents[k] for k in range(21, 109)])


def zonas(datos):
    nz = struct.unpack_from("<I", datos, 4)[0]
    z = []
    for i in range(nz):
        klo, khi = datos[12 + i * 40], datos[12 + i * 40 + 1]
        fino = struct.unpack_from("<h", datos, 12 + i * 40 + 6)[0]
        z.append((i, klo, khi, fino))
    return z


def resumen(c, etiqueta):
    saltos = np.abs(np.diff(c))
    print(f"{etiqueta}: salto máximo entre teclas vecinas {saltos.max():.1f} cents · media {saltos.mean():.1f} cents")


def main(ruta):
    compilar_audit()
    datos = bytearray(open(ruta, "rb").read())
    c = medir(ruta)
    resumen(c, "antes   ")
    ks = np.arange(21, 109)
    for vuelta in range(3):
        suave = np.polyval(np.polyfit(ks, c, 4), ks)
        for i, klo, khi, fino in zonas(datos):
            sel = [k - 21 for k in range(max(klo, 21), min(khi, 108) + 1)]
            if not sel:
                continue
            delta = float(np.mean(suave[sel] - c[sel]))
            nuevo = int(round(fino + delta))
            struct.pack_into("<h", datos, 12 + i * 40 + 6, nuevo)
        open(ruta, "wb").write(datos)
        c = medir(ruta)
        resumen(c, f"vuelta {vuelta + 1}")
    for i, klo, khi, fino in zonas(datos):
        print(f"  zona {i:2d} teclas {klo:3d}-{khi:3d}: corrección {fino:+d} cents")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "..", "android", "app", "src", "main", "assets", "piano.bin"))
