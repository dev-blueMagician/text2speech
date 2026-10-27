param([switch]$Apply)
$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot)).TrimEnd('\')
$workspacePrefix = $workspaceRoot + [IO.Path]::DirectorySeparatorChar
$versionMatch = [regex]::Match((Get-Content -LiteralPath (Join-Path $workspaceRoot 'app/build.gradle') -Raw), "versionName\s+'([^']+)'")
if (-not $versionMatch.Success) { throw 'Could not determine the current release version.' }
$currentVersion = $versionMatch.Groups[1].Value

function Resolve-CleanupTarget([string]$RelativePath) {
    $target = [IO.Path]::GetFullPath((Join-Path $workspaceRoot $RelativePath))
    if (-not $target.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing a cleanup target outside the workspace: $target"
    }
    # Refuse symlink/junction ancestors so a contained spelling cannot escape.
    $ancestor = $target
    while ($ancestor -and $ancestor -ne $workspaceRoot) {
        if (Test-Path -LiteralPath $ancestor) {
            $item = Get-Item -LiteralPath $ancestor -Force
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw "Refusing a linked cleanup target: $ancestor"
            }
        }
        $ancestor = Split-Path -Parent $ancestor
    }
    return $target
}

$relativeTargets = @(
    '.models/source', '.deps/openblas', '.deps/qwen-tts',
    '.deps/sherpa/libsherpa-onnx-cxx-api.so',
    '.research', '.tools/prosody-env', '.tools/apk-check',
    '.tools/jdk-unpack', '.tools/cmdline-unpack', '.tools/runtime-unpack',
    '.tools/test-libs', '.tools/unit-tests', '.tools/android-sdk/.temp',
    '.tools/cleanup-before.json',
    'app/build', 'app/.cxx', '.gradle', 'build', 'scripts/__pycache__',
    'scripts/prepare_model.py', 'dist/Qwen-model-LICENSE.txt'
)

# Keep the current runtime/model archives for preparation without downloads,
# and the upstream sources needed to regenerate the accompanying source ZIP.
$retainedArchives = @(
    'kokoro-int8-en-v0_19.tar.bz2', 'sherpa-android-1.12.36.tar.bz2',
    'sherpa-source.zip', 'espeak-source.zip', 'phonemize-source.zip',
    'source-kaldi-decoder.tar.gz', 'source-kaldi-native-fbank.tar.gz',
    'source-kaldifst.tar.gz', 'source-openfst.tar.gz',
    'source-simple-sentencepiece.tar.gz',
    'signalsmith-stretch-source.zip', 'signalsmith-linear-source.zip'
)
$downloads = Join-Path $workspaceRoot '.tools/downloads'
if (Test-Path -LiteralPath $downloads) {
    foreach ($item in Get-ChildItem -LiteralPath $downloads -Force) {
        if ($retainedArchives -notcontains $item.Name) {
            $relativeTargets += '.tools/downloads/' + $item.Name
        }
    }
}
foreach ($pattern in @('.tools/*.log', '.tools/*-metadata.xml', '.tools/range-*',
                       '.deps/sherpa/*.probe')) {
    if (-not (Test-Path -LiteralPath (Split-Path -Parent (Join-Path $workspaceRoot $pattern)))) { continue }
    foreach ($item in Get-ChildItem -Path (Join-Path $workspaceRoot $pattern) -File -Force) {
        $relativeTargets += $item.FullName.Substring($workspacePrefix.Length)
    }
}
$distribution = Join-Path $workspaceRoot 'dist'
if (Test-Path -LiteralPath $distribution) {
    foreach ($item in Get-ChildItem -LiteralPath $distribution -File -Force) {
        if ($item.Name -match '^EmberReader-(.+)-(debug\.apk|source\.zip)$' -and $Matches[1] -ne $currentVersion) {
            $relativeTargets += 'dist/' + $item.Name
        }
    }
}

$targets = @($relativeTargets | Select-Object -Unique | ForEach-Object {
    $resolved = Resolve-CleanupTarget $_
    if (Test-Path -LiteralPath $resolved) { $resolved }
})
# Resolve and validate every target before performing the first deletion.
foreach ($target in $targets) {
    if ($Apply) {
        Write-Output "Removing $target"
        Remove-Item -LiteralPath $target -Recurse -Force
    } else {
        Write-Output "Would remove $target"
    }
}
if (-not $Apply) { Write-Output 'Preview only. Run with -Apply to delete these disposable files.' }
