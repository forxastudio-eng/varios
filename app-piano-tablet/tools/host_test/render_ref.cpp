// Renderiza con el motor real (C++) un guion de órdenes y escribe el audio (float32 estéreo) en un archivo.
// Sirve de referencia para comprobar el motor en Kotlin de la versión de escritorio.
// Uso: render_ref piano.bin guion.txt salida.raw
// Órdenes: param ID VALOR | noteon K V | noteoff K | pedal 0/1 | alloff | seqload N (siguen N líneas «K V MANO INICIO_MS DUR_MS»)
//          seqplay 0/1 | seqseek MS | seqspeed X | seqgate MS | seqloop A B 0/1 | handgain H G | seqstop | render FRAMES
#include <cstdio>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include "../../android/app/src/main/cpp/engine.h"

int main(int argc, char** argv) {
    if (argc < 4) { std::fprintf(stderr, "uso: %s piano.bin guion.txt salida.raw\n", argv[0]); return 2; }
    std::ifstream bf(argv[1], std::ios::binary);
    std::vector<uint8_t> bank((std::istreambuf_iterator<char>(bf)), std::istreambuf_iterator<char>());
    pc::Engine e;
    e.setSampleRate(48000.0f);
    if (!e.loadBank(bank.data(), bank.size())) { std::fprintf(stderr, "banco inválido\n"); return 1; }
    std::ifstream sf(argv[2]);
    std::FILE* out = std::fopen(argv[3], "wb");
    std::string line;
    while (std::getline(sf, line)) {
        std::istringstream in(line);
        std::string cmd;
        if (!(in >> cmd) || cmd[0] == '#') continue;
        if (cmd == "param") { int id; float v; in >> id >> v; e.setParam(id, v); }
        else if (cmd == "noteon") { int k, v; in >> k >> v; e.noteOn(k, v); }
        else if (cmd == "noteoff") { int k; in >> k; e.noteOff(k); }
        else if (cmd == "pedal") { int d; in >> d; e.setPedal(d != 0); }
        else if (cmd == "alloff") e.allNotesOff();
        else if (cmd == "seqload") {
            int n; in >> n;
            std::vector<int32_t> k(n), v(n), h(n); std::vector<double> s(n), d(n);
            for (int i = 0; i < n; ++i) { std::getline(sf, line); std::istringstream r(line); r >> k[i] >> v[i] >> h[i] >> s[i] >> d[i]; }
            e.loadSequence(k.data(), v.data(), h.data(), s.data(), d.data(), n);
        }
        else if (cmd == "seqplay") { int d; in >> d; e.seqPlay(d != 0); }
        else if (cmd == "seqseek") { double ms; in >> ms; e.seqSeek(ms); }
        else if (cmd == "seqspeed") { float x; in >> x; e.seqSpeed(x); }
        else if (cmd == "seqgate") { double ms; in >> ms; e.seqGate(ms); }
        else if (cmd == "seqloop") { double a, b; int on; in >> a >> b >> on; e.seqLoop(a, b, on != 0); }
        else if (cmd == "handgain") { int h; float g; in >> h >> g; e.setHandGain(h, g); }
        else if (cmd == "seqstop") e.seqStopNotes();
        else if (cmd == "render") {
            int frames; in >> frames;
            std::vector<float> buf((size_t)frames * 2);
            // se renderiza en bloques de 480 como hace la salida de audio
            int done = 0;
            while (done < frames) { int n = std::min(480, frames - done); e.render(buf.data() + (size_t)done * 2, n); done += n; }
            std::fwrite(buf.data(), sizeof(float), buf.size(), out);
        }
    }
    std::fclose(out);
    return 0;
}
