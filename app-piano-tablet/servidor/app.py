"""Servidor en casa de Piano Cascada: convierte partituras (PDF, foto, MusicXML) en canciones para la app.

  GET  /health          → {"ok": true, "omr": bool}
  POST /omr             → multipart «file»: PDF, imagen o MusicXML. Devuelve {"job": id}
  GET  /jobs/{id}       → {"status": queued|running|done|error, "progress", "message", "result", "warnings", "error"}
"""
from __future__ import annotations
import os
import queue
import shutil
import socket
import tempfile
import threading
import time
import traceback
import uuid

from fastapi import FastAPI, File, HTTPException, UploadFile

import conversion
import omr

app = FastAPI(title="Servidor de partituras de Piano Cascada", version="1.0")
JOBS: dict[str, dict] = {}
COLA: "queue.Queue[tuple[str, str, str]]" = queue.Queue()
MAX_BYTES = 60 * 1024 * 1024


def _set(job: str, **kw):
    JOBS[job].update(kw)


def _trabajo(job: str, ruta: str, nombre: str):
    dir_ = os.path.dirname(ruta)
    try:
        ext = os.path.splitext(nombre)[1].lower()
        _set(job, status="running", progress=0.05, message="Preparando el archivo…")
        titulo = os.path.splitext(os.path.basename(nombre))[0].replace("_", " ").strip() or "Mi partitura"
        if ext in (".xml", ".musicxml", ".mxl"):
            xml = ruta
            _set(job, progress=0.6, message="Convirtiendo la partitura…")
        else:
            entrada = ruta
            if ext not in (".pdf",):
                entrada = omr.preparar_imagen(ruta, dir_)
            paginas = omr.contar_paginas_pdf(entrada) if entrada.lower().endswith(".pdf") else 1
            xml = omr.ejecutar_audiveris(entrada, os.path.join(dir_, "salida"), lambda p, m: _set(job, progress=p, message=m), paginas)
            _set(job, progress=0.9, message="Convirtiendo a notas…")
        r = conversion.convertir(xml, titulo=titulo, id_="mi-partitura")
        _set(job, status="done", progress=1.0, message="Listo", result=conversion.documento(r["cancion"]), warnings=r["avisos"])
    except Exception as e:   # noqa: BLE001
        traceback.print_exc()
        _set(job, status="error", error=str(e)[:600], progress=1.0)
    finally:
        shutil.rmtree(dir_, ignore_errors=True)


def _worker():
    while True:
        job, ruta, nombre = COLA.get()
        _trabajo(job, ruta, nombre)
        COLA.task_done()


threading.Thread(target=_worker, daemon=True).start()


@app.get("/health")
def health():
    return {"ok": True, "omr": omr.localizar_audiveris() is not None, "version": "1.0"}


@app.post("/omr", status_code=202)
async def subir(file: UploadFile = File(...)):
    nombre = file.filename or "partitura.pdf"
    ext = os.path.splitext(nombre)[1].lower()
    if ext not in (".pdf", ".png", ".jpg", ".jpeg", ".tif", ".tiff", ".bmp", ".webp", ".xml", ".musicxml", ".mxl"):
        raise HTTPException(415, f"Tipo de archivo no soportado: {ext}")
    d = tempfile.mkdtemp(prefix="pc_")
    ruta = os.path.join(d, "entrada" + ext)
    total = 0
    with open(ruta, "wb") as f:
        while chunk := await file.read(1 << 20):
            total += len(chunk)
            if total > MAX_BYTES:
                shutil.rmtree(d, ignore_errors=True)
                raise HTTPException(413, "Archivo demasiado grande (máximo 60 MB)")
            f.write(chunk)
    job = uuid.uuid4().hex[:12]
    JOBS[job] = {"status": "queued", "progress": 0.0, "message": "En cola…", "t": time.time()}
    # limpieza de trabajos antiguos
    for k in [k for k, v in JOBS.items() if time.time() - v.get("t", 0) > 3600]:
        JOBS.pop(k, None)
    COLA.put((job, ruta, nombre))
    return {"job": job}


@app.get("/jobs/{job}")
def estado(job: str):
    j = JOBS.get(job)
    if not j:
        raise HTTPException(404, "Trabajo desconocido")
    return {k: v for k, v in j.items() if k != "t"}


def direcciones_lan() -> list[str]:
    ips = set()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("10.255.255.255", 1)); ips.add(s.getsockname()[0]); s.close()
    except Exception:   # noqa: BLE001
        pass
    try:
        for i in socket.gethostbyname_ex(socket.gethostname())[2]:
            if not i.startswith("127."):
                ips.add(i)
    except Exception:   # noqa: BLE001
        pass
    return sorted(ips)


@app.on_event("startup")
def _aviso():
    port = os.environ.get("PORT", "8765")
    print("\n  Servidor de partituras listo. Escribe en la app (Añadir canción → Servidor en casa):")
    for ip in direcciones_lan():
        print(f"     http://{ip}:{port}")
    print(f"  Audiveris: {omr.localizar_audiveris() or 'NO ENCONTRADO'}\n", flush=True)
