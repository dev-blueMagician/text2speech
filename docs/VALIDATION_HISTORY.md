# Development validation history

Archived implementation notes and release comparisons. For current verification status and acceptance checks, see [VALIDATION.md](../VALIDATION.md). This record preserves historical evidence; it is not the application introduction.


## Current version 0.3.2 - correction across the question ending

The user could not hear a difference and supplied `Do you like mountains?` as the exact input. It passes the existing question detector. The previous test examined only a tiny terminal region; for ready/now the whole ending still fell. Recognition is unchanged in this release, and no model/voice change or new option is introduced.

- Native DSP now tracks and median-smooths the pitch contour over up to 0.4 seconds, interpolates through unvoiced gaps, and compensates the source fall toward a gradual rising target. The target spans 4–5.5 semitones depending on starting pitch, with -3/+12 semitone local bounds and a gradual entry. Earlier audio and sample count remain unchanged. A clear existing rise still bypasses processing. These settings are specific to the fixed female voice.
- The exact C++ source compiled as a Windows DLL was exercised on four newly generated clips from the unchanged model. Independent Praat analysis measures the first/last 80 ms of the longer corrected interval, requiring at least 0.3 seconds between endpoints. Results (0.3.1 -> 0.3.2 on the same source/span): ready **-4.73 -> +4.51**, tea **+3.76 -> +5.36**, now **-4.91 -> +3.65**, mountains **+3.83 -> +4.89** semitones. The previous mountains correction already had a numerical rise; that did not satisfy the user's listening test. A measured rise alone is not acceptance of this release either.
- Analysis uses Praat autocorrelation with octave cost 0.08 for this female voice. Default-cost results are also recorded: tea tracks a subharmonic, producing an apparent fall, while the other three agree. This ambiguity is an explicit limitation, not evidence of better listening quality. No claim of pitch-perfect or artifact-free output is made.
- Four real clips retain exact prefixes, equal duration, and finite bounded output. Silence/noise remain unchanged; a synthetic already-rising harmonic clip is unchanged and a known falling clip becomes rising. Current desktop DSP takes roughly 7–11 ms, excluding model inference. These are Windows measurements, not S10+ timings.
- `dist/question-comparison.wav` is the user's exact sentence processed by 0.3.1, then 0.3.2, with 0.6 seconds of silence. `question-before.wav` now means **0.3.1 output**, not raw model audio. `question-after.wav` means 0.3.2. JSON: `question-prosody-validation.json`.
- The header displays the installed version from Android package metadata, helping rule out testing an older APK. A regression test covers recognition/chunking of the reported sentence. Same voice installation and cache limits; upgrade in place without a new model pack.

Android build, **20 JVM tests**, and Android lint pass. Final APK reports versionCode 7/versionName 0.3.2, declares no permissions, and passes signing verification and ZIP/ELF 16 KB alignment checks. It contains exactly the three expected ARM64 libraries and the unchanged voice asset. The signing certificate matches previous versions for in-place upgrades. Current GPL/source directions are present in APK assets. No phone is attached; Android execution and listening acceptance remain pending. Listen to the comparison before accepting the change; do not substitute pitch statistics for the user's assessment.

Reproduce using `scripts/make_question_audio.py` and `scripts/check_question_audio.py` with `scripts/prosody-qa-requirements.txt`. In addition to the current DLL, compile `question_prosody.cpp` from the accompanying 0.3.1 source ZIP as `.research/prosody/ember_question-0.3.1.dll` using the same pinned include paths/compiler command documented below. The comparison processes the same newly generated PCM through both versions, avoiding model sampling differences.

## Previous version 0.3.1 - automatic yes/no question endings

The user reported that the compact standard voice still sounds robotic and specifically requested a rising ending on yes/no questions. This release adds explicit local prosody without restoring the large emotional model or introducing controls.

