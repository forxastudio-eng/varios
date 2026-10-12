"""Pruebas del convertidor con partituras reales del corpus de music21 (exportadas a MusicXML)."""
import os, sys, tempfile
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from music21 import corpus
import conversion


def _xml(nombre: str, tmp: str) -> str:
    ruta = os.path.join(tmp, nombre.replace("/", "_") + ".musicxml")
    corpus.parse(nombre).write("musicxml", fp=ruta)
    return ruta


def test_preludio_de_bach_dos_manos():
    with tempfile.TemporaryDirectory() as tmp:
        r = conversion.convertir(_xml("bach/bwv846", tmp), id_="prelude")
    c = r["cancion"]
    assert c["compas"] == "4/4" and c["tonalidad"] == "C"
    assert c["compases"] >= 34
    assert len(c["mdNotas"]) > 300 and len(c["miNotas"]) > 100
    assert all(21 <= n[2] <= 108 for n in c["mdNotas"] + c["miNotas"])
    assert c["mdNotas"][0][2] > c["miNotas"][0][2]          # derecha más aguda que izquierda al empezar


def test_sonata_k545_notas_exactas():
    with tempfile.TemporaryDirectory() as tmp:
        c = conversion.convertir(_xml("mozart/k545/movement1_exposition", tmp))["cancion"]
    primeras = [n[2] for n in c["mdNotas"][:3]]
    assert primeras == [72, 76, 79]                           # C5 E5 G5
    assert c["miNotas"][0][2] == 60                           # C4 del bajo de Alberti


def test_coral_con_cuatro_voces_reparte_en_dos_manos():
    with tempfile.TemporaryDirectory() as tmp:
        c = conversion.convertir(_xml("bach/bwv80.8", tmp))["cancion"]
    assert c["miNotas"] and c["mdNotas"]
    assert c["anacrusa"] > 0                                    # el coral empieza con anacrusa
    assert "melNotas" in c                                      # la mano derecha lleva dos voces: se extrae la melodía


XML_IRREGULAR = """<?xml version="1.0" encoding="UTF-8"?>
<score-partwise version="3.1"><part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
<part id="P1">
<measure number="1"><attributes><divisions>1</divisions><key><fifths>0</fifths></key><time><beats>4</beats><beat-type>4</beat-type></time><clef><sign>G</sign><line>2</line></clef></attributes>
<note><pitch><step>C</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
<note><pitch><step>D</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note>
<note><pitch><step>E</step><octave>4</octave></pitch><duration>1</duration><type>quarter</type></note></measure>
<measure number="2">
<note><pitch><step>F</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></measure>
</part></score-partwise>"""


def test_compases_irregulares_generan_aviso():
    with tempfile.TemporaryDirectory() as tmp:
        ruta = os.path.join(tmp, "x.musicxml")
        open(ruta, "w").write(XML_IRREGULAR)
        r = conversion.convertir(ruta)
    assert any("Compás 1" in a for a in r["avisos"]), r["avisos"]
    assert r["cancion"]["miNotas"] == [] and len(r["cancion"]["mdNotas"]) == 4
