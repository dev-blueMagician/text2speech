"""Prepare pinned MIT DSP headers for duration-preserving question intonation."""
import zipfile
from bootstrap import ROOT, TOOLS, download

PACKAGES = [
    ('signalsmith-stretch', 'Signalsmith-Audio/signalsmith-stretch',
     '88061fd4eda56a2353c5d65c74aa3f4ccf76f9da',
     '717711bcf859a9a9a8cbb4baa6a8f994ca594368335eb73430fb6ac98ff1fc04'),
    ('signalsmith-linear', 'Signalsmith-Audio/linear',
     '7bb1c8f36794d6980080ce817c91f8da8fe56a66',
     'f9a7b159488c6e2940718d6de1048996cecdb7fadac9c142b677953f52df15d4'),
]


def main():
    for name, repo, revision, checksum in PACKAGES:
        archive = TOOLS / f'downloads/{name}-source.zip'
        download(f'https://codeload.github.com/{repo}/zip/{revision}', archive, checksum)
        target = ROOT / '.deps' / name
        with zipfile.ZipFile(archive) as source:
            for item in source.infolist():
                relative = item.filename.partition('/')[2]
                if item.is_dir() or not relative or not (relative.endswith('.h') or relative == 'LICENSE.txt'):
                    continue
                output = target / relative
                if not output.resolve().is_relative_to(target.resolve()):
                    raise RuntimeError('Unsafe DSP path')
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(source.read(item))
        (ROOT / 'licenses' / f'{name}-MIT.txt').write_bytes((target / 'LICENSE.txt').read_bytes())
    print('Prepared pinned question-prosody DSP headers.')


if __name__ == '__main__':
    main()
