#ifndef EMBER_QUESTION_PROSODY_H
#define EMBER_QUESTION_PROSODY_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef _WIN32
#define EMBER_DSP_API __declspec(dllexport)
#else
#define EMBER_DSP_API
#endif
/* 1 = corrected, 0 = already rising/no reliable voice, -1 = processing failure.
 * Optional six-float diagnostics: rise start/end seconds, existing rise,
 * requested endpoint correction (semitones), processed samples, voiced frames. */
EMBER_DSP_API int ember_raise_question(float *audio, int count, int rate, float *diagnostics);
#ifdef __cplusplus
}
#endif
#endif
