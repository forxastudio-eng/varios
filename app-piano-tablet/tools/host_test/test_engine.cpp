// Prueba del motor de sonido en el PC. Uso: test_engine piano.bin salida_dir [secuencia.csv]
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "../../android/app/src/main/cpp/engine.h"

static std::vector<uint8_t> readFile(const std::string& p) {
    std::ifstream f(p, std::ios::binary);
    return std::vector<uint8_t>((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
}

static void writeWav(const std::string& path, const std::vector<float>& st, int sr) {
    std::ofstream f(path, std::ios::binary);
    uint32_t n = st.size() * 2, rate = sr, br = sr * 4;
    uint16_t ch = 2, bps = 16, ba = 4, fmt = 1;
    uint32_t sz = 36 + n, sixteen = 16;
    f.write("RIFF", 4); f.write((char*)&sz, 4); f.write("WAVEfmt ", 8); f.write((char*)&sixteen, 4);
    f.write((char*)&fmt, 2); f.write((char*)&ch, 2); f.write((char*)&rate, 4); f.write((char*)&br, 4);
    f.write((char*)&ba, 2); f.write((char*)&bps, 2); f.write("data", 4); f.write((char*)&n, 4);
    for (float v : st) {
        v = std::fmax(-1.f, std::fmin(1.f, v));
        int16_t s = (int16_t)std::lrintf(v * 32767.f);
        f.write((char*)&s, 2);
    }
}

static void renderSeconds(pc::Engine& e, std::vector<float>& out, double sec, int sr) {
    int total = (int)(sec * sr), pos = 0;
    while (pos < total) {
        int n = std::min(480, total - pos);  // bloques de 10 ms, como un callback real
        size_t at = out.size();
        out.resize(at + n * 2);
        e.render(out.data() + at, n);
        pos += n;
    }
}

int main(int argc, char** argv) {
    if (argc < 3) { std::fprintf(stderr, "uso: %s piano.bin dir [seq.csv]\n", argv[0]); return 2; }
    const int SR = 48000;
    std::string dir = argv[2];
    pc::Engine e;
    e.setSampleRate(SR);
    auto bank = readFile(argv[1]);
    if (!e.loadBank(bank.data(), bank.size())) { std::fprintf(stderr, "banco inválido\n"); return 1; }
    int fails = 0;
    auto check = [&](bool ok, const char* what) { std::printf("%s %s\n", ok ? "  OK " : "  FALLA", what); if (!ok) ++fails; };

    // 1) una nota (C4 = 60), 3 s sostenida y 1.5 s de liberación
    {
        std::vector<float> out;
        e.noteOn(60, 100);
        renderSeconds(e, out, 3.0, SR);
        e.noteOff(60);
        renderSeconds(e, out, 1.5, SR);
        writeWav(dir + "/nota_c4.wav", out, SR);
        float peak = 0; bool nan = false;
        for (float v : out) { peak = std::fmax(peak, std::fabs(v)); if (!std::isfinite(v)) nan = true; }
        std::printf("nota C4: pico %.3f voces activas al final %d\n", peak, e.activeVoices());
        check(!nan, "sin NaN/Inf");
        check(peak > 0.05f && peak <= 1.0f, "nivel razonable (0.05..1.0)");
    }
    // 2) escala + acorde con pedal
    {
        std::vector<float> out;
        int scale[] = {60, 62, 64, 65, 67, 69, 71, 72};
        for (int k : scale) { e.noteOn(k, 90); renderSeconds(e, out, 0.30, SR); e.noteOff(k); }
        e.setPedal(true);
        for (int k : {48, 55, 64, 67, 72, 76}) e.noteOn(k, 110);
        renderSeconds(e, out, 1.0, SR);
        for (int k : {48, 55, 64, 67, 72, 76}) e.noteOff(k);
        renderSeconds(e, out, 2.0, SR);       // el pedal las mantiene
        e.setPedal(false);
        renderSeconds(e, out, 2.0, SR);
        writeWav(dir + "/escala_acorde.wav", out, SR);
        float peak = 0;
        for (float v : out) peak = std::fmax(peak, std::fabs(v));
        check(peak <= 1.0f, "el limitador evita el recorte");
    }
    // 3) rango completo: todas las teclas, para detectar zonas rotas
    {
        std::vector<float> out;
        for (int k = 21; k <= 108; k += 1) { e.noteOn(k, 80); renderSeconds(e, out, 0.25, SR); e.noteOff(k); }
        renderSeconds(e, out, 1.0, SR);
        writeWav(dir + "/rango_completo.wav", out, SR);
    }
    // 4) rendimiento: 64 notas sostenidas 10 s
    {
        std::vector<float> out;
        for (int k = 30; k < 94; ++k) e.noteOn(k, 100);
        auto t0 = std::chrono::steady_clock::now();
        renderSeconds(e, out, 10.0, SR);
        double dt = std::chrono::duration<double>(std::chrono::steady_clock::now() - t0).count();
        std::printf("rendimiento: 64 voces, 10 s de audio en %.3f s (%.1f%% de un núcleo, x%.0f tiempo real)\n", dt, dt * 10.0, 10.0 / dt);
        check(dt < 5.0, "64 voces con margen de tiempo real");
        for (int k = 30; k < 94; ++k) e.noteOff(k);
        renderSeconds(e, out, 3.0, SR);
        e.allNotesOff();
        renderSeconds(e, out, 1.0, SR);
    }
    // 5) secuenciador
    if (argc > 3) {
        std::ifstream f(argv[3]);
        std::vector<int32_t> k, v, h; std::vector<double> s, d;
        std::string line;
        while (std::getline(f, line)) {
            std::istringstream is(line);
            int kk, vv, hh; double ss, dd;
            if (is >> kk >> vv >> hh >> ss >> dd) { k.push_back(kk); v.push_back(vv); h.push_back(hh); s.push_back(ss); d.push_back(dd); }
        }
        e.loadSequence(k.data(), v.data(), h.data(), s.data(), d.data(), (int)k.size());
        std::vector<float> out;
        renderSeconds(e, out, 0.05, SR);            // procesa el comando
        e.seqPlay(true);
        renderSeconds(e, out, 4.0, SR);
        double p = e.positionMs();
        std::printf("secuenciador: tras 4 s de audio la posición es %.1f ms (esperado ~4000)\n", p);
        check(std::fabs(p - 4000.0) < 30.0, "reloj de audio exacto (±30 ms)");
        // velocidad 50 %
        e.seqSpeed(0.5f);
        renderSeconds(e, out, 2.0, SR);
        double p2 = e.positionMs();
        std::printf("a velocidad 0.5: +%.1f ms en 2 s (esperado ~1000)\n", p2 - p);
        check(std::fabs((p2 - p) - 1000.0) < 20.0, "velocidad 50 % correcta");
        e.seqSpeed(1.0f);
        // puerta (modo espera): no debe pasar de 6500 ms
        e.seqGate(p2 + 800.0);
        renderSeconds(e, out, 3.0, SR);
        double p3 = e.positionMs();
        std::printf("puerta en %.1f: posición %.3f, esperando=%d\n", p2 + 800.0, p3, (int)e.isWaiting());
        check(std::fabs(p3 - (p2 + 800.0)) < 0.01 && e.isWaiting(), "la puerta detiene la reproducción exactamente");
        e.seqGate(1e18);
        renderSeconds(e, out, 1.0, SR);
        check(e.positionMs() > p3 + 900.0 && !e.isWaiting(), "al abrir la puerta continúa");
        // bucle
        e.seqSeek(1000.0);
        e.seqLoop(1000.0, 3000.0, true);
        renderSeconds(e, out, 5.0, SR);
        double p4 = e.positionMs();
        std::printf("bucle 1000–3000 ms tras 5 s: posición %.1f\n", p4);
        check(p4 >= 1000.0 && p4 < 3000.0, "el bucle mantiene la posición dentro del tramo");
        // mano izquierda muda
        e.seqLoop(0, 0, false);
        e.seqSeek(0); e.setHandGain(1, 0.0f);
        renderSeconds(e, out, 0.1, SR);
        e.seqPlay(true);
        renderSeconds(e, out, 6.0, SR);
        writeWav(dir + "/cancion.wav", out, SR);
    }
    std::printf("%s (%d fallos)\n", fails ? "RESULTADO: FALLOS" : "RESULTADO: TODO OK", fails);
    return fails ? 1 : 0;
}
