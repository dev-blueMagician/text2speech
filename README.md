# Ember Reader

Ember Reader turns English text into spoken audio on an Android phone. It is designed for reading passages with one consistent voice, simple controls, and local processing. Paste text, open a UTF-8 `.txt` file, or share text from another app, then tap **Read aloud**.

The app combines a pretrained Kokoro speech model with a rule-based adjustment for yes/no question endings. Natural rhythm and pronunciation come from the model. Question recognition and the extra rising ending come from application code. It does not classify emotions or accept acting instructions such as "read this sadly."

## Start reading

1. Use an **ARM64 Android phone running Android 9 or later**. Install [EmberReader-0.3.2-debug.apk](dist/EmberReader-0.3.2-debug.apk). This is a development build.
2. Allow approximately **450 MB of free internal storage** for initial voice setup.
3. Enter English text or choose **Open .txt**, then tap **Read aloud**. The included voice is unpacked and verified automatically on the first read.
4. Choose a reading speed between **0.8× and 1.6×** before starting. Use **Pause/Resume** or **Stop** during playback.

Reading needs no account, network connection, model download, or separate model import. The app declares no permissions and uses Android's file picker for selected files. Keep it visible while listening; leaving the screen stops playback. Text and the selected speed are saved locally when the activity pauses.

See [installation instructions](dist/INSTALL.txt) for setup and troubleshooting.

## Reading behavior

The reader accepts up to 20,000 characters and prepares bounded passages. It can play the first completed passage while generating later ones. Each passage must finish synthesis before it can play; gaps are possible when generation is slower than playback.

One US English female voice is selected from Kokoro's bundled voice embeddings. A question such as `Do you like mountains?` can receive a rising ending when it matches the English question rules and its audio has a reliable pitch track. Statements, wh-questions, and explicit alternative questions retain the model's delivery. These rules do not cover every English construction, and listening quality varies with text.

The model stays loaded between reads while the process retains it. Up to two minutes of recently generated audio is cached in memory, allowing retained passages to be replayed at another speed without synthesis. New text still requires generation. Process exit, cache eviction, or Android memory pressure can require generation or loading again. Neither startup latency nor uninterrupted reading is guaranteed on a particular phone.

## Why this model?

Kokoro is used because this reader needs one consistent English voice, local CPU execution, and a manageable model bundle. Its pretrained prosody, available ONNX conversion, and public model assets fit the Java/native Android design. The current v0.19 package is a pinned integration baseline; it has not been proven superior to every alternative. Strong emotion instructions are outside its current role.

See the [model-selection rationale](docs/MODEL.md#why-kokoro-is-used-here) for the requirements, alternatives, version choice, and measurements needed to justify a different model.

## Understand the project

| Guide | What it explains |
| --- | --- |
| [Application architecture](docs/ARCHITECTURE.md) | Screen design, Java components, native inference, threading, playback, storage, and extension points |
| [Model guide](docs/MODEL.md) | What `.models` contains, weights versus source code, Kokoro architecture, ONNX inputs, and upstream sources |
| [Validation](VALIDATION.md) | Verified behavior, test coverage, and device/listening acceptance checks |
| [Workspace storage](docs/STORAGE.md) | Required voice/build files, removable caches, cleanup, and offline build support |
| [Third-party notices](THIRD_PARTY_NOTICES.md) | Component licenses and attribution |

The Android application is written in **Java**. A **C JNI adapter** connects it to **sherpa-onnx and ONNX Runtime**; a **C++ audio processor** adjusts recognized question endings. Python scripts prepare development tools and model assets. Python is not required on the phone.

## Build on Windows

Install Python 3 and make `python`, `powershell`, and `curl.exe` available on PATH. From the project root:

```powershell
python scripts/bootstrap.py
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
```

Bootstrap downloads workspace-local JDK 17, Gradle 8.11.1, SDK 35, NDK 28.0.13004108, CMake 3.22.1, pinned native dependencies, and the voice bundle. Development setup requires internet access. The phone performs reading locally.

The build compiles Java/native code, runs JVM tests, and runs Android lint. Output: `app/build/outputs/apk/debug/app-debug.apk`. The default build uses a development signing key; production distribution needs release signing.

To prepare assets with an existing tool installation:

```powershell
python scripts/prepare_fast_voice.py
python scripts/prepare_prosody.py
```

`.models`, `.deps`, `.tools`, `.research`, generated build output, and the large voice ZIP are ignored by Git. Bootstrap recreates required runtime assets. Read the [model guide](docs/MODEL.md) before editing or replacing a model.

With the toolchain, Gradle dependencies, and prepared assets already present, use `powershell -ExecutionPolicy Bypass -File scripts/build.ps1 -Offline` to build without Gradle downloads. A fresh checkout still needs initial development setup. Preview disposable workspace files with `scripts/clean_workspace.ps1`; add `-Apply` to remove them. The cleanup preserves the current voice, native dependencies, installed toolchain, Gradle dependency cache, and current release/source package. See [workspace storage](docs/STORAGE.md) for the retention rules.

## Source and licenses

The application's own source is available under [MIT](LICENSE). Kokoro weights use Apache-2.0 terms; a weights file is a trained artifact, distinct from the code that defines or trains the network. The [model guide](docs/MODEL.md) links the upstream implementation.

The combined APK includes GPL-3.0-or-later eSpeak NG through sherpa-onnx. Source-access directions and dependency notices are provided in [SOURCE.txt](dist/SOURCE.txt), the [source archive](dist/EmberReader-0.3.2-source.zip), and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Keep source access and notices available when redistributing the APK.
