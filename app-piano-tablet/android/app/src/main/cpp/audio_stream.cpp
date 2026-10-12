#include "audio_stream.h"

#include <android/log.h>
#include <time.h>

#include <algorithm>

#define LOG_TAG "PianoEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace pc {

static void enableFlushToZero() {
#if defined(__aarch64__)
    uint64_t fpcr;
    asm volatile("mrs %0, fpcr" : "=r"(fpcr));
    fpcr |= (1ull << 24);  // FZ: los denormales se tratan como cero (evita picos de CPU en las colas)
    asm volatile("msr fpcr, %0" : : "r"(fpcr));
#endif
}

aaudio_data_callback_result_t AudioStream::dataCallback(AAudioStream*, void* user, void* data, int32_t frames) {
    static thread_local bool ftz = false;
    if (!ftz) { enableFlushToZero(); ftz = true; }
    auto* self = static_cast<AudioStream*>(user);
    self->engine_->render(static_cast<float*>(data), frames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void AudioStream::errorCallback(AAudioStream*, void* user, aaudio_result_t error) {
    auto* self = static_cast<AudioStream*>(user);
    LOGE("error de audio: %d", error);
    if (error == AAUDIO_ERROR_DISCONNECTED) self->needRestart_.store(true);
}

bool AudioStream::open(bool exclusive) {
    AAudioStreamBuilder* b = nullptr;
    if (AAudio_createStreamBuilder(&b) != AAUDIO_OK) return false;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(b, exclusive ? AAUDIO_SHARING_MODE_EXCLUSIVE : AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_FLOAT);
    AAudioStreamBuilder_setChannelCount(b, 2);
    AAudioStreamBuilder_setUsage(b, AAUDIO_USAGE_MEDIA);
    AAudioStreamBuilder_setContentType(b, AAUDIO_CONTENT_TYPE_MUSIC);
    AAudioStreamBuilder_setDataCallback(b, &AudioStream::dataCallback, this);
    AAudioStreamBuilder_setErrorCallback(b, &AudioStream::errorCallback, this);
    aaudio_result_t r = AAudioStreamBuilder_openStream(b, &stream_);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) {
        LOGE("openStream falló: %s", AAudio_convertResultToText(r));
        stream_ = nullptr;
        return false;
    }
    exclusive_ = exclusive;
    return true;
}

bool AudioStream::start() {
    stop();
    needRestart_.store(false);
    if (!open(true) && !open(false)) return false;
    sampleRate_ = AAudioStream_getSampleRate(stream_);
    burst_ = AAudioStream_getFramesPerBurst(stream_);
    AAudioStream_setBufferSizeInFrames(stream_, std::max(burst_ * 2, 128));
    engine_->setSampleRate(static_cast<float>(sampleRate_));
    latencyEma_ = 0;
    xruns_ = 0;
    aaudio_result_t r = AAudioStream_requestStart(stream_);
    if (r != AAUDIO_OK) {
        LOGE("requestStart falló: %s", AAudio_convertResultToText(r));
        stop();
        return false;
    }
    LOGI("audio: %d Hz, ráfaga %d frames, %s", sampleRate_, burst_, exclusive_ ? "exclusivo" : "compartido");
    return true;
}

void AudioStream::stop() {
    if (!stream_) return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
    stream_ = nullptr;
}

void AudioStream::updateLatency() {
    if (!stream_) return;
    // si hay cortes (xruns) se agranda el búfer una ráfaga, hasta su capacidad máxima
    int32_t xr = AAudioStream_getXRunCount(stream_);
    if (xr > xruns_) {
        xruns_ = xr;
        int32_t cur = AAudioStream_getBufferSizeInFrames(stream_);
        int32_t cap = AAudioStream_getBufferCapacityInFrames(stream_);
        int32_t want = std::min(cap, cur + std::max(burst_, 64));
        if (want > cur) AAudioStream_setBufferSizeInFrames(stream_, want);
        LOGI("xrun %d: búfer %d → %d frames", xr, cur, want);
    }
    int64_t framePos = 0, timeNs = 0;
    if (AAudioStream_getTimestamp(stream_, CLOCK_MONOTONIC, &framePos, &timeNs) != AAUDIO_OK) {
        float est = 1000.0f * AAudioStream_getBufferSizeInFrames(stream_) / std::max(1, sampleRate_);
        engine_->setLatencyMs(est);
        return;
    }
    timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    int64_t now = (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
    int64_t written = AAudioStream_getFramesWritten(stream_);
    double presentNs = (double)timeNs + (double)(written - framePos) * 1e9 / sampleRate_;
    float ms = (float)((presentNs - (double)now) / 1e6);
    ms = std::clamp(ms, 0.0f, 400.0f);
    latencyEma_ = latencyEma_ == 0 ? ms : latencyEma_ * 0.8f + ms * 0.2f;
    engine_->setLatencyMs(latencyEma_);
}

}  // namespace pc
