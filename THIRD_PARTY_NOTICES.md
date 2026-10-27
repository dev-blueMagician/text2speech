# Third-party components

The project's own application source is MIT-licensed. The combined APK includes GPL-3.0-or-later eSpeak NG and must be conveyed under GPL terms with corresponding source. The MIT grant remains available for the application's own source. Keep SOURCE.txt and the source ZIP with APK distributions.

| Component | License | Origin |
| --- | --- | --- |
| Kokoro-82M English v0.19 INT8 and voice embeddings | Apache-2.0 | https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2 |
| sherpa-onnx 1.12.36 C API | Apache-2.0 plus linked dependency notices | https://github.com/k2-fsa/sherpa-onnx/tree/90f3020f2fb723e5990449c276f74b34a23bc3c2 |
| ONNX Runtime 1.23.2 | MIT plus bundled third-party notices | https://github.com/microsoft/onnxruntime/tree/v1.23.2 |
| eSpeak NG and language data at f6fed6c58b5e0998b8e68c6610125e2d07d595a7 | GPL-3.0-or-later plus component notices | https://github.com/csukuangfj/espeak-ng/tree/f6fed6c58b5e0998b8e68c6610125e2d07d595a7 |
| piper-phonemize at 78a788e0b719013401572d70fef372e77bff8e43 | MIT | https://github.com/csukuangfj/piper-phonemize/tree/78a788e0b719013401572d70fef372e77bff8e43 |
| kaldi-decoder, kaldi-native-fbank, kaldifst/OpenFst, simple-sentencepiece | Apache/BSD; retained upstream notices | Pinned source archives and sherpa CMake definitions in the source ZIP |
| Signalsmith Stretch at 88061fd4eda56a2353c5d65c74aa3f4ccf76f9da | MIT | https://github.com/Signalsmith-Audio/signalsmith-stretch |
| Signalsmith Linear at 7bb1c8f36794d6980080ce817c91f8da8fe56a66 | MIT | https://github.com/Signalsmith-Audio/linear |
| JUnit 4 (development tests only) | EPL-1.0 | https://junit.org/junit4/ |

The build copies license notices and source-access directions into APK assets/licenses. The voice is verified against a per-file SHA-256 manifest during automatic first-use setup. Model weights, upstream implementation code, native runtime libraries, and application source are separate artifacts; see the [model guide](docs/MODEL.md) for their roles and source locations. Runtime libraries packaged by this app are the JNI adapter, sherpa-onnx C API, and ONNX Runtime. Each distributed artifact retains its applicable notices and source-access directions.
