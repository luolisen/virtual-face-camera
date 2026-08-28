#include "camserver_shm.h"

#include <chrono>
#include <jni.h>

static CameraFramePacket* g_writer_packet = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_alanlaw_vfc_utils_CameraServerBridge_nativeInitShm(
        JNIEnv* /*env*/, jclass /*clazz*/) {
    if (!g_writer_packet) {
        g_writer_packet = CsShmManager::openWriter();
    }
    return g_writer_packet != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_alanlaw_vfc_utils_CameraServerBridge_nativePushFrame(
        JNIEnv* env, jclass /*clazz*/, jbyteArray frame_bytes,
        jint width, jint height, jint format, jint rotation,
        jfloat r, jfloat g, jfloat b, jfloat intensity) {
    if (!g_writer_packet) {
        g_writer_packet = CsShmManager::openWriter();
        if (!g_writer_packet) {
            return JNI_FALSE;
        }
    }

    if (!frame_bytes || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }
    jsize len = env->GetArrayLength(frame_bytes);
    if (len <= 0) {
        return JNI_FALSE;
    }

    int copy_size = len > CS_MAX_FRAME_SIZE ? CS_MAX_FRAME_SIZE : len;
    jbyte* bytes = env->GetByteArrayElements(frame_bytes, nullptr);
    if (!bytes) {
        return JNI_FALSE;
    }

    std::memcpy(g_writer_packet->data, bytes, copy_size);
    env->ReleaseByteArrayElements(frame_bytes, bytes, JNI_ABORT);

    g_writer_packet->width = width;
    g_writer_packet->height = height;
    g_writer_packet->format = format;
    g_writer_packet->rotation = rotation;
    g_writer_packet->ambient_r_color = r;
    g_writer_packet->ambient_g_color = g;
    g_writer_packet->ambient_b_color = b;
    g_writer_packet->ambient_intensity = intensity;
    g_writer_packet->data_size = copy_size;

    auto now = std::chrono::steady_clock::now().time_since_epoch();
    g_writer_packet->timestamp_ns = std::chrono::duration_cast<std::chrono::nanoseconds>(now).count();

    uint64_t next_seq = g_writer_packet->sequence.load(std::memory_order_relaxed) + 1;
    g_writer_packet->sequence.store(next_seq, std::memory_order_release);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_alanlaw_vfc_utils_CameraServerBridge_nativeCloseShm(
        JNIEnv* /*env*/, jclass /*clazz*/) {
    if (g_writer_packet) {
        CsShmManager::closeShm(g_writer_packet);
        g_writer_packet = nullptr;
    }
}
