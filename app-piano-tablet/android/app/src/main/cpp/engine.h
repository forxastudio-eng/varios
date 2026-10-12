// Motor de piano: sampler multi-zona con bucles, pedal, ecualizador, reverb, limitador y
// secuenciador con reloj de audio. No depende de Android (se prueba en el PC).
#pragma once
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <vector>

#include "dsp.h"

namespace pc {

struct Zone {
    uint8_t keyLo, keyHi, velLo, velHi, root;
    int16_t fineCents;
    uint32_t sampleRate, offset, frames, loopStart, loopEnd;
    float decay100;  // segundos que tarda en caer 100 dB
    float gain;
};

struct SampleBank {
    std::vector<Zone> zones;
    std::vector<int16_t> pcm;  // estéreo intercalado
    bool load(const uint8_t* data, size_t size);
    const Zone* find(int key, int vel) const;
};

enum Param : int32_t {
    P_MASTER = 0,        // 0..1
    P_EQ0 = 1,           // 60 Hz  (shelf)    dB
    P_EQ1 = 2,           // 250 Hz (peaking)  dB
    P_EQ2 = 3,           // 1 kHz  (peaking)  dB
    P_EQ3 = 4,           // 4 kHz  (peaking)  dB
    P_EQ4 = 5,           // 12 kHz (shelf)    dB
    P_REVERB_MIX = 6,    // 0..1
    P_REVERB_SIZE = 7,   // 0..1
    P_REVERB_DAMP = 8,   // 0..1
    P_TUNING_A4 = 9,     // Hz (415..466)
    P_DECAY_SCALE = 10,  // 0.3..2
    P_BRIGHTNESS = 11,   // 0.3..2 (escala del filtro por velocidad)
    P_RELEASE_SCALE = 12,
    P_SPEAKER = 13,      // 0/1 perfil altavoces de tablet
    P_VEL_EXP = 14,      // exponente de la curva de velocidad
    P_COUNT
};

struct SeqEvent {
    double timeMs;
    uint8_t on, key, vel, hand;  // hand: 0 = derecha, 1 = izquierda
};

struct Sequence {
    std::vector<SeqEvent> ev;  // ordenados por tiempo (off antes que on en empate)
};

class Engine {
public:
    static constexpr int kMaxVoices = 96;
    static constexpr int kBlock = 256;

    Engine();
    ~Engine();

    bool loadBank(const uint8_t* data, size_t size);
    void setSampleRate(float sr);
    float sampleRate() const { return sr_; }

    // Hilo de audio.
    void render(float* out, int frames);

    // Control (cualquier hilo).
    void noteOn(int key, int vel);
    void noteOff(int key);
    void setPedal(bool down);
    void allNotesOff();
    void setParam(int id, float value);

    void loadSequence(const int32_t* key, const int32_t* vel, const int32_t* hand, const double* startMs,
                      const double* durMs, int n);
    void seqPlay(bool play);
    void seqSeek(double ms);
    void seqSpeed(float speed);
    void seqLoop(double startMs, double endMs, bool enabled);
    void seqGate(double ms);  // la reproducción no pasará de este punto
    void setHandGain(int hand, float gain);
    void seqStopNotes();

    double positionMs() const { return posOut_.load(std::memory_order_relaxed); }
    int64_t stampNs() const { return stampNs_.load(std::memory_order_relaxed); }
    bool isPlaying() const { return playingOut_.load(std::memory_order_relaxed); }
    bool isWaiting() const { return waitingOut_.load(std::memory_order_relaxed); }
    int activeVoices() const { return activeOut_.load(std::memory_order_relaxed); }
    void setLatencyMs(float ms) { latencyMs_.store(ms); }
    float latencyMs() const { return latencyMs_.load(); }
    bool ready() const { return bankReady_.load(); }

private:
    enum CmdType : int32_t {
        C_NOTE_ON, C_NOTE_OFF, C_PEDAL, C_ALL_OFF, C_PARAM, C_SEQ_SET, C_SEQ_PLAY, C_SEQ_SEEK, C_SEQ_SPEED,
        C_SEQ_LOOP, C_SEQ_GATE, C_HAND_GAIN, C_SEQ_NOTES_OFF
    };
    struct Command {
        int32_t type = 0, a = 0, b = 0;
        float f = 0;
        double d = 0, d2 = 0;
        void* p = nullptr;
    };
    struct Voice {
        bool active = false, releasing = false, sustained = false;
        int key = -1, tag = 0;  // tag 0 = usuario, 1 = secuenciador
        const Zone* z = nullptr;
        double pos = 0, inc = 1;
        float amp = 0, decayMul = 1, relMul = 1, vgain = 1;
        float lpA = 1, lpL = 0, lpR = 0;
        uint64_t age = 0;
    };

    void push(const Command& c);
    void drainCommands();
    void execute(const Command& c);
    void applyParam(int id, float v);
    void updateFilters();

    void startNote(int key, int vel, int tag);
    void releaseKey(int key, int tag, bool fast);
    void startRelease(Voice& v, bool fast);
    Voice* allocVoice();
    void renderVoice(Voice& v, float* L, float* R, int n);
    void renderChunk(float* out, int n);

    void dispatchDue();
    void seekInternal(double ms);

    // Estado de audio
    SampleBank bank_;
    std::atomic<bool> bankReady_{false};
    float sr_ = 48000.0f;
    Voice voices_[kMaxVoices];
    uint64_t ageCounter_ = 0;
    bool pedal_ = false;

    float params_[P_COUNT];
    bool eqDirty_ = true, revDirty_ = true;
    float masterCur_ = 0.5f;
    pc::Biquad eq_[2][5], hpf_[2], presence_[2];
    pc::Reverb reverb_;
    pc::Limiter limiter_;
    float dryL_[kBlock], dryR_[kBlock], wetL_[kBlock], wetR_[kBlock];

    // Secuenciador
    Sequence* seq_ = nullptr;
    std::atomic<Sequence*> retired_{nullptr};
    size_t idx_ = 0;
    bool playing_ = false;
    double pos_ = 0, gate_ = 1e18, loopStart_ = 0, loopEnd_ = 0;
    bool loopOn_ = false;
    float speed_ = 1.0f;
    float handGain_[2] = {1.0f, 1.0f};
    bool waiting_ = false;

    // Salidas leídas por otros hilos
    std::atomic<double> posOut_{0};
    std::atomic<int64_t> stampNs_{0};
    std::atomic<bool> playingOut_{false}, waitingOut_{false};
    std::atomic<int> activeOut_{0};
    std::atomic<float> latencyMs_{0};

    // Cola de comandos (varios productores con spinlock, un consumidor sin bloqueos)
    static constexpr int kQueue = 4096;
    Command queue_[kQueue];
    std::atomic<uint32_t> head_{0}, tail_{0};
    std::atomic_flag prodLock_ = ATOMIC_FLAG_INIT;
};

}  // namespace pc
