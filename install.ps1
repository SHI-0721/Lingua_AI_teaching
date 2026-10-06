param([string]$Serial)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $adb = Join-Path $PSScriptRoot '.tools/android-sdk/platform-tools/adb.exe'
    $devices = @(& $adb devices | Select-String '^([^\s]+)\s+device$' | ForEach-Object { $_.Matches[0].Groups[1].Value })
    if ($Serial) {
        if ($Serial -notin $devices) { throw 'The selected device is not connected or USB debugging is not authorized.' }
    } elseif ($devices.Count -eq 1) { $Serial = $devices[0] }
    else { throw 'Connect one Android device and authorize USB debugging, or specify -Serial.' }
    & "$PSScriptRoot/build.ps1" -Tasks ':app:assembleDebug'
    & $adb -s $Serial install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adb -s $Serial shell am start -n 'com.lingua.app/.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'APK installed, but Activity launch failed.' }
    $linguaProcess = & $adb -s $Serial shell pidof com.lingua.app
    if (-not $linguaProcess) { throw 'APK installed, but no running app process was detected. Check device logs.' }
    Write-Output 'APK installed and app process detected. Interactive and TTS checks still require device verification.'
} finally { Pop-Location }
