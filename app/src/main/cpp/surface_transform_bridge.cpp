#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>

namespace {
constexpr char kLogTag[] = "VFC_SurfaceTransform";
constexpr int32_t kTransformMask =
        ANATIVEWINDOW_TRANSFORM_MIRROR_HORIZONTAL |
        ANATIVEWINDOW_TRANSFORM_MIRROR_VERTICAL |
        ANATIVEWINDOW_TRANSFORM_ROTATE_90;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_alanlaw_vfc_SurfaceTransformBridge_nativeApplyTransform(
        JNIEnv* env, jclass /*clazz*/, jobject surface, jint transform_flags) {
    if (env == nullptr || surface == nullptr) {
        return JNI_FALSE;
    }
    if ((transform_flags & ~kTransformMask) != 0) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                "reject invalid transform flags=0x%x", transform_flags);
        return JNI_FALSE;
    }

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                "ANativeWindow_fromSurface returned null");
        return JNI_FALSE;
    }

    int32_t result = ANativeWindow_setBuffersTransform(window, transform_flags);
    ANativeWindow_release(window);
    if (result != 0) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
                "ANativeWindow_setBuffersTransform flags=0x%x result=%d",
                transform_flags, result);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
