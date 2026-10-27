#include <jni.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <stdint.h>
#include <stdatomic.h>
#include <math.h>
#include "sherpa-onnx/c-api/c-api.h"
#include "question_prosody.h"
static atomic_bool cancelled = ATOMIC_VAR_INIT(0);
static void fail(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (type) (*env)->ThrowNew(env, type, message);
}
// The tokenizer needs standard UTF-8, rather than JNI's modified UTF-8.
static char *utf8(JNIEnv *env, jstring value) {
    if (!value) return NULL;
    jclass cls = (*env)->FindClass(env, "java/lang/String");
    if (!cls) return NULL;
    jmethodID method = (*env)->GetMethodID(env, cls, "getBytes", "(Ljava/lang/String;)[B");
    jstring charset = (*env)->NewStringUTF(env, "UTF-8");
    if (!method || !charset) { (*env)->DeleteLocalRef(env, cls); return NULL; }
    jbyteArray bytes = (jbyteArray) (*env)->CallObjectMethod(env, value, method, charset);
    (*env)->DeleteLocalRef(env, charset);
    (*env)->DeleteLocalRef(env, cls);
    if (!bytes || (*env)->ExceptionCheck(env)) return NULL;
    jsize n = (*env)->GetArrayLength(env, bytes);
    char *result = malloc((size_t) n + 1);
    if (result) {
        (*env)->GetByteArrayRegion(env, bytes, 0, n, (jbyte *) result);
        result[n] = 0;
        if ((*env)->ExceptionCheck(env)) { free(result); result = NULL; }
    }
    (*env)->DeleteLocalRef(env, bytes);
    return result;
}

static int32_t progress(const float *samples, int32_t count, float fraction, void *arg) {
    (void)samples; (void)count; (void)fraction; (void)arg;
    return !atomic_load(&cancelled);
}
JNIEXPORT void JNICALL Java_com_ember_reader_FastSpeech_cancel(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; atomic_store(&cancelled, 1);
}
JNIEXPORT void JNICALL Java_com_ember_reader_FastSpeech_resetCancellation(JNIEnv *env, jclass cls) {
    (void)env; (void)cls; atomic_store(&cancelled, 0);
}
JNIEXPORT jlong JNICALL Java_com_ember_reader_FastSpeech_loadNative(JNIEnv *env, jclass cls, jstring path, jint threads) {
    (void)cls;
    char *dir = utf8(env, path);
    if (!dir) { if (!(*env)->ExceptionCheck(env)) fail(env, "Could not read natural voice path."); return 0; }
    char model[4096], voices[4096], tokens[4096], data[4096];
    if (strlen(dir) > 4000) { free(dir); fail(env, "Natural voice path is too long."); return 0; }
    snprintf(model, sizeof(model), "%s/model.int8.onnx", dir);
    snprintf(voices, sizeof(voices), "%s/voices.bin", dir);
    snprintf(tokens, sizeof(tokens), "%s/tokens.txt", dir);
    snprintf(data, sizeof(data), "%s/espeak-ng-data", dir);
    free(dir);
    SherpaOnnxOfflineTtsConfig config = {0};
    config.model.kokoro.model = model;
    config.model.kokoro.voices = voices;
    config.model.kokoro.tokens = tokens;
    config.model.kokoro.data_dir = data;
    config.model.kokoro.length_scale = 1.0f;
    config.model.num_threads = threads > 0 ? threads : 2;
    config.model.provider = "cpu";
    config.max_num_sentences = 1;
    config.silence_scale = 0.15f;
    const SherpaOnnxOfflineTts *tts = SherpaOnnxCreateOfflineTts(&config);
    if (!tts) { fail(env, "Could not initialize the natural-reading voice."); return 0; }
    if (SherpaOnnxOfflineTtsSampleRate(tts) != 24000 || SherpaOnnxOfflineTtsNumSpeakers(tts) != 11) {
        SherpaOnnxDestroyOfflineTts(tts); fail(env, "Unsupported natural-reading model."); return 0;
    }
    return (jlong)(intptr_t)tts;
}
JNIEXPORT jfloatArray JNICALL Java_com_ember_reader_FastSpeech_synthesizeNative(JNIEnv *env, jclass cls, jlong pointer, jstring text, jint speaker, jboolean rising_question) {
    (void)cls;
    const SherpaOnnxOfflineTts *tts = (const SherpaOnnxOfflineTts *)(intptr_t)pointer;
    if (!tts) { fail(env, "The natural voice is not loaded."); return NULL; }
    if (atomic_load(&cancelled)) return NULL;
    if (speaker < 0 || speaker >= SherpaOnnxOfflineTtsNumSpeakers(tts)) { fail(env, "Unknown natural voice."); return NULL; }
    char *input = utf8(env, text);
    if (!input) { if (!(*env)->ExceptionCheck(env)) fail(env, "Could not prepare natural speech."); return NULL; }
    SherpaOnnxGenerationConfig generation = {0};
    generation.sid = speaker;
    generation.speed = 1.0f;
    generation.silence_scale = 0.15f;
    const SherpaOnnxGeneratedAudio *audio = SherpaOnnxOfflineTtsGenerateWithConfig(tts, input, &generation, progress, NULL);
    free(input);
    jfloatArray result = NULL;
    if (!atomic_load(&cancelled)) {
        if (!audio || !audio->samples || audio->n <= 0 || audio->n > 24000 * 60 || audio->sample_rate != 24000) {
            fail(env, "Natural speech generation failed. Try a shorter sentence.");
        } else {
            int valid = 1;
            for (int i = 0; i < audio->n; i++) if (!isfinite(audio->samples[i])) { valid = 0; break; }
            if (!valid) fail(env, "The natural voice produced invalid audio.");
            else {
                float *adjusted = NULL;
                if (rising_question) {
                    adjusted = malloc((size_t)audio->n * sizeof(float));
                    if (!adjusted) { fail(env, "Not enough memory to prepare question intonation."); }
                    else {
                        memcpy(adjusted, audio->samples, (size_t)audio->n * sizeof(float));
                        if (ember_raise_question(adjusted, audio->n, audio->sample_rate, NULL) < 0)
                            fail(env, "Could not prepare question intonation.");
                    }
                }
                if (!(*env)->ExceptionCheck(env) && !atomic_load(&cancelled)) {
                    result = (*env)->NewFloatArray(env, audio->n);
                    if (result) (*env)->SetFloatArrayRegion(env, result, 0, audio->n, adjusted ? adjusted : audio->samples);
                }
                free(adjusted);
            }
        }
    }
    if (audio) SherpaOnnxDestroyOfflineTtsGeneratedAudio(audio);
    return result;
}
JNIEXPORT void JNICALL Java_com_ember_reader_FastSpeech_unloadNative(JNIEnv *env, jclass cls, jlong pointer) {
    (void)env; (void)cls;
    if (pointer) SherpaOnnxDestroyOfflineTts((const SherpaOnnxOfflineTts *)(intptr_t)pointer);
}
