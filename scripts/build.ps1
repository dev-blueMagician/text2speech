param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug'),
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $projectRoot '.tools\jdk'
$env:ANDROID_HOME = Join-Path $projectRoot '.tools\android-sdk'
$env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
Remove-Item Env:DEBUG -ErrorAction SilentlyContinue
$gradlePath = Join-Path $projectRoot '.tools\gradle-8.11.1\bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradlePath)) { throw 'Run python scripts/bootstrap.py first.' }
Push-Location $projectRoot
try {
    # Windows PowerShell 5 treats native stderr as errors even for successful tools.
    $ErrorActionPreference = 'Continue'
    $buildArguments = @($Tasks) + @('--console=plain')
    if ($Offline) { $buildArguments += '--offline' }
    & $gradlePath @buildArguments
    $gradleExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($gradleExit -ne 0) { throw "Gradle failed with exit code $gradleExit" }
} finally { Pop-Location }
