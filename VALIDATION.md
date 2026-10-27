# Validation and acceptance

This document describes evidence for the current application, Ember Reader 0.3.2. It separates automated checks from the device listening checks required to assess a text reader. Application design is in [ARCHITECTURE.md](docs/ARCHITECTURE.md); model details are in [MODEL.md](docs/MODEL.md).

## Automated evidence

The application build was verified on October 25, 2026:

| Area | Evidence | What it does not establish |
| --- | --- | --- |
| Android build and lint | Java/native debug build and Android lint completed successfully | Successful launch or audio playback on a physical phone |
| JVM tests | 20 tests passed: text/chunk rules, question recognition, and audio-cache behavior | Native model inference or perceived naturalness |
| Native speech inference | Matching Windows sherpa runtime generated finite, non-silent 24 kHz audio using the bundled model | Android latency, pronunciation accuracy, or continuous playback |
| Question DSP | Exact C++ processor exercised on four generated questions, silence/noise, an already-rising signal, and a known falling signal | Artifact-free output or a perceptually convincing question ending |
| APK packaging | Version code 7/name 0.3.2; ARM64; no declared permissions; signing and ZIP/ELF 16 KB alignment checks passed | Store readiness or broad device compatibility |
| Assets | Bundled voice matches the prepared asset; current source-access directions are packaged | A reproducible training pipeline for the neural network |

The native library set is `libember_fast.so`, `libsherpa-onnx-c-api.so`, and `libonnxruntime.so`. Release checksums are in [SHA256SUMS.txt](dist/SHA256SUMS.txt). The source archive and source-access directions accompany the development APK.

After workspace cleanup on October 26, 2026, a rebuild from deleted project build/CMake output succeeded with `scripts/build.ps1 -Offline`: all 52 Gradle tasks executed, all 20 JVM tests passed, and lint reported no errors. The retained release APK and freshly built APK both contain all 360 manifest-verified voice files and exactly the current three native libraries. The retained release APK's hash is unchanged, and it declares no permissions. This verifies the prepared workspace's Gradle offline build path and packaged asset integrity, not physical-phone listening performance. Current-only desktop DSP checks also passed before the optional QA environment was removed.

## Audio measurements and their limits

[question-prosody-validation.json](dist/question-prosody-validation.json) records desktop checks on `Are you ready?`, `Do you want some tea?`, `Can we go now?`, and `Do you like mountains?`. Measured rises across the corrected region are approximately 3.7-5.4 semitones. Sample counts are unchanged, prefixes before the correction region are exact, and outputs are finite and bounded.

Pitch analysis uses Praat autocorrelation. The JSON records both a selected octave cost and default-cost results. The tea clip has an octave/subharmonic ambiguity: different tracker settings report different pitch trajectories. This is a measurement limitation and a reason to retain listening evaluation. Pitch statistics alone are not acceptance of speech quality.

The DSP took roughly 7-11 ms on the development Windows machine, excluding neural synthesis. Separate desktop inference measurements for a short sentence were about 2.6-2.8 seconds, plus about 1.35 seconds for model loading. These are desktop examples, not mobile speed guarantees or a benchmark across input lengths.

## Reproduce the checks

Run the standard build from the project root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

The script assembles the debug APK, runs JVM tests, and runs Android lint. Test results are written under `app/build/test-results/`; lint reports are under `app/build/reports/`.

Desktop waveform QA is optional and is not part of the Android application. Install [prosody-qa-requirements.txt](scripts/prosody-qa-requirements.txt), prepare the matching Windows sherpa runtime used by `scripts/benchmark_fast_voice.py`, and run `scripts/make_question_audio.py`. Compile the current processor using the pinned header dependencies:

```powershell
python -m ziglang c++ -shared -O2 -std=c++17 -I .deps/signalsmith-stretch -I .deps/signalsmith-linear/include app/src/main/cpp/question_prosody.cpp -o .research/prosody/ember_question.dll
```

Run `python scripts/check_question_audio.py` to check the current processor against raw model audio. It writes `dist/question-current-validation.json` and a raw/processed `question-current-comparison.wav`; it does not require any legacy DLL or release archive. An optional `--compare-dll PATH` can compare a separately supplied baseline. The existing `question-comparison.wav` and `question-prosody-validation.json` are preserved historical measurements, rather than files required by the app.

Desktop QA environments, generated DLLs/clips, and Windows inference binaries are optional development tools and are not retained by workspace cleanup. Recreate them before running the optional audio check. The [storage guide](docs/STORAGE.md) distinguishes these tools from the voice and dependencies retained for offline Android builds.

## Device acceptance

Android execution, startup time, memory use, and listening quality on the target phone have not been accepted by an end-to-end device test. The app is a foreground reader; background/lock-screen playback is outside its current behavior.

A device check should cover:

1. Fresh installation, automatic voice setup without a network connection, and playback of a short sentence.
2. Repeated text, changed text, another reading speed, and a process restart; verify correct words and cache behavior.
3. Paragraphs, long questions, numbers, dates, names, abbreviations, and contractions; listen for omissions, repetitions, mispronunciations, and gaps.
4. Yes/no questions, statements, wh-questions, and alternative questions; assess intonation and vocal character by listening.
5. Pause/Resume, Stop during setup/generation/playback, audio focus interruption, leaving the screen, and idle memory pressure.
6. Record device/Android version, first-audio delay, synthesis duration, memory use, heat, and battery impact with the tested text.

Detailed historical observations and release comparisons are preserved in [validation history](docs/VALIDATION_HISTORY.md). They are supplementary evidence rather than the introduction to the application.
