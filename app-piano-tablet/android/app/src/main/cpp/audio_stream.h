// Salida de audio de baja latencia con AAudio (Android 8+), alimentada por el motor.
#pragma once
#include <aaudio/AAudio.h>

#include <atomic>

#include "engine.h"

namespace pc {

class AudioStream {
public:
    explicit AudioStream(Engine* engine) : engine_(engine) {}
    ~AudioStream() { stop(); }

    bool start();
    void stop();
    bool needsRestart() const { return needRestart_.load(); }
    void updateLatency();  // llamar cada ~250 ms desde un hilo normal
    int sampleRate() const { return sampleRate_; }
    int framesPerBurst() const { return burst_; }
    bool exclusive() const { return exclusive_; }

private:
    static aaudio_data_callback_result_t dataCallback(AAudioStream*, void* user, void* data, int32_t frames);
    static void errorCallback(AAudioStream*, void* user, aaudio_result_t error);
    bool open(bool exclusive);

    Engine* engine_;
    AAudioStream* stream_ = nullptr;
    std::atomic<bool> needRestart_{false};
    int sampleRate_ = 48000, burst_ = 0;
    bool exclusive_ = false;
    float latencyEma_ = 0;
    int32_t xruns_ = 0;
};

}  // namespace pc
