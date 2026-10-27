"""Install workspace-local Android build tools and pinned inference sources (Windows)."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import urllib.request
import zipfile
import re

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'


def remove_tool_stage(path):
    """Delete only a resolved staging directory inside this workspace's tools."""
    path = Path(path).resolve()
    if path == TOOLS.resolve() or not path.is_relative_to(TOOLS.resolve()):
        raise RuntimeError('Refusing to remove a directory outside tool staging')
    if path.exists():
        shutil.rmtree(path)


def download(url, destination, sha256=None):
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists() and (not sha256 or digest(destination) == sha256):
        return
    print('Downloading ' + destination.name, flush=True)
    temporary = destination.with_suffix(destination.suffix + '.part')
    # Ranged downloads also work around unreliable long-lived connections on Windows.
    probe = subprocess.run(['curl.exe', '-fLsS', '--range', '0-0', '--max-time', '40',
                            '-D', '-', '-o', str(destination) + '.probe', url], capture_output=True, check=True)
    sizes = re.findall(rb'Content-Range:\s*bytes\s+0-0/(\d+)', probe.stdout, re.I)
    if sizes and int(sizes[-1]) > 8 * 1024 * 1024:
        size = int(sizes[-1])
        chunk_size = 4 * 1024 * 1024
        chunks = destination.parent / (destination.name + '.chunks')
        chunks.mkdir(exist_ok=True)
        def fetch(start):
            end = min(size - 1, start + chunk_size - 1)
            part = chunks / str(start)
            if part.exists() and part.stat().st_size == end - start + 1:
                return part
            subprocess.run(['curl.exe', '-fLsS', '--retry', '5', '--retry-all-errors', '--retry-delay', '0',
                            '--connect-timeout', '10', '--max-time', '30', '--speed-limit', '65536', '--speed-time', '10',
                            '--range', f'{start}-{end}', '-o', str(part), url], check=True)
            if part.stat().st_size != end - start + 1:
                raise RuntimeError('Incomplete download range: ' + str(part))
            return part
        with ThreadPoolExecutor(max_workers=12) as pool:
            parts = list(pool.map(fetch, range(0, size, chunk_size)))
        with temporary.open('wb') as output:
            for part in parts:
                with part.open('rb') as source:
                    shutil.copyfileobj(source, output, 1024 * 1024)
    else:
        subprocess.run(['curl.exe', '--fail', '--location', '--silent', '--show-error',
                        '--retry', '2', '--connect-timeout', '20', '--max-time', '1200',
                        '--output', str(temporary), url], check=True)
    if sha256 and digest(temporary) != sha256:
        raise RuntimeError('Checksum mismatch: ' + destination.name)
    temporary.replace(destination)
    # Completed range parts/probes duplicate the verified archive, sometimes in full.
    chunks = destination.parent / (destination.name + '.chunks')
    if chunks.exists():
        remove_tool_stage(chunks)
    Path(str(destination) + '.probe').unlink(missing_ok=True)


def digest(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def unzip(archive, target):
    with zipfile.ZipFile(archive) as source:
        for info in source.infolist():
            resolved = (Path(target) / info.filename).resolve()
            if not resolved.is_relative_to(Path(target).resolve()):
                raise RuntimeError('Unsafe archive path')
        source.extractall(target)


def native_dependencies():
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_fast_voice.py')], check=True)
    subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_prosody.py')], check=True)


def jdk():
    target = TOOLS / 'jdk'
    if (target / 'bin/java.exe').exists():
        return
    api = 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows'
    package = json.loads(subprocess.check_output(['curl.exe', '-fLsS', '--max-time', '60', api]))[0]['binary']['package']
    archive = TOOLS / 'downloads/jdk.zip'
    download(package['link'], archive, package['checksum'])
    stage = TOOLS / 'jdk-unpack'
    unzip(archive, stage)
    folder = next(p for p in stage.iterdir() if (p / 'bin/java.exe').exists())
    if target.exists():
        shutil.copytree(folder, target, dirs_exist_ok=True)
    else:
        shutil.move(str(folder), str(target))
    remove_tool_stage(stage)


def gradle():
    if (TOOLS / 'gradle-8.11.1/bin/gradle.bat').exists():
        return
    archive = TOOLS / 'downloads/gradle-8.11.1-bin.zip'
    url = 'https://services.gradle.org/distributions/gradle-8.11.1-bin.zip'
    with urllib.request.urlopen(url + '.sha256', timeout=60) as source:
        checksum = source.read().decode().strip()
    download(url, archive, checksum)
    if not (TOOLS / 'gradle-8.11.1/bin/gradle.bat').exists():
        unzip(archive, TOOLS)


def sdk():
    target = TOOLS / 'android-sdk/cmdline-tools/latest'
    if (target / 'bin/sdkmanager.bat').exists():
        return
    archive = TOOLS / 'downloads/commandlinetools.zip'
    download('https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', archive)
    if not (target / 'bin/sdkmanager.bat').exists():
        stage = TOOLS / 'cmdline-unpack'
        unzip(archive, stage)
        target.parent.mkdir(parents=True, exist_ok=True)
        if target.exists():
            shutil.copytree(stage / 'cmdline-tools', target, dirs_exist_ok=True)
        else:
            shutil.move(str(stage / 'cmdline-tools'), str(target))
        remove_tool_stage(stage)


def main():
    TOOLS.mkdir(exist_ok=True)
    if '--runtime-only' in sys.argv:
        native_dependencies()
        return
    with ThreadPoolExecutor(max_workers=4) as pool:
        for result in pool.map(lambda f: f(), [native_dependencies, jdk, gradle, sdk]):
            pass
    import os
    env = os.environ.copy()
    env['JAVA_HOME'] = str(TOOLS / 'jdk')
    env['ANDROID_HOME'] = str(TOOLS / 'android-sdk')
    env.pop('DEBUG', None)
    manager = TOOLS / 'android-sdk/cmdline-tools/latest/bin/sdkmanager.bat'
    required = ['platform-tools/adb.exe', 'platforms/android-35/android.jar',
                'build-tools/35.0.0/aapt2.exe', 'ndk/28.0.13004108/source.properties',
                'cmake/3.22.1/bin/cmake.exe']
    if not all((TOOLS / 'android-sdk' / name).is_file() for name in required):
        with (TOOLS / 'sdk-setup.log').open('w', encoding='utf-8') as log:
            subprocess.run([str(manager), '--licenses'], input='y\n' * 100, text=True, env=env,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
            print('Installing SDK 35, NDK 28 and CMake. Details: .tools/sdk-setup.log', flush=True)
            subprocess.run([str(manager), 'platform-tools', 'platforms;android-35', 'build-tools;35.0.0',
                            'ndk;28.0.13004108', 'cmake;3.22.1'], env=env,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
    (ROOT / 'local.properties').write_text('sdk.dir=' + str(TOOLS / 'android-sdk').replace('\\', '/') + '\n')
    print('Build tools ready. Run scripts/build.ps1', flush=True)


if __name__ == '__main__':
    main()
