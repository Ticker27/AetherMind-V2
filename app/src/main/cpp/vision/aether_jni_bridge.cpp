#include <jni.h>
#include <cstdint>
#include "AetherEngine.h"

static AetherEngine* g_engine = nullptr;

extern "C" {

JNIEXPORT void JNICALL
Java_com_aethermind_vision_AetherNativeBridge_nativeInit(JNIEnv* env, jobject thiz, jint width, jint height) {
    if (!g_engine) {
        g_engine = new AetherEngine(width, height);
    }
}

JNIEXPORT void JNICALL
Java_com_aethermind_vision_AetherNativeBridge_nativeProcessFrame(JNIEnv* env, jobject thiz, jobject byte_buffer, jint width, jint height) {
    if (!g_engine) return;
    uint8_t* pixels = static_cast<uint8_t*>(env->GetDirectBufferAddress(byte_buffer));
    if (pixels) {
        g_engine->processFrame(pixels, width, height);
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_aethermind_vision_AetherNativeBridge_nativeGetCommand(JNIEnv* env, jobject thiz) {
    if (!g_engine) return nullptr;

    AetherCommand cmd = g_engine->getLatestCommand();
    jfloatArray result = env->NewFloatArray(6);
    if (result) {
        float data[6] = {
            cmd.hasShot ? 1.0f : 0.0f,
            cmd.startX, cmd.startY,
            cmd.endX, cmd.endY,
            (float)cmd.durationMs
        };
        env->SetFloatArrayRegion(result, 0, 6, data);
        cmd.hasShot = false; // Reset หลังส่งคำสั่ง
    }
    return result;
}

} // extern "C"
