#include "engine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>

namespace pc {

// ---------------------------------------------------------------- banco de muestras
bool SampleBank::load(const uint8_t* d, size_t size) {
    if (size < 12 || std::memcmp(d, "PNO1", 4) != 0) return false;
    uint32_t nz, total;
    std::memcpy(&nz, d + 4, 4);
    std::memcpy(&total, d + 8, 4);
    const size_t recSize = 40;
    if (nz == 0 || nz > 512) return false;
    size_t pcmOff = 12 + nz * recSize;
    if (size < pcmOff + (size_t)total * 4) return false;
    std::vector<Zone> zs(nz);
    for (uint32_t i = 0; i < nz; ++i) {
        const uint8_t* r = d + 12 + i * recSize;
        Zone& z = zs[i];
        z.keyLo = r[0]; z.keyHi = r[1]; z.velLo = r[2]; z.velHi = r[3]; z.root = r[4];
        std::memcpy(&z.fineCents, r + 6, 2);
        std::memcpy(&z.sampleRate, r + 8, 4);
        std::memcpy(&z.offset, r + 12, 4);
        std::memcpy(&z.frames, r + 16, 4);
        std::memcpy(&z.loopStart, r + 20, 4);
        std::memcpy(&z.loopEnd, r + 24, 4);
        std::memcpy(&z.decay100, r + 28, 4);
        std::memcpy(&z.gain, r + 32, 4);
        if ((uint64_t)z.offset + z.frames > total || z.frames < 8) return false;
        if (z.loopEnd > z.frames) z.loopEnd = z.frames;
    }
    std::vector<int16_t> p((size_t)total * 2);
    std::memcpy(p.data(), d + pcmOff, (size_t)total * 4);
    zones.swap(zs);
    pcm.swap(p);
    return true;
}

const Zone* SampleBank::find(int key, int vel) const {
    const Zone* best = nullptr;
    int bestDist = 1 << 20;
    for (const Zone& z : zones) {
        if (key >= z.keyLo && key <= z.keyHi && vel >= z.velLo && vel <= z.velHi) return &z;
        int dist = key < z.keyLo ? z.keyLo - key : (key > z.keyHi ? key - z.keyHi : 0);
        if (dist < bestDist) { bestDist = dist; best = &z; }
    }
    return best;
}

// ---------------------------------------------------------------- utilidades
static inline float hermite(float y0, float y1, float y2, float y3, float t) {
    float c1 = 0.5f * (y2 - y0);
    float c2 = y0 - 2.5f * y1 + 2.0f * y2 - 0.5f * y3;
    float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
    return ((c3 * t + c2) * t + c1) * t + y1;
}

static inline int64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

// ---------------------------------------------------------------- motor
Engine::Engine() {
    for (int i = 0; i < P_COUNT; ++i) params_[i] = 0;
    params_[P_MASTER] = 0.8f;
    params_[P_REVERB_MIX] = 0.18f;
    params_[P_REVERB_SIZE] = 0.5f;
    params_[P_REVERB_DAMP] = 0.4f;
    params_[P_TUNING_A4] = 440.0f;
    params_[P_DECAY_SCALE] = 1.0f;
    params_[P_BRIGHTNESS] = 1.0f;
    params_[P_RELEASE_SCALE] = 1.0f;
    params_[P_VEL_EXP] = 1.3f;
    setSampleRate(48000.0f);
}

Engine::~Engine() {
    delete seq_;
    delete retired_.exchange(nullptr);
}

bool Engine::loadBank(const uint8_t* data, size_t size) {
    SampleBank tmp;
    if (!tmp.load(data, size)) return false;
    bankReady_.store(false);
    bank_.zones.swap(tmp.zones);
    bank_.pcm.swap(tmp.pcm);
    for (auto& v : voices_) v.active = false;
    bankReady_.store(true);
    return true;
}

void Engine::setSampleRate(float sr) {
    sr_ = sr;
    reverb_.init(sr);
    limiter_.init(sr);
    eqDirty_ = revDirty_ = true;
    for (auto& v : voices_) v.active = false;
}

// -------- cola de comandos
void Engine::push(const Command& c) {
    while (prodLock_.test_and_set(std::memory_order_acquire)) {}
    uint32_t h = head_.load(std::memory_order_relaxed);
    uint32_t t = tail_.load(std::memory_order_acquire);
    if (h - t < (uint32_t)kQueue) {
        queue_[h % kQueue] = c;
        head_.store(h + 1, std::memory_order_release);
    }
    prodLock_.clear(std::memory_order_release);
}

void Engine::drainCommands() {
    uint32_t t = tail_.load(std::memory_order_relaxed);
    uint32_t h = head_.load(std::memory_order_acquire);
    while (t != h) {
        execute(queue_[t % kQueue]);
        ++t;
    }
    tail_.store(t, std::memory_order_release);
}

void Engine::execute(const Command& c) {
    switch (c.type) {
        case C_NOTE_ON: startNote(c.a, c.b, 0); break;
        case C_NOTE_OFF:
            for (auto& v : voices_)
                if (v.active && v.key == c.a && v.tag == 0 && !v.releasing) {
                    if (pedal_) v.sustained = true; else startRelease(v, false);
                }
            break;
        case C_PEDAL:
            pedal_ = c.a != 0;
            if (!pedal_)
                for (auto& v : voices_)
                    if (v.active && v.sustained && !v.releasing) startRelease(v, false);
            break;
        case C_ALL_OFF:
            for (auto& v : voices_)
                if (v.active && !v.releasing) startRelease(v, true);
            break;
        case C_PARAM: applyParam(c.a, c.f); break;
        case C_SEQ_SET: {
            Sequence* old = seq_;
            seq_ = static_cast<Sequence*>(c.p);
            if (old) {
                Sequence* prev = retired_.exchange(old);
                (void)prev;  // un segundo reemplazo antes de liberar es rarísimo; se pierde, no se cuelga
            }
            playing_ = false;
            seekInternal(0);
            break;
        }
        case C_SEQ_PLAY: playing_ = c.a != 0 && seq_ != nullptr; break;
        case C_SEQ_SEEK: seekInternal(c.d); break;
        case C_SEQ_SPEED: speed_ = std::clamp(c.f, 0.1f, 4.0f); break;
        case C_SEQ_LOOP:
            loopStart_ = c.d; loopEnd_ = c.d2; loopOn_ = c.a != 0 && c.d2 > c.d + 50.0;
            break;
        case C_SEQ_GATE: gate_ = c.d; break;
        case C_HAND_GAIN: handGain_[c.a & 1] = c.f; break;
        case C_SEQ_NOTES_OFF:
            for (auto& v : voices_)
                if (v.active && v.tag == 1 && !v.releasing) startRelease(v, false);
            break;
    }
}

void Engine::applyParam(int id, float v) {
    if (id < 0 || id >= P_COUNT) return;
    params_[id] = v;
    if (id >= P_EQ0 && id <= P_EQ4) eqDirty_ = true;
    if (id == P_SPEAKER) eqDirty_ = true;
    if (id == P_REVERB_SIZE || id == P_REVERB_DAMP) revDirty_ = true;
}

void Engine::updateFilters() {
    if (eqDirty_) {
        for (int ch = 0; ch < 2; ++ch) {
            eq_[ch][0].setLowShelf(sr_, 80.0f, params_[P_EQ0]);
            eq_[ch][1].setPeaking(sr_, 250.0f, 0.9f, params_[P_EQ1]);
            eq_[ch][2].setPeaking(sr_, 1000.0f, 0.9f, params_[P_EQ2]);
            eq_[ch][3].setPeaking(sr_, 4000.0f, 0.9f, params_[P_EQ3]);
            eq_[ch][4].setHighShelf(sr_, std::min(10000.0f, sr_ * 0.4f), params_[P_EQ4]);
            bool spk = params_[P_SPEAKER] > 0.5f;
            hpf_[ch].setHighpass(sr_, spk ? 85.0f : 28.0f, 0.707f);
            if (spk) presence_[ch].setPeaking(sr_, 2800.0f, 0.8f, 2.5f); else presence_[ch].setIdentity();
        }
        eqDirty_ = false;
    }
    if (revDirty_) {
        reverb_.setParams(params_[P_REVERB_SIZE], params_[P_REVERB_DAMP]);
        revDirty_ = false;
    }
}

// -------- voces
Engine::Voice* Engine::allocVoice() {
    Voice* best = nullptr;
    for (auto& v : voices_)
        if (!v.active) return &v;
    // robo de voz: primero las que ya se están apagando (la más baja), después la más antigua
    float bestAmp = 1e9f;
    for (auto& v : voices_)
        if (v.releasing && v.amp < bestAmp) { bestAmp = v.amp; best = &v; }
    if (best) return best;
    uint64_t oldest = ~0ull;
    for (auto& v : voices_)
        if (v.age < oldest) { oldest = v.age; best = &v; }
    return best;
}

void Engine::startNote(int key, int vel, int tag) {
    if (!bankReady_.load(std::memory_order_acquire)) return;
    if (key < 0 || key > 127 || vel <= 0) return;
    vel = std::min(vel, 127);
    const Zone* z = bank_.find(key, vel);
    if (!z) return;
    for (auto& v : voices_)  // misma tecla: la anterior se apaga rápido
        if (v.active && v.key == key && v.tag == tag && !v.releasing) startRelease(v, true);
    Voice* v = allocVoice();
    if (!v) return;
    double tuneCents = 1200.0 * std::log2(std::max(400.0f, params_[P_TUNING_A4]) / 440.0);
    double semis = (key - z->root) + (z->fineCents + tuneCents) / 100.0;
    v->active = true; v->releasing = false; v->sustained = false;
    v->key = key; v->tag = tag; v->z = z; v->pos = 0;
    v->inc = std::pow(2.0, semis / 12.0) * z->sampleRate / sr_;
    float vn = vel / 127.0f;
    v->vgain = z->gain * 0.9f * std::pow(vn, std::max(0.5f, params_[P_VEL_EXP]));
    v->amp = 1.0f;
    float tau = std::max(0.5f, z->decay100 * std::max(0.2f, params_[P_DECAY_SCALE]));
    v->decayMul = std::pow(10.0f, -5.0f / (tau * sr_));
    v->relMul = 1.0f;
    float fc = (1800.0f + (19000.0f - 1800.0f) * std::pow(vn, 1.2f)) * std::max(0.2f, params_[P_BRIGHTNESS]);
    fc = std::min(fc, sr_ * 0.45f);
    v->lpA = 1.0f - std::exp(-2.0f * kPi * fc / sr_);
    v->lpL = v->lpR = 0;
    v->age = ++ageCounter_;
}

void Engine::startRelease(Voice& v, bool fast) {
    v.releasing = true; v.sustained = false;
    float t;
    if (fast) {
        t = 0.03f;
    } else {
        float k = std::clamp((v.key - 21) / 87.0f, 0.0f, 1.0f);
        t = (1.1f + (0.28f - 1.1f) * k) * std::max(0.2f, params_[P_RELEASE_SCALE]);
    }
    v.relMul = std::pow(10.0f, -5.0f / (t * sr_));
}

void Engine::renderVoice(Voice& v, float* L, float* R, int n) {
    const Zone& z = *v.z;
    const int16_t* base = bank_.pcm.data() + (size_t)z.offset * 2;
    const bool loop = z.loopEnd > z.loopStart + 16;
    const int64_t loopLen = (int64_t)z.loopEnd - (int64_t)z.loopStart;
    const int64_t last = (int64_t)z.frames - 1;
    const float k = 1.0f / 32768.0f;
    for (int i = 0; i < n; ++i) {
        if (loop) {
            while (v.pos >= (double)z.loopEnd) v.pos -= (double)loopLen;
        } else if (v.pos >= (double)(last - 1)) {
            v.active = false;
            return;
        }
        int64_t i1 = (int64_t)v.pos;
        float t = (float)(v.pos - (double)i1);
        int64_t i0 = i1 - 1, i2 = i1 + 1, i3 = i1 + 2;
        if (loop) {
            if (i2 >= z.loopEnd) i2 -= loopLen;
            if (i3 >= z.loopEnd) i3 -= loopLen;
        } else {
            if (i2 > last) i2 = last;
            if (i3 > last) i3 = last;
        }
        if (i0 < 0) i0 = 0;
        float xl = hermite(base[i0 * 2], base[i1 * 2], base[i2 * 2], base[i3 * 2], t) * k;
        float xr = hermite(base[i0 * 2 + 1], base[i1 * 2 + 1], base[i2 * 2 + 1], base[i3 * 2 + 1], t) * k;
        v.lpL += v.lpA * (xl - v.lpL);
        v.lpR += v.lpA * (xr - v.lpR);
        float g = v.amp * v.vgain;
        L[i] += v.lpL * g;
        R[i] += v.lpR * g;
        v.amp *= v.releasing ? v.relMul : v.decayMul;
        v.pos += v.inc;
        if (v.amp < 1e-5f) { v.active = false; return; }
    }
}

void Engine::renderChunk(float* out, int n) {
    updateFilters();
    std::fill(dryL_, dryL_ + n, 0.0f);
    std::fill(dryR_, dryR_ + n, 0.0f);
    int active = 0;
    for (auto& v : voices_)
        if (v.active) { renderVoice(v, dryL_, dryR_, n); ++active; }
    activeOut_.store(active, std::memory_order_relaxed);

    for (int i = 0; i < n; ++i) {
        float l = dryL_[i], r = dryR_[i];
        for (int b = 0; b < 5; ++b) { l = eq_[0][b].process(l); r = eq_[1][b].process(r); }
        l = hpf_[0].process(l); r = hpf_[1].process(r);
        l = presence_[0].process(l); r = presence_[1].process(r);
        dryL_[i] = l; dryR_[i] = r;
    }
    float mix = std::clamp(params_[P_REVERB_MIX], 0.0f, 1.0f);
    if (mix > 0.001f) {
        reverb_.process(dryL_, dryR_, wetL_, wetR_, n);
    } else {
        std::fill(wetL_, wetL_ + n, 0.0f);
        std::fill(wetR_, wetR_ + n, 0.0f);
    }
    float target = std::clamp(params_[P_MASTER], 0.0f, 1.0f);
    target = target * target * 0.9f;  // curva de volumen más natural
    float step = (target - masterCur_) / (float)n;
    for (int i = 0; i < n; ++i) {
        masterCur_ += step;
        float l = (dryL_[i] + wetL_[i] * mix) * masterCur_;
        float r = (dryR_[i] + wetR_[i] * mix) * masterCur_;
        limiter_.process(l, r);
        out[2 * i] = l;
        out[2 * i + 1] = r;
    }
}

// -------- secuenciador
void Engine::seekInternal(double ms) {
    pos_ = std::max(0.0, ms);
    if (seq_) {
        auto it = std::lower_bound(seq_->ev.begin(), seq_->ev.end(), pos_ - 1e-6,
                                   [](const SeqEvent& e, double t) { return e.timeMs < t; });
        idx_ = (size_t)(it - seq_->ev.begin());
    } else {
        idx_ = 0;
    }
    for (auto& v : voices_)
        if (v.active && v.tag == 1 && !v.releasing) startRelease(v, true);
}

void Engine::dispatchDue() {
    if (!seq_) return;
    while (idx_ < seq_->ev.size() && seq_->ev[idx_].timeMs <= pos_ + 1e-6) {
        const SeqEvent& e = seq_->ev[idx_++];
        float g = handGain_[e.hand & 1];
        if (g <= 0.0f) continue;
        if (e.on) {
            int vel = std::clamp((int)std::lround(e.vel * g), 1, 127);
            startNote(e.key, vel, 1);
        } else {
            for (auto& v : voices_)
                if (v.active && v.key == e.key && v.tag == 1 && !v.releasing) startRelease(v, false);
        }
    }
}

void Engine::render(float* out, int frames) {
    drainCommands();
    if (!bankReady_.load(std::memory_order_acquire)) {
        std::fill(out, out + frames * 2, 0.0f);
        return;
    }
    int done = 0;
    while (done < frames) {
        int chunk = std::min(frames - done, kBlock);
        const double msPerFrame = 1000.0 * speed_ / sr_;
        if (playing_ && seq_) {
            if (pos_ < gate_ - 1e-9) dispatchDue();
            double target = 1e18;
            if (idx_ < seq_->ev.size()) target = seq_->ev[idx_].timeMs;
            if (loopOn_ && loopEnd_ < target) target = loopEnd_;
            if (gate_ < target) target = gate_;
            if (target < 1e17 && pos_ < gate_ - 1e-9) {
                double fr = std::ceil((target - pos_) / msPerFrame);
                if (fr < 1) fr = 1;
                if (fr < chunk) chunk = (int)fr;
            }
        }
        renderChunk(out + done * 2, chunk);
        if (playing_ && seq_) {
            if (pos_ >= gate_ - 1e-9) {
                pos_ = gate_;
                waiting_ = true;
            } else {
                waiting_ = false;
                pos_ += chunk * msPerFrame;
                if (pos_ >= gate_ - 1e-9) { pos_ = gate_; waiting_ = true; }
                if (loopOn_ && pos_ >= loopEnd_ - 1e-9) seekInternal(loopStart_);
            }
        } else {
            waiting_ = false;
        }
        done += chunk;
    }
    posOut_.store(pos_, std::memory_order_relaxed);
    stampNs_.store(nowNs(), std::memory_order_relaxed);
    playingOut_.store(playing_, std::memory_order_relaxed);
    waitingOut_.store(waiting_, std::memory_order_relaxed);
}

// ---------------------------------------------------------------- API de control
void Engine::noteOn(int key, int vel) { Command c; c.type = C_NOTE_ON; c.a = key; c.b = vel; push(c); }
void Engine::noteOff(int key) { Command c; c.type = C_NOTE_OFF; c.a = key; push(c); }
void Engine::setPedal(bool down) { Command c; c.type = C_PEDAL; c.a = down ? 1 : 0; push(c); }
void Engine::allNotesOff() { Command c; c.type = C_ALL_OFF; push(c); }
void Engine::setParam(int id, float v) { Command c; c.type = C_PARAM; c.a = id; c.f = v; push(c); }
void Engine::seqPlay(bool play) { Command c; c.type = C_SEQ_PLAY; c.a = play ? 1 : 0; push(c); }
void Engine::seqSeek(double ms) { Command c; c.type = C_SEQ_SEEK; c.d = ms; push(c); }
void Engine::seqSpeed(float s) { Command c; c.type = C_SEQ_SPEED; c.f = s; push(c); }
void Engine::seqLoop(double a, double b, bool on) { Command c; c.type = C_SEQ_LOOP; c.d = a; c.d2 = b; c.a = on ? 1 : 0; push(c); }
void Engine::seqGate(double ms) { Command c; c.type = C_SEQ_GATE; c.d = ms; push(c); }
void Engine::setHandGain(int hand, float g) { Command c; c.type = C_HAND_GAIN; c.a = hand; c.f = g; push(c); }
void Engine::seqStopNotes() { Command c; c.type = C_SEQ_NOTES_OFF; push(c); }

void Engine::loadSequence(const int32_t* key, const int32_t* vel, const int32_t* hand, const double* startMs,
                          const double* durMs, int n) {
    delete retired_.exchange(nullptr);
    auto* s = new Sequence();
    s->ev.reserve((size_t)n * 2);
    for (int i = 0; i < n; ++i) {
        uint8_t h = (uint8_t)(hand[i] & 1), k = (uint8_t)key[i], v = (uint8_t)std::clamp(vel[i], 1, 127);
        s->ev.push_back({startMs[i], 1, k, v, h});
        s->ev.push_back({startMs[i] + std::max(30.0, durMs[i]), 0, k, 0, h});
    }
    std::stable_sort(s->ev.begin(), s->ev.end(), [](const SeqEvent& a, const SeqEvent& b) {
        if (a.timeMs != b.timeMs) return a.timeMs < b.timeMs;
        return a.on < b.on;  // off antes que on
    });
    Command c; c.type = C_SEQ_SET; c.p = s;
    push(c);
}

}  // namespace pc
