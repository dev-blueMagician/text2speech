"""Alternative SDK installer for connections that stall on the SDK manager's large downloads."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import shutil
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
import os
from bootstrap import TOOLS, download

PACKAGES = {
    'platforms/android-35': 'platform-35_r02.zip',
    'build-tools/35.0.0': 'build-tools_r35_windows.zip',
    'ndk/28.0.13004108': 'android-ndk-r28-windows.zip',
    'platform-tools': 'platform-tools_r37.0.1-win.zip',
}


def main():
    raw = urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml', timeout=60).read()
    repo = ET.fromstring(raw)
    def install(pair):
        directory, name = pair
        target = TOOLS / 'android-sdk' / directory
        if (target / 'source.properties').exists(): return
        archive_info = next(a.find('complete') for p in repo.findall('remotePackage')
                            for a in p.findall('archives/archive') if a.findtext('complete/url') == name)
        archive = TOOLS / 'downloads' / name
        download('https://dl.google.com/android/repository/' + name, archive)
        algorithm = archive_info.find('checksum').attrib.get('type', 'sha1')
        with archive.open('rb') as source:
            actual = hashlib.file_digest(source, algorithm).hexdigest()
        if actual != archive_info.findtext('checksum'):
            raise RuntimeError('SDK checksum mismatch: ' + name)
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                relative = entry.filename.partition('/')[2]
                if not relative or entry.is_dir(): continue
                destination = target / relative
                if not destination.resolve().is_relative_to(target.resolve()): raise RuntimeError('Unsafe SDK path')
                physical = Path('\\\\?\\' + str(destination.resolve())) if os.name == 'nt' else destination
                physical.parent.mkdir(parents=True, exist_ok=True)
                with source.open(entry) as content, physical.open('wb') as output:
                    shutil.copyfileobj(content, output, 1024 * 1024)
        print('Installed ' + directory, flush=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(install, PACKAGES.items()))


if __name__ == '__main__': main()
