"""Ejecución de Audiveris (reconocimiento óptico musical) y preparación de imágenes."""
from __future__ import annotations
import glob
import os
import re
import shutil
import subprocess
from typing import Callable

CANDIDATOS_EXE = [
    os.environ.get("AUDIVERIS_BIN", ""),
    "/opt/audiveris/bin/Audiveris",
    "/opt/audiveris/bin/audiveris",
    "/usr/bin/audiveris",
    "/usr/local/bin/audiveris",
]


def localizar_audiveris() -> str | None:
    for c in CANDIDATOS_EXE:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c
    return shutil.which("audiveris") or shutil.which("Audiveris")


def preparar_imagen(ruta: str, destino_dir: str) -> str:
    """Corrige orientación EXIF, pasa a gris, sube la resolución (Audiveris rinde mejor con ≥ 300 ppp) y realza el contraste."""
    from PIL import Image, ImageOps
    im = Image.open(ruta)
    im = ImageOps.exif_transpose(im).convert("L")
    corto = min(im.size)
    if corto < 1800:
        f = 1800 / corto
        im = im.resize((int(im.width * f), int(im.height * f)), Image.LANCZOS)
    im = ImageOps.autocontrast(im, cutoff=1)
    destino = os.path.join(destino_dir, "entrada.png")
    im.save(destino)
    return destino


def contar_paginas_pdf(ruta: str) -> int:
    try:
        from pypdf import PdfReader
        return len(PdfReader(ruta).pages)
    except Exception:   # noqa: BLE001
        return 0


def ejecutar_audiveris(entrada: str, salida: str, progreso: Callable[[float, str], None], paginas: int = 0,
                       tiempo_max: int = 20 * 60) -> str:
    """Devuelve la ruta del MusicXML exportado."""
    exe = localizar_audiveris()
    if not exe:
        raise RuntimeError("Audiveris no está instalado en el servidor (usa la imagen Docker del proyecto)")
    os.makedirs(salida, exist_ok=True)
    cmd = [exe, "-batch", "-export", "-output", salida, "--", entrada]
    progreso(0.1, "Leyendo la partitura…")
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")
    hechas = 0
    ultimas: list[str] = []
    import time
    t0 = time.time()
    assert p.stdout is not None
    for linea in p.stdout:
        ultimas = (ultimas + [linea.rstrip()])[-40:]
        if re.search(r"(?i)(sheet|page)[^\n]*(transcribed|exported|completed)", linea):
            hechas += 1
            if paginas:
                progreso(0.1 + 0.8 * min(1.0, hechas / paginas), f"Leyendo página {min(hechas + 1, paginas)} de {paginas}…")
        elif re.search(r"(?i)(step|stage)", linea) and paginas == 0:
            progreso(0.4, "Reconociendo símbolos…")
        if time.time() - t0 > tiempo_max:
            p.kill()
            raise RuntimeError("Audiveris tardó demasiado")
    p.wait()
    salidas = glob.glob(os.path.join(salida, "**", "*.mxl"), recursive=True) + \
        glob.glob(os.path.join(salida, "**", "*.musicxml"), recursive=True) + \
        glob.glob(os.path.join(salida, "**", "*.xml"), recursive=True)
    salidas = [s for s in salidas if os.path.getsize(s) > 200]
    if not salidas:
        cola = "\n".join(ultimas[-12:])
        raise RuntimeError("Audiveris no pudo leer la partitura. ¿Es una imagen nítida y recta?\n" + cola)
    return max(salidas, key=os.path.getsize)
