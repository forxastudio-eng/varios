#!/usr/bin/env python3
"""Extrae el «Yamaha Grand Piano» de FluidR3_GM.sf2 (licencia MIT, Frank Wen) y lo guarda
en un formato compacto (`piano.bin`) que lee el motor de sonido C++ de la app.

Formato (little-endian):
  'PNO1' | u32 numZonas | u32 totalFrames
  por zona (40 bytes):
     u8 teclaMin | u8 teclaMax | u8 velMin | u8 velMax | u8 teclaRaiz | u8 pad | i16 afinacionCents
     u32 sampleRate | u32 offsetFrames | u32 numFrames | u32 loopIni | u32 loopFin
     f32 decaySeg100dB | f32 gainLineal | f32 pad
  PCM int16 estéreo intercalado (L,R)
"""
import struct, sys, math

SF2 = "/usr/share/sounds/sf2/FluidR3_GM.sf2"


def parse_riff(data):
    assert data[:4] == b"RIFF" and data[8:12] == b"sfbk"
    pos = 12
    chunks = {}
    while pos < len(data):
        cid = data[pos:pos + 4]; size = struct.unpack_from("<I", data, pos + 4)[0]
        body = data[pos + 8: pos + 8 + size]
        if cid == b"LIST":
            lt = body[:4]; sub = body[4:]
            p = 0
            while p < len(sub):
                scid = sub[p:p + 4]; ssz = struct.unpack_from("<I", sub, p + 4)[0]
                chunks[(lt, scid)] = sub[p + 8: p + 8 + ssz]
                p += 8 + ssz + (ssz & 1)
        pos += 8 + size + (size & 1)
    return chunks


def records(buf, fmt):
    sz = struct.calcsize(fmt)
    return [struct.unpack_from(fmt, buf, i * sz) for i in range(len(buf) // sz)]


def main(out_path):
    data = open(SF2, "rb").read()
    ch = parse_riff(data)
    smpl = ch[(b"sdta", b"smpl")]
    phdr = records(ch[(b"pdta", b"phdr")], "<20sHHHIII")
    pbag = records(ch[(b"pdta", b"pbag")], "<HH")
    pgen = records(ch[(b"pdta", b"pgen")], "<Hh")
    inst = records(ch[(b"pdta", b"inst")], "<20sH")
    ibag = records(ch[(b"pdta", b"ibag")], "<HH")
    igen = records(ch[(b"pdta", b"igen")], "<Hh")
    shdr = records(ch[(b"pdta", b"shdr")], "<20sIIIIIBbHH")

    # preset 0 banco 0
    pi = next(i for i, p in enumerate(phdr[:-1]) if p[1] == 0 and p[2] == 0)
    b0, b1 = phdr[pi][3], phdr[pi + 1][3]
    inst_idx = None
    for b in range(b0, b1):
        for g in pgen[pbag[b][0]:pbag[b + 1][0]]:
            if g[0] == 41:
                inst_idx = g[1]
    print("preset:", phdr[pi][0].split(b"\0")[0].decode(), "→ instrumento", inst[inst_idx][0].split(b"\0")[0].decode())
    ib0, ib1 = inst[inst_idx][1], inst[inst_idx + 1][1]
    glob = {}
    zonas = []
    for b in range(ib0, ib1):
        gens = {g[0]: g[1] for g in igen[ibag[b][0]:ibag[b + 1][0]]}
        if 53 not in gens:
            glob.update(gens); continue
        z = dict(glob); z.update(gens)
        zonas.append(z)
    print("zonas:", len(zonas))

    def rng(v, default=(0, 127)):
        return (v & 0xFF, (v >> 8) & 0xFF) if v is not None else default

    # agrupar pares L/R
    por_rango = {}
    for z in zonas:
        s = shdr[z[53]]
        kr = rng(z.get(43)); vr = rng(z.get(44))
        por_rango.setdefault((kr, vr), []).append((z, s))
    pcm = bytearray(); tabla = []; frames_tot = 0
    for (kr, vr), lista in sorted(por_rango.items()):
        izq = next((x for x in lista if x[1][8] in (4, 1)), lista[0])
        der = next((x for x in lista if x[1][8] == 2), None)
        canales = []
        for z, s in ([izq] + ([der] if der else [izq])):
            nombre, ini, fin, li, lf, sr, raiz, corr, link, tipo = s
            ini += z.get(0, 0) + (z.get(4, 0) << 15)
            fin += z.get(1, 0) + (z.get(12, 0) << 15)
            li += z.get(2, 0) + (z.get(45, 0) << 15)
            lf += z.get(3, 0) + (z.get(50, 0) << 15)
            x = struct.unpack_from(f"<{fin - ini}h", smpl, ini * 2)
            canales.append((x, li - ini, lf - ini))
        n = min(len(canales[0][0]), len(canales[1][0]))
        L, R = canales[0][0][:n], canales[1][0][:n]
        li, lf = canales[0][1], canales[0][2]
        z, s = izq
        raiz = z.get(58, s[6])
        fino = z.get(51, 0) * 100 + z.get(52, 0) + s[7]
        decay_tc = z.get(36, -12000)
        decay_s = 2 ** (decay_tc / 1200.0)
        atten_db = z.get(48, 0) / 10.0
        modo_loop = z.get(54, 0) & 3
        if modo_loop not in (1, 3):
            li, lf = 0, 0       # sin bucle
        tabla.append((kr[0], kr[1], vr[0], vr[1], raiz, 0, int(fino), s[5], frames_tot, n, max(li, 0), min(lf, n), decay_s, 10 ** (-atten_db / 20)))
        inter = bytearray(n * 4)
        struct.pack_into(f"<{2 * n}h", inter, 0, *[v for pair in zip(L, R) for v in pair])
        pcm += inter
        frames_tot += n
    with open(out_path, "wb") as f:
        f.write(b"PNO1" + struct.pack("<II", len(tabla), frames_tot))
        for t in tabla:
            f.write(struct.pack("<BBBBBBhIIIIIfff", *t, 0.0))
        f.write(pcm)
    print(f"{len(tabla)} zonas, {frames_tot} frames, {len(pcm) / 1e6:.2f} MB → {out_path}")
    for t in tabla:
        print(f"  teclas {t[0]:3d}-{t[1]:3d} vel {t[2]}-{t[3]} raíz {t[4]} sr {t[7]} frames {t[9]:6d} loop {t[10]}-{t[11]} decay100dB {t[12]:.1f}s gain {t[13]:.2f}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "../android/app/src/main/assets/piano.bin")