- English auxiliary-led and tag questions ending in `?` are recognized with conservative grammar/punctuation rules. Plain statements, wh-questions, and explicit alternative questions retain their original delivery. These are heuristics, not complete syntactic or emotion analysis; ambiguous/rhetorical/indirect constructions may not be recognized correctly.
- Recognized questions are isolated in paragraphs. A long question keeps its original words and only its last bounded fragment gets the rise. Cache keys include the prosody flag, so an identical final fragment with different context cannot reuse the wrong PCM.
- The native postprocessor estimates voicing/pitch in the final phrase, then raises only the terminal voiced syllable, aiming for about 3.2 semitones of rise. Added correction is capped at seven semitones. Already clear rises and unreliable/unvoiced clips are unchanged. Earlier audio is bit-exact and duration is unchanged. Formant compensation is enabled; perceived naturalness still needs listening evaluation.
- The exact C++ DSP source was compiled into a Windows DLL and tested on three real clips from the unchanged Kokoro model. Independent Praat pitch analysis measured terminal rises of **2.67 semitones** (`Are you ready?`), **3.17** (`Do you want some tea?`), and **2.83** (`Can we go now?`). The tea clip changed from a measured **3.47-semitone fall** to a rise. Processing took about **5-8 ms on Windows**, excluding speech synthesis. These are desktop DSP results, not S10+ timings.
- All three retained sample count/duration, finite bounded output, and exact prefixes. Silence/noise were unchanged, and a synthetic already-rising voice was not exaggerated. Results: `dist/question-prosody-validation.json`. `dist/question-comparison.wav` plays the original then corrected `Are you ready?` with 0.4 seconds of silence between them.
- Android build, **19 JVM tests**, and lint pass. The six added tests cover polar/tag/negative/quoted questions, wh/alternative/statement exclusions, paragraph isolation, long-question endings, and cache distinction. No phone inference, JNI playback, or listening-quality acceptance is claimed.
- Same model, installation manifest, single voice, and cache bounds as 0.3.0. No model reimport is needed on upgrade. The local DSP adds MIT Signalsmith Stretch and Signalsmith Linear headers, pinned by revision and SHA-256 in `prepare_prosody.py`. Complete upstream source archives and notices accompany the release.

To reproduce the numerical audio check, use the matching Windows sherpa shared release in `.research/sherpa-win`, install the QA-only `scripts/prosody-qa-requirements.txt`, run `scripts/make_question_audio.py`, compile `question_prosody.cpp` as `.research/prosody/ember_question.dll` with the pinned DSP include paths, and run `scripts/check_question_audio.py`. The build used `python -m ziglang c++ -shared -O2 -std=c++17 -I .deps/signalsmith-stretch -I .deps/signalsmith-linear/include app/src/main/cpp/question_prosody.cpp -o .research/prosody/ember_question.dll`. These Python/desktop tools are not part of the APK.

Phone check: compare `Are you ready?`, `Do you want some tea?`, `Can we go now?`, `Where are you going?`, and `I am ready.` in the app. Listen for a restrained rise on the polar questions, unchanged earlier words/speaker identity, and no new artifacts. Repeat in a paragraph, after cache replay, and at different playback speeds. Record actual latency and naturalness on the SM-G975N before accepting the change.

## Previous 0.3.0 baseline - simplified standard reader

Verified October 25, 2026:

- One fixed standard English voice (Kokoro v0.19 blended US English, speaker 0). No mode, tone, expression, voice, language, or model-import picker. The only reading controls are speed and Read/Pause/Stop.
- The application cannot select the Qwen engine: its Java/JNI path, native target, and model catalog have been removed. OpenBLAS is no longer linked. Old settings are ignored. Previously installed files are left untouched; only the existing compact `fast-model` installation is used.
- The compact voice bundle, pinned runtime, and JNI inference kernels are unchanged from 0.2.0. A compatible 0.2.0 compact installation is reused, so upgrading does not require setting up the voice again. A fresh install still verifies/unpacks once on a worker thread.
- The first complete sentence is sent to synthesis separately. Later sentences are grouped to at most 260 UTF-16 code units. Fixed an edge case where whitespace exactly at the chunk limit could exceed it by one character. Tests preserve text and surrogate boundaries.
- An in-memory LRU retains up to 2,880,000 samples (120 seconds of 24 kHz audio, about 11.5 MB) and at most 64 passages. Retained text reuses its PCM regardless of playback speed. Oversized/empty audio is not stored. The cache is cleared under idle memory pressure and on close; process exit also discards it. No persistent audio cache is created.
- Full cache hits bypass model loading as well as inference. New text still requires synthesis. Complete-passage buffering and a two-passage queue remain; continuous long reading is not guaranteed when generation is slower than playback.
- Model file checks no longer run from the UI render callback. The worker checks installation when an uncached read needs the engine.
- Clean build, JVM tests, and Android lint passed. **13 JVM tests** passed with zero failures: eight text/input/chunk cases and five cache/reuse/eviction cases. Android lint reports zero errors; prototype style/portability warnings remain.
- Final artifact checks inspect APK contents, declared permissions, signing identity, and ZIP/ELF 16 KB alignment. The development signing identity matches prior versions for in-place upgrades. Checksums are in `dist/SHA256SUMS.txt`.

