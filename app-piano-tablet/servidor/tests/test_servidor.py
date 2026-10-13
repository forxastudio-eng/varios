import os, sys, tempfile, time
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from fastapi.testclient import TestClient
from music21 import corpus
import app as servidor

cliente = TestClient(servidor.app)


def test_health():
    r = cliente.get("/health").json()
    assert r["ok"] is True and "omr" in r


def _esperar(job, tope=60):
    t0 = time.time()
    while time.time() - t0 < tope:
        j = cliente.get(f"/jobs/{job}").json()
        if j["status"] in ("done", "error"):
            return j
        time.sleep(0.2)
    raise AssertionError("tiempo agotado")


def test_musicxml_se_convierte_sin_audiveris():
    with tempfile.TemporaryDirectory() as tmp:
        ruta = os.path.join(tmp, "Sonata facil.musicxml")
        corpus.parse("mozart/k545/movement1_exposition").write("musicxml", fp=ruta)
        with open(ruta, "rb") as f:
            r = cliente.post("/omr", files={"file": ("Sonata facil.musicxml", f, "application/xml")})
    assert r.status_code == 202
    j = _esperar(r.json()["job"])
    assert j["status"] == "done", j
    c = j["result"]["canciones"][0]
    assert c["titulo"] == "Sonata facil" and c["tonalidad"] == "C" and len(c["mdNotas"]) > 100


def test_rechaza_tipos_desconocidos():
    r = cliente.post("/omr", files={"file": ("virus.exe", b"MZ", "application/octet-stream")})
    assert r.status_code == 415


def test_pdf_sin_audiveris_da_error_claro():
    if servidor.omr.localizar_audiveris():
        return
    r = cliente.post("/omr", files={"file": ("x.pdf", b"%PDF-1.4\n%%EOF", "application/pdf")})
    j = _esperar(r.json()["job"])
    assert j["status"] == "error" and "Audiveris" in j["error"]


def test_trabajo_inexistente():
    assert cliente.get("/jobs/nope").status_code == 404


def test_progreso_de_audiveris_se_lee_del_registro():
    import omr
    m = omr.PASO_AUDIVERIS.search("2026-10-12 22:57:57,687 INFO [bach_bwv846#2]       StepMonitoring.java:98   | SCALE\n")
    assert m and m.group(1) == "2" and m.group(2) == "SCALE" and "SCALE" in omr.PASOS_AUDIVERIS
    assert omr.PASO_AUDIVERIS.search("INFO [x#1] Voice.java:257 | Measure{#1} Voice{#5 excess:7/16} too long") is None


def test_estimar_interlinea_de_pentagrama_sintetico():
    import omr
    from PIL import Image, ImageDraw
    im = Image.new("L", (900, 1200), 255)
    d = ImageDraw.Draw(im)
    for sistema in range(4):
        y0 = 150 + sistema * 250
        for k in range(5):
            d.line([(60, y0 + k * 24), (840, y0 + k * 24)], fill=0, width=2)
    assert omr.estimar_interlinea(im) == 24.0
