// Puente JNI entre Kotlin (objeto NativeEngine) y el motor de audio.
#include <jni.h>

#include <memory>
#include <mutex>
#include <vector>

#include "audio_stream.h"
#include "engine.h"

namespace {
std::mutex gMutex;  // protege crear/destruir; las llamadas de control no lo necesitan
pc::Engine* gEngine = nullptr;
pc::AudioStream* gStream = nullptr;
}  // namespace

#define JNI(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_forxa_pianocascada_audio_NativeEngine_##name

JNI(jboolean, nCreate)(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> g(gMutex);
    if (!gEngine) {
        gEngine = new pc::Engine();
        gStream = new pc::AudioStream(gEngine);
    }
    return JNI_TRUE;
}

JNI(void, nDestroy)(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> g(gMutex);
    delete gStream; gStream = nullptr;
    delete gEngine; gEngine = nullptr;
}

JNI(jboolean, nLoadBank)(JNIEnv* env, jobject, jbyteArray bytes) {
    if (!gEngine) return JNI_FALSE;
    jsize n = env->GetArrayLength(bytes);
    std::vector<uint8_t> buf((size_t)n);
    env->GetByteArrayRegion(bytes, 0, n, reinterpret_cast<jbyte*>(buf.data()));
    return gEngine->loadBank(buf.data(), buf.size()) ? JNI_TRUE : JNI_FALSE;
}

JNI(jboolean, nStart)(JNIEnv*, jobject) { return gStream && gStream->start() ? JNI_TRUE : JNI_FALSE; }
JNI(void, nStop)(JNIEnv*, jobject) { if (gStream) gStream->stop(); }
JNI(jboolean, nNeedsRestart)(JNIEnv*, jobject) { return gStream && gStream->needsRestart() ? JNI_TRUE : JNI_FALSE; }
JNI(void, nUpdateLatency)(JNIEnv*, jobject) { if (gStream) gStream->updateLatency(); }
JNI(jint, nSampleRate)(JNIEnv*, jobject) { return gStream ? gStream->sampleRate() : 0; }

JNI(void, nNoteOn)(JNIEnv*, jobject, jint key, jint vel) { if (gEngine) gEngine->noteOn(key, vel); }
JNI(void, nNoteOff)(JNIEnv*, jobject, jint key) { if (gEngine) gEngine->noteOff(key); }
JNI(void, nPedal)(JNIEnv*, jobject, jboolean down) { if (gEngine) gEngine->setPedal(down == JNI_TRUE); }
JNI(void, nAllOff)(JNIEnv*, jobject) { if (gEngine) gEngine->allNotesOff(); }
JNI(void, nSetParam)(JNIEnv*, jobject, jint id, jfloat v) { if (gEngine) gEngine->setParam(id, v); }

JNI(void, nLoadSeq)(JNIEnv* env, jobject, jintArray keys, jintArray vels, jintArray hands, jdoubleArray starts,
                    jdoubleArray durs, jint n) {
    if (!gEngine || n <= 0) return;
    std::vector<int32_t> k((size_t)n), v((size_t)n), h((size_t)n);
    std::vector<double> s((size_t)n), d((size_t)n);
    env->GetIntArrayRegion(keys, 0, n, k.data());
    env->GetIntArrayRegion(vels, 0, n, v.data());
    env->GetIntArrayRegion(hands, 0, n, h.data());
    env->GetDoubleArrayRegion(starts, 0, n, s.data());
    env->GetDoubleArrayRegion(durs, 0, n, d.data());
    gEngine->loadSequence(k.data(), v.data(), h.data(), s.data(), d.data(), n);
}

JNI(void, nSeqPlay)(JNIEnv*, jobject, jboolean p) { if (gEngine) gEngine->seqPlay(p == JNI_TRUE); }
JNI(void, nSeqSeek)(JNIEnv*, jobject, jdouble ms) { if (gEngine) gEngine->seqSeek(ms); }
JNI(void, nSeqSpeed)(JNIEnv*, jobject, jfloat s) { if (gEngine) gEngine->seqSpeed(s); }
JNI(void, nSeqLoop)(JNIEnv*, jobject, jdouble a, jdouble b, jboolean on) { if (gEngine) gEngine->seqLoop(a, b, on == JNI_TRUE); }
JNI(void, nSeqGate)(JNIEnv*, jobject, jdouble ms) { if (gEngine) gEngine->seqGate(ms); }
JNI(void, nHandGain)(JNIEnv*, jobject, jint hand, jfloat g) { if (gEngine) gEngine->setHandGain(hand, g); }
JNI(void, nSeqStopNotes)(JNIEnv*, jobject) { if (gEngine) gEngine->seqStopNotes(); }

JNI(jdouble, nPositionMs)(JNIEnv*, jobject) { return gEngine ? gEngine->positionMs() : 0.0; }
JNI(jlong, nStampNs)(JNIEnv*, jobject) { return gEngine ? (jlong)gEngine->stampNs() : 0; }
JNI(jboolean, nPlaying)(JNIEnv*, jobject) { return gEngine && gEngine->isPlaying() ? JNI_TRUE : JNI_FALSE; }
JNI(jboolean, nWaiting)(JNIEnv*, jobject) { return gEngine && gEngine->isWaiting() ? JNI_TRUE : JNI_FALSE; }
JNI(jfloat, nLatencyMs)(JNIEnv*, jobject) { return gEngine ? gEngine->latencyMs() : 0.0f; }
JNI(jint, nActiveVoices)(JNIEnv*, jobject) { return gEngine ? gEngine->activeVoices() : 0; }