The packaged native set is limited to the JNI adapter, sherpa-onnx C API, and ONNX Runtime. The APK includes GPL/upstream notices and source directions. Minimum Android API 28, target 35, ARM64 only, no permissions.

## Existing native inference evidence

The unchanged compact model and matching Windows x64 sherpa C API were exercised during 0.2.0 development. Four utterances produced finite, non-silent mono audio at 24 kHz with four CPU threads. The exact bundled model exposes eleven embeddings; this release fixes selection to speaker 0 and offers no voice picker.

- Model load: 1.35 seconds.
- `I'm glad you're here.`: 2.61 seconds synthesis, 1.06 seconds audio.
- Same sentence, warm engine: 2.83 seconds synthesis.
- A sentence containing date/time information: 9.37 seconds synthesis, 4.84 seconds audio.
- Three short sentences: 9.60 seconds synthesis, 4.75 seconds audio.

These are **prior Windows measurements of the unchanged engine, not 0.3.0 S10+ results**. They prove waveform generation and basic numeric validity, not pronunciation accuracy, naturalness, emotion recognition, Android JNI execution, or gap-free reading. The new cache and sentence scheduling have JVM/source checks; audible startup and cache replay on Android remain unverified.

The pinned compact model contains 157,947,103 unpacked bytes in 360 files, with a 102,643,830-byte bundled ZIP. Model archive SHA-256: `c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd`. Runtime: sherpa-onnx 1.12.36 at `90f3020f2fb723e5990449c276f74b34a23bc3c2`; ARM64 archive SHA-256: `c8a03a8947406558063e302f5d763f0a7d101ec88d436cc678840590460740ab`.

## User evidence and remaining phone check

The user tests on Samsung SM-G975N (Galaxy S10+) and reports roughly five minutes before reading after switching options. Earlier versions with Qwen were also reported to take five minutes; streaming in 0.1.1 worsened fluency. The user subsequently confirmed that only Emotional reading had the five-minute delay; Fast Natural reading did not have that delay. No numerical phone timing or listening-quality acceptance was supplied for the compact voice. Removing the large-engine path is an architectural fix for mode switching, not proof of a measured phone speed improvement.

No Android phone is attached to ADB. Launch, playback, real latency, cancellation, memory behavior, and listening quality on the S10+ remain unverified. The voice uses natural learned prosody; semantic selection of an emotion for every short sentence is not claimed.

Device checks:

1. Upgrade from 0.2.0 in place with Emotional reading previously selected. Confirm the new screen has no pickers/import button and saved text remains. An existing compact installation should need no setup.
2. Also test a fresh installation with no external model pack and no connection. One-time automatic setup should finish, then read a short sentence.
3. Read the same sentence again, and again with another playback speed. Confirm audio is reused without model/synthesis work, words are unchanged, and playback is smooth. Record actual first-audio time for the fresh and repeated reads.
4. Edit a word and confirm the new text is spoken. Read multiple paragraphs, long sentences, numbers, dates, names, negation, and contractions. Listen for omissions, repetitions, and gaps between passages.
5. Exercise cache eviction, process restart, idle memory pressure, pause/resume, Stop during setup/generation/playback, foreground exit, and audio focus interruption. Check no stuck worker or unexpected audio remains.
6. Record phone RAM/Android version, latency, synthesis/audio durations, memory, temperature, and battery use. Compare these with prior versions using identical text.

Phone acceptance remains pending; no five-minute-to-seconds guarantee or listening-quality improvement is asserted without that test.
