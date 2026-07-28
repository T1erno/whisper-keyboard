#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_t1erno_whisperkeyboard_nativeengine_WhisperNative_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    if (!model_path_str) return 0;
    const char *model_path = env->GetStringUTFChars(model_path_str, nullptr);
    if (!model_path) return 0;

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = true; // Enables Vulkan GPU / hardware acceleration

    struct whisper_context *ctx = whisper_init_from_file_with_params(model_path, cparams);
    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (!ctx) {
        LOGE("Failed to initialize whisper context from model path");
        return 0;
    }

    LOGI("Successfully initialized whisper context with GPU/Vulkan acceleration");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_t1erno_whisperkeyboard_nativeengine_WhisperNative_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    if (context_ptr == 0) return;
    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);
    whisper_free(ctx);
    LOGI("Freed whisper context");
}

JNIEXPORT jstring JNICALL
Java_com_t1erno_whisperkeyboard_nativeengine_WhisperNative_transcribeData(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jfloatArray samples_array, jstring language_str) {
    if (context_ptr == 0) {
        return env->NewStringUTF("");
    }

    auto *ctx = reinterpret_cast<struct whisper_context *>(context_ptr);

    jfloat *samples = env->GetFloatArrayElements(samples_array, nullptr);
    jsize n_samples = env->GetArrayLength(samples_array);
    const char *lang = language_str ? env->GetStringUTFChars(language_str, nullptr) : nullptr;

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = (lang && strlen(lang) > 0) ? lang : "auto";
    params.n_threads = num_threads > 0 ? num_threads : 3;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = true;
    params.max_tokens = 128; // Cap max tokens to prevent infinite loops on background noise
    params.temperature = 0.0f; // Single-pass greedy decoding

    whisper_reset_timings(ctx);

    std::string result_text;
    if (whisper_full(ctx, params, samples, n_samples) == 0) {
        int n_segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < n_segments; ++i) {
            const char *text = whisper_full_get_segment_text(ctx, i);
            if (text) {
                result_text += text;
            }
        }
    } else {
        LOGE("Failed to run whisper_full");
    }

    env->ReleaseFloatArrayElements(samples_array, samples, JNI_ABORT);
    if (lang) {
        env->ReleaseStringUTFChars(language_str, lang);
    }

    return env->NewStringUTF(result_text.c_str());
}

} // extern "C"
