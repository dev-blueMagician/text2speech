"""Prepare pinned local Kokoro assets and the ARM64 sherpa-onnx C API."""
from pathlib import Path
import json
import tarfile
import zipfile
import shutil
from bootstrap import ROOT, TOOLS, download, digest

VERSION = '1.12.36'
SOURCE_REV = '90f3020f2fb723e5990449c276f74b34a23bc3c2'
HEADER_SHA = '437b1279047877167d8fadc74a60d47f3df514d703fdac1c1b6851da9bc2fdb4'
ANDROID_SHA = 'c8a03a8947406558063e302f5d763f0a7d101ec88d436cc678840590460740ab'
MODEL_SHA = 'c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd'
MODEL_NAME = 'kokoro-int8-en-v0_19'


def main():
    native_archive = TOOLS / f'downloads/sherpa-android-{VERSION}.tar.bz2'
    model_archive = TOOLS / ('downloads/' + MODEL_NAME + '.tar.bz2')
    download(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/v{VERSION}/sherpa-onnx-v{VERSION}-android.tar.bz2', native_archive, ANDROID_SHA)
    download(f'https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/{MODEL_NAME}.tar.bz2', model_archive, MODEL_SHA)
    native = ROOT / '.deps/sherpa'
    native.mkdir(parents=True, exist_ok=True)
    with tarfile.open(native_archive) as archive:
        for member in archive:
            if member.isfile() and member.name.startswith('./jniLibs/arm64-v8a/') and member.name.endswith(('libsherpa-onnx-c-api.so', 'libonnxruntime.so')):
                (native / Path(member.name).name).write_bytes(archive.extractfile(member).read())
    upstream = f'https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/{SOURCE_REV}/'
    header = native / f'c-api-{VERSION}.h'
    download(upstream + 'sherpa-onnx/c-api/c-api.h', header, HEADER_SHA)
    target_header = native / 'sherpa-onnx/c-api/c-api.h'
    target_header.parent.mkdir(parents=True, exist_ok=True)
    target_header.write_bytes(header.read_bytes())
    download(upstream + 'LICENSE', native / 'LICENSE')
    source = ROOT / '.models/fast'
    source.mkdir(parents=True, exist_ok=True)
    with tarfile.open(model_archive) as archive:
        for member in archive:
            if not member.isfile():
                continue
            relative = Path(member.name).parts[1:]
            if not relative:
                continue
            target = source.joinpath(*relative)
            if not target.resolve().is_relative_to(source.resolve()):
                raise RuntimeError('Unsafe model path')
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(member) as input_file, target.open('wb') as output:
                shutil.copyfileobj(input_file, output)
    assets = ROOT / 'app/src/main/assets'
    bundle = assets / 'fast-voice.zip'
    files = sorted(p for p in source.rglob('*') if p.is_file())
    manifest = {'revision': MODEL_NAME + ':' + MODEL_SHA, 'files': []}
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for path in files:
            name = path.relative_to(source).as_posix()
            manifest['files'].append({'path': name, 'size': path.stat().st_size, 'sha256': digest(path)})
            archive.write(path, name)
    (assets / 'fast-model-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(f'Bundled {len(files)} verified files, {sum(p.stat().st_size for p in files):,} bytes; ZIP {bundle.stat().st_size:,} bytes', flush=True)


if __name__ == '__main__':
    main()
