#include <jni.h>
#include <atomic>
#include <memory>
#include <string>
#include <vector>
#include "whisper.h"

static std::atomic<bool> cancelled{false};
extern "C" JNIEXPORT void JNICALL
Java_com_kuiro_youtubemarker_transcription_WhisperEngine_resetAbort(JNIEnv *, jobject) { cancelled.store(false); }
extern "C" JNIEXPORT void JNICALL
Java_com_kuiro_youtubemarker_transcription_WhisperEngine_cancel(JNIEnv *, jobject) { cancelled.store(true); }

extern "C" JNIEXPORT jstring JNICALL
Java_com_kuiro_youtubemarker_transcription_WhisperEngine_transcribe(JNIEnv *env, jobject, jstring path, jfloatArray pcm) {
    auto fail = [&](const char *message) -> jstring {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message); return nullptr;
    };
    const char *chars = env->GetStringUTFChars(path, nullptr);
    if (!chars) return nullptr;
    std::string model_path(chars); env->ReleaseStringUTFChars(path, chars);
    const int count = env->GetArrayLength(pcm);
    if (count < 160 || count > 16000 * 120) return fail("Invalid PCM length");
    std::vector<float> samples(count);
    env->GetFloatArrayRegion(pcm, 0, count, samples.data());
    if (env->ExceptionCheck()) return nullptr;
    auto options = whisper_context_default_params(); options.use_gpu = false;
    std::unique_ptr<whisper_context, decltype(&whisper_free)> context(
        whisper_init_from_file_with_params(model_path.c_str(), options), whisper_free);
    if (!context) return fail("Cannot load local Whisper model");
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = 4; params.language = "ko"; params.translate = false;
    params.no_timestamps = true; params.print_progress = false;
    params.print_realtime = false; params.print_timestamps = false;
    params.print_special = false; params.single_segment = false;
    params.abort_callback = [](void *) -> bool { return cancelled.load(); };
    params.abort_callback_user_data = nullptr;
    if (cancelled.load() || whisper_full(context.get(), params, samples.data(), count) != 0) return fail("Local transcription stopped or failed");
    std::string text;
    for (int i = 0; i < whisper_full_n_segments(context.get()); ++i) text += whisper_full_get_segment_text(context.get(), i);
    // Standard UTF-8, including supplementary characters, rather than JNI modified UTF-8.
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(text.size()));
    if (!bytes) return nullptr;
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    jclass string_class = env->FindClass("java/lang/String");
    auto ctor = env->GetMethodID(string_class, "<init>", "([BLjava/lang/String;)V");
    return static_cast<jstring>(env->NewObject(string_class, ctor, bytes, env->NewStringUTF("UTF-8")));
}
