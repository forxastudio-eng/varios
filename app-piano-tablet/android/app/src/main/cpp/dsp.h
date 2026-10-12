// Bloques de procesado de señal: filtros biquad (RBJ), reverb tipo Freeverb y límite suave.
#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace pc {

constexpr float kPi = 3.14159265358979323846f;

struct Biquad {
    float b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0;
    float z1 = 0, z2 = 0;

    inline float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
    void reset() { z1 = z2 = 0; }
    void setIdentity() { b0 = 1; b1 = b2 = a1 = a2 = 0; }

    void set(float nb0, float nb1, float nb2, float na0, float na1, float na2) {
        b0 = nb0 / na0; b1 = nb1 / na0; b2 = nb2 / na0;
        a1 = na1 / na0; a2 = na2 / na0;
    }
    void setPeaking(float sr, float f, float q, float gainDb) {
        if (std::fabs(gainDb) < 0.01f) { setIdentity(); return; }
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2 * kPi * f / sr, c = std::cos(w0), alpha = std::sin(w0) / (2 * q);
        set(1 + alpha * A, -2 * c, 1 - alpha * A, 1 + alpha / A, -2 * c, 1 - alpha / A);
    }
    void setLowShelf(float sr, float f, float gainDb) {
        if (std::fabs(gainDb) < 0.01f) { setIdentity(); return; }
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2 * kPi * f / sr, c = std::cos(w0);
        float alpha = std::sin(w0) / 2 * std::sqrt(2.0f), sA = 2 * std::sqrt(A) * alpha;
        set(A * ((A + 1) - (A - 1) * c + sA), 2 * A * ((A - 1) - (A + 1) * c), A * ((A + 1) - (A - 1) * c - sA),
            (A + 1) + (A - 1) * c + sA, -2 * ((A - 1) + (A + 1) * c), (A + 1) + (A - 1) * c - sA);
    }
    void setHighShelf(float sr, float f, float gainDb) {
        if (std::fabs(gainDb) < 0.01f) { setIdentity(); return; }
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2 * kPi * f / sr, c = std::cos(w0);
        float alpha = std::sin(w0) / 2 * std::sqrt(2.0f), sA = 2 * std::sqrt(A) * alpha;
        set(A * ((A + 1) + (A - 1) * c + sA), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sA),
            (A + 1) - (A - 1) * c + sA, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sA);
    }
    void setHighpass(float sr, float f, float q) {
        float w0 = 2 * kPi * f / sr, c = std::cos(w0), alpha = std::sin(w0) / (2 * q);
        set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
    }
};

// Reverb estéreo de tipo Freeverb (8 peines con amortiguación + 4 pasa-todo por canal).
class Reverb {
public:
    void init(float sr) {
        const int combT[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        const int apT[4] = {556, 441, 341, 225};
        const int spread = 23;
        float k = sr / 44100.0f;
        for (int ch = 0; ch < 2; ++ch) {
            for (int i = 0; i < 8; ++i) {
                comb_[ch][i].buf.assign(std::max(8, int((combT[i] + ch * spread) * k)), 0.0f);
                comb_[ch][i].idx = 0; comb_[ch][i].store = 0;
            }
            for (int i = 0; i < 4; ++i) {
                ap_[ch][i].buf.assign(std::max(8, int((apT[i] + ch * spread) * k)), 0.0f);
                ap_[ch][i].idx = 0;
            }
        }
        setParams(0.5f, 0.4f);
    }
    // size 0..1, damp 0..1
    void setParams(float size, float damp) {
        feedback_ = 0.70f + 0.28f * std::clamp(size, 0.0f, 1.0f);
        damp_ = 0.05f + 0.75f * std::clamp(damp, 0.0f, 1.0f);
    }
    // Procesa un bloque: devuelve solo la parte húmeda sumada en outL/outR.
    void process(const float* inL, const float* inR, float* wetL, float* wetR, int n) {
        for (int i = 0; i < n; ++i) {
            float in = (inL[i] + inR[i]) * 0.015f;
            float sums[2] = {0, 0};
            for (int ch = 0; ch < 2; ++ch) {
                float s = 0;
                for (int c = 0; c < 8; ++c) {
                    Comb& cb = comb_[ch][c];
                    float out = cb.buf[cb.idx];
                    cb.store = out * (1 - damp_) + cb.store * damp_;
                    cb.buf[cb.idx] = in + cb.store * feedback_;
                    if (++cb.idx >= (int)cb.buf.size()) cb.idx = 0;
                    s += out;
                }
                for (int a = 0; a < 4; ++a) {
                    AllPass& ap = ap_[ch][a];
                    float bo = ap.buf[ap.idx];
                    float o = -s + bo;
                    ap.buf[ap.idx] = s + bo * 0.5f;
                    if (++ap.idx >= (int)ap.buf.size()) ap.idx = 0;
                    s = o;
                }
                sums[ch] = s;
            }
            wetL[i] = sums[0] * 3.0f;
            wetR[i] = sums[1] * 3.0f;
        }
    }
private:
    struct Comb { std::vector<float> buf; int idx = 0; float store = 0; };
    struct AllPass { std::vector<float> buf; int idx = 0; };
    Comb comb_[2][8];
    AllPass ap_[2][4];
    float feedback_ = 0.84f, damp_ = 0.3f;
};

// Limitador de picos con ataque instantáneo y recuperación suave, más un recorte suave final.
class Limiter {
public:
    void init(float sr) { release_ = std::exp(-1.0f / (0.12f * sr)); gain_ = 1.0f; }
    inline void process(float& l, float& r) {
        float peak = std::max(std::fabs(l), std::fabs(r));
        float need = peak > ceiling_ ? ceiling_ / peak : 1.0f;
        if (need < gain_) gain_ = need; else gain_ = 1.0f - (1.0f - gain_) * release_;
        l = softClip(l * gain_);
        r = softClip(r * gain_);
    }
    float gainReduction() const { return gain_; }
private:
    static inline float softClip(float x) {
        const float t = 0.9f;
        float a = std::fabs(x);
        if (a <= t) return x;
        float s = x < 0 ? -1.0f : 1.0f;
        float over = (a - t) / (1.0f - t);          // 0..∞
        return s * (t + (1.0f - t) * std::tanh(over));
    }
    float release_ = 0.999f, gain_ = 1.0f, ceiling_ = 0.95f;
};

}  // namespace pc
