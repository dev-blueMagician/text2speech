"""Package application and upstream runtime source alongside the development APK.

Run after bootstrap and the source-archive downloads described in SOURCE.txt.
Large permissive dependencies can also be obtained from the direct upstream
source links in SOURCE.txt; sherpa's CMake files pin its remaining dependencies.
"""
from pathlib import Path
import zipfile
from bootstrap import ROOT

SOURCE_NOTICE = '''Ember Reader 0.3.2 - source access and binary license

The combined APK includes GPL-3.0-or-later eSpeak NG through sherpa-onnx.
Convey the APK with GPL-3.0-or-later terms, the included notices, and access
to corresponding source. The project's own source remains offered under MIT.

Application and native runtime source: EmberReader-0.3.2-source.zip, beside
the APK. This includes the app, build scripts, user/developer documentation,
and complete source archives for sherpa-onnx, eSpeak NG, piper-phonemize,
kaldi-decoder, kaldi-native-fbank, kaldifst, OpenFst, simple-sentencepiece,
Signalsmith Stretch and Signalsmith Linear (MIT question-intonation DSP).
No source archive is a model import pack.

Additional corresponding source is available for free from these upstream
download locations. Keep these directions available next to the APK:

ONNX Runtime 1.23.2 source:
https://github.com/microsoft/onnxruntime/archive/refs/tags/v1.23.2.zip
The Android binary is the unmodified one packaged with sherpa-onnx 1.12.36.
Its build dependency and toolchain definitions are in the upstream source.

sherpa-onnx source (also included in the source ZIP):
https://github.com/k2-fsa/sherpa-onnx/archive/90f3020f2fb723e5990449c276f74b34a23bc3c2.zip
eSpeak NG source (also included):
https://github.com/csukuangfj/espeak-ng/archive/f6fed6c58b5e0998b8e68c6610125e2d07d595a7.zip
piper-phonemize source (also included):
https://github.com/csukuangfj/piper-phonemize/archive/78a788e0b719013401572d70fef372e77bff8e43.zip

Rebuild the application on Windows using README.md: bootstrap.py acquires
the pinned tools and unmodified native binaries; prepare_fast_voice.py
recreates the bundled compact model with checked archive hashes; build.ps1
compiles Java/JNI, runs JVM tests, and performs Android lint.

To change/recompile the natural native runtime, extract sherpa's source ZIP
and use its build-android-arm64-v8a.sh and CMake definitions. Its cmake/
directory specifies dependency source URLs and hashes. Use the corresponding
eSpeak/piper archives supplied here, and replace the .deps/sherpa ARM64 libs.
The application links its C API and ONNX Runtime; it does not require the
React Native wrapper, any credential, or a cloud endpoint.

The bundled Kokoro model weights use Apache-2.0 terms and are obtained from
sherpa's hash-pinned English conversion in prepare_fast_voice.py. This release
uses one standard English voice. Model weights are inference assets, distinct
from application/runtime source; docs/MODEL.md explains their provenance and
links the upstream v0.19 Python implementation. App/model architecture is
documented in README.md and docs/ARCHITECTURE.md.

The GPL text and upstream notices are in licenses/ and APK assets/licenses/.
Retain the MIT/Apache/BSD copyright and attribution notices as supplied.
'''


def main():
    dist = ROOT / 'dist'
    dist.mkdir(exist_ok=True)
    (dist / 'SOURCE.txt').write_text(SOURCE_NOTICE, encoding='utf-8')
    (ROOT / 'licenses/SOURCE.txt').write_text(SOURCE_NOTICE, encoding='utf-8')
    output = dist / 'EmberReader-0.3.2-source.zip'
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for folder in ['app/src', 'scripts', 'gradle', 'licenses', 'docs']:
            for path in sorted((ROOT / folder).rglob('*')):
                if not path.is_file() or '__pycache__' in path.parts or path.name == 'fast-voice.zip':
                    continue
                archive.write(path, path.relative_to(ROOT).as_posix())
        for name in ['LICENSE', 'THIRD_PARTY_NOTICES.md', 'README.md', 'VALIDATION.md',
                     '.gitignore', 'build.gradle', 'settings.gradle', 'gradle.properties',
                     'gradlew', 'gradlew.bat', 'app/build.gradle']:
            archive.write(ROOT / name, name)
        archive.write(dist / 'SOURCE.txt', 'SOURCE.txt')
        archive.write(dist / 'INSTALL.txt', 'dist/INSTALL.txt')
        archive.write(dist / 'SOURCE.txt', 'dist/SOURCE.txt')
        archive.write(dist / 'question-prosody-validation.json', 'dist/question-prosody-validation.json')
        if (dist / 'question-current-validation.json').exists():
            archive.write(dist / 'question-current-validation.json', 'dist/question-current-validation.json')
        names = ['sherpa-source.zip', 'espeak-source.zip', 'phonemize-source.zip',
                 'source-kaldi-decoder.tar.gz', 'source-kaldi-native-fbank.tar.gz',
                 'source-kaldifst.tar.gz', 'source-openfst.tar.gz', 'source-simple-sentencepiece.tar.gz',
                 'signalsmith-stretch-source.zip', 'signalsmith-linear-source.zip']
        for name in names:
            archive.write(ROOT / '.tools/downloads' / name, 'upstream-source/' + name)
    print(f'Created {output.name}: {output.stat().st_size:,} bytes')


if __name__ == '__main__':
    main()
