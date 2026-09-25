$watchPath = "$PSScriptRoot\..\app\src\main\res"
Write-Host "==========================================================" -ForegroundColor Green
Write-Host " [HOT RELOAD ACTIVE] Watching: $watchPath" -ForegroundColor Cyan
Write-Host " Any file you save in res/ will instantly rebuild and launch on your phone!" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Green

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = (Resolve-Path $watchPath).Path
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true
$watcher.NotifyFilter = [System.IO.NotifyFilters]::LastWrite -bor [System.IO.NotifyFilters]::FileName

$lastRun = [DateTime]::MinValue

while ($true) {
    $change = $watcher.WaitForChanged([System.IO.WatcherChangeTypes]::Changed -bor [System.IO.WatcherChangeTypes]::Created, 1000)
    if ($change.TimedOut) {
        continue
    }

    $now = [DateTime]::Now
    # Debounce 1.5 seconds
    if (($now - $lastRun).TotalSeconds -lt 1.5) {
        continue
    }
    $lastRun = $now

    $fileName = $change.Name
    Write-Host "`n[$($now.ToString('HH:mm:ss'))] File changed: $fileName -> Rebuilding and reloading..." -ForegroundColor Cyan
    
    Set-Location "$PSScriptRoot\.."
    $buildOutput = & .\gradlew.bat installDebug 2>&1
    if ($LASTEXITCODE -eq 0) {
        & adb shell am start -n com.thesis.bananaleaf/.SplashActivity | Out-Null
        Write-Host "[$([DateTime]::Now.ToString('HH:mm:ss'))] RELOADED SUCCESSFULLY ON DEVICE!" -ForegroundColor Green
    } else {
        Write-Host "[$([DateTime]::Now.ToString('HH:mm:ss'))] Build failed. Check your XML syntax:" -ForegroundColor Red
        $buildOutput | Select-Object -Last 10 | ForEach-Object { Write-Host ("   " + $_.ToString()) -ForegroundColor DarkRed }
    }
}
