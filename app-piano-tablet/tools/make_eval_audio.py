#!/usr/bin/env python3
"""Genera audio de prueba para medir el transcriptor: renderiza los MIDI del repertorio con el piano de
FluidR3 (muestras reales), con y sin reverb, y también tras pasar por MP3 a 128 kbps.
Guarda además la verdad (notas con su mano) en CSV: pitch,start_s,end_s,hand
Uso: python3 make_eval_audio.py [dir_salida]
"""
import json, os, subprocess, sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SALIDA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(RAIZ, "tools", "eval_data")
SF2 = "/usr/share/sounds/sf2/FluidR3_GM.sf2"
os.makedirs(SALIDA, exist_ok=True)
songs = json.load(open(os.path.join(RAIZ, "repertorio", "songs.json"), encoding="utf-8"))["canciones"]
midis = {os.path.basename(f)[3:-4]: os.path.join(RAIZ, "repertorio", "midi", f) for f in os.listdir(os.path.join(RAIZ, "repertorio", "midi")) if f.endswith(".mid")}
for s in songs:
    mid = midis.get(s["id"])
    if not mid:
        continue
    spb = 60.0 / s["bpm"]
    with open(os.path.join(SALIDA, s["id"] + ".csv"), "w") as f:
        f.write(f"# bpm={s['bpm']} compas={s['compas']} tonalidad={s['tonalidad']} anacrusa={s['anacrusa']}\n")
        for hand, key in (("R", "mdNotas"), ("L", "miNotas")):
            for a, d, p in s[key]:
                f.write(f"{p},{a * spb:.4f},{(a + d) * spb:.4f},{hand}\n")
    base = os.path.join(SALIDA, s["id"])
    # con reverb y coro por defecto de fluidsynth (más realista) y seco
    for tag, extra in (("", []), ("_seco", ["-R", "0", "-C", "0"])):
        wav = f"{base}{tag}.wav"
        subprocess.run(["fluidsynth", "-ni", "-g", "0.8", "-r", "44100", *extra, "-F", wav, SF2, mid],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    # ida y vuelta por MP3
    mp3 = base + ".mp3"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", base + ".wav", "-b:a", "128k", mp3], check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp3, "-ar", "44100", "-ac", "2", base + "_mp3.wav"], check=True)
    os.remove(mp3)
    print("ok", s["id"])
