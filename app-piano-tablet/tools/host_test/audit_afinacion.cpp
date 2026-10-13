// Auditoría de afinación: toca las 88 notas con el motor real y mide la frecuencia fundamental de cada una.
// Uso: audit_afinacion piano.bin   → tabla con la desviación en cents y las notas fuera de ±12 cents.
#include <cmath>
#include <cstdio>
#include <fstream>
#include <vector>
#include "../../android/app/src/main/cpp/engine.h"

static std::vector<uint8_t> readFile(const char* p) {
    std::ifstream f(p, std::ios::binary);
    return std::vector<uint8_t>((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
}

// Frecuencia por autocorrelación normalizada alrededor de la esperada (con interpolación parabólica).
static double medir(const std::vector<float>& x, int sr, double fEsp) {
    int lagMin = (int)(sr / (fEsp * 1.06)), lagMax = (int)(sr / (fEsp / 1.06)) + 2;
    int N = (int)x.size() - lagMax - 1;
    if (N < 2000) return -1;
    std::vector<double> r(lagMax + 2, 0.0);
    for (int lag = lagMin - 1; lag <= lagMax + 1; ++lag) {
        double s = 0, a = 0, b = 0;
        for (int i = 0; i < N; ++i) { s += x[i] * x[i + lag]; a += x[i] * x[i]; b += x[i + lag] * x[i + lag]; }
        r[lag] = s / std::sqrt(a * b + 1e-12);
    }
    int best = lagMin;
    for (int lag = lagMin; lag <= lagMax; ++lag) if (r[lag] > r[best]) best = lag;
    double y0 = r[best - 1], y1 = r[best], y2 = r[best + 1];
    double d = (y0 - 2 * y1 + y2);
    double off = d != 0 ? 0.5 * (y0 - y2) / d : 0;
    return sr / (best + off);
}

int main(int argc, char** argv) {
    if (argc < 2) { std::fprintf(stderr, "uso: %s piano.bin\n", argv[0]); return 2; }
    const int SR = 48000;
    pc::Engine e;
    e.setSampleRate(SR);
    auto bank = readFile(argv[1]);
    if (!e.loadBank(bank.data(), bank.size())) { std::fprintf(stderr, "banco inválido\n"); return 1; }
    // neutro: sin reverb, sin ecualización
    e.setParam(pc::P_REVERB_MIX, 0.0f); e.setParam(pc::P_MASTER, 1.0f);
    int malas = 0;
    std::printf("nota  esperada(Hz)  medida(Hz)  desviación(cents)  [vel 90]\n");
    for (int k = 21; k <= 108; ++k) {
        e.allNotesOff();
        std::vector<float> out;
        e.noteOn(k, 90);
        int total = (int)(1.6 * SR), pos = 0;
        while (pos < total) { int n = std::min(480, total - pos); size_t at = out.size(); out.resize(at + n * 2); e.render(out.data() + at, n); pos += n; }
        std::vector<float> mono;
        for (int i = (int)(0.25 * SR); i < (int)(1.4 * SR); ++i) mono.push_back(0.5f * (out[2 * i] + out[2 * i + 1]));
        double fEsp = 440.0 * std::pow(2.0, (k - 69) / 12.0);
        double f = medir(mono, SR, fEsp);
        double cents = f > 0 ? 1200.0 * std::log2(f / fEsp) : 999;
        bool mal = std::fabs(cents) > 12.0;
        if (mal) ++malas;
        std::printf("%3d   %9.2f   %9.2f   %+8.1f   %s\n", k, fEsp, f, cents, mal ? "<-- DESAFINADA" : "");
        e.noteOff(k);
        for (int i = 0; i < 40; ++i) { std::vector<float> d(960); e.render(d.data(), 480); }
    }
    std::printf("\nnotas fuera de ±12 cents: %d de 88\n", malas);
    return malas == 0 ? 0 : 1;
}
