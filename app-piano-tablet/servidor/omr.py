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


# Interlínea (distancia entre líneas del pentagrama) con la que Audiveris acierta mejor los ritmos:
# medido con partituras de prueba, a ~19 px falla en las figuras pequeñas y a ~28 px las lee bien.
INTERLINEA_OBJETIVO = 28.0


def estimar_interlinea(gris) -> float | None:
    """Distancia en píxeles entre las líneas de un pentagrama, por autocorrelación del perfil horizontal de tinta."""
    import numpy as np
    a = np.asarray(gris, dtype=np.float32)
    if a.ndim != 2 or min(a.shape) < 200:
        return None
    oscuro = (a < 128).astype(np.float32)
    perfil = oscuro.sum(axis=1)
    perfil -= perfil.mean()
    if perfil.std() < 1e-3:
        return None
    n = len(perfil)
    f = np.fft.rfft(perfil, 2 * n)
    ac = np.fft.irfft(f * np.conj(f))[:n]
    ac /= ac[0]
    lo, hi = 6, min(120, n // 4)
    zona = ac[lo:hi]
    # el primer máximo local fuerte es el espaciado entre líneas (los siguientes son múltiplos)
    pico = None
    for i in range(1, len(zona) - 1):
        if zona[i] > zona[i - 1] and zona[i] >= zona[i + 1] and zona[i] > 0.25 * zona.max() and zona[i] > 0.05:
            pico = i
            break
    if pico is None:
        return None
    return float(lo + pico)


def _normalizar_iluminacion(im):
    """Quita sombras y degradados de luz típicos de una foto: divide por una versión muy desenfocada del fondo."""
    import numpy as np
    from PIL import Image, ImageFilter
    a = np.asarray(im, dtype=np.float32)
    pequena = im.resize((max(1, im.width // 8), max(1, im.height // 8)), Image.BILINEAR)
    fondo = pequena.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(max(2, min(pequena.size) // 25)))
    fondo = np.asarray(fondo.resize(im.size, Image.BILINEAR), dtype=np.float32)
    out = np.clip(a / np.maximum(fondo, 1.0) * 255.0, 0, 255).astype(np.uint8)
    return Image.fromarray(out)


def estimar_inclinacion(gris) -> float:
    """Ángulo (grados) que hay que girar la imagen para que las líneas del pentagrama queden horizontales."""
    import numpy as np
    from PIL import Image
    f = 1800.0 / max(gris.size)
    pq = gris.resize((int(gris.width * f), int(gris.height * f)), Image.BILINEAR) if f < 1 else gris
    mejor, mejor_v = 0.0, -1.0
    for ang in np.arange(-4.0, 4.01, 0.25):
        r = pq.rotate(float(ang), resample=Image.BILINEAR, fillcolor=255)
        perfil = (np.asarray(r) < 128).sum(axis=1).astype(np.float64)
        v = float(np.var(np.diff(perfil)))
        if v > mejor_v:
            mejor, mejor_v = float(ang), v
    return mejor


def preparar_imagen(ruta: str, destino_dir: str) -> str:
    """Prepara una foto o escaneo para Audiveris: orientación, luz uniforme, enderezado y escala de pentagrama ideal."""
    from PIL import Image, ImageOps
    im = Image.open(ruta)
    im = ImageOps.exif_transpose(im).convert("L")
    im = _normalizar_iluminacion(im)
    im = ImageOps.autocontrast(im, cutoff=1)
    ang = estimar_inclinacion(im)
    if abs(ang) >= 0.2:
        im = im.rotate(ang, resample=Image.BICUBIC, expand=True, fillcolor=255)
    inter = estimar_interlinea(im)
    if inter:
        f = INTERLINEA_OBJETIVO / inter
    else:
        f = 1800 / min(im.size) if min(im.size) < 1800 else 1.0
    f = min(max(f, 0.5), 4.0)
    if abs(f - 1.0) > 0.08:
        im = im.resize((int(im.width * f), int(im.height * f)), Image.LANCZOS)
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
