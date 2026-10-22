# Проверяет, что установленный run.bat реально запускает программу.
# Снимает только тот javaw, который подняла сама (свои процессы не трогает).
$ErrorActionPreference = 'SilentlyContinue'
$app = Join-Path $env:TEMP 'psr-inst-test'
if (-not (Test-Path (Join-Path $app 'run.bat'))) { Write-Output 'NOT_INSTALLED'; exit 1 }
$before = (Get-Process javaw).Id
Start-Process -FilePath (Join-Path $app 'run.bat') -WorkingDirectory $app -WindowStyle Hidden
Start-Sleep -Seconds 12
$after = (Get-Process javaw).Id
$new = @($after | Where-Object { $before -notcontains $_ })
$db = Test-Path (Join-Path $app 'data\psr.db')
foreach ($p in $new) { Stop-Process -Id $p -Force }
Write-Output ("PROCESSES=" + $new.Count + " DB_CREATED=" + $db)
if ($new.Count -ge 1 -and $db) { exit 0 } else { exit 1 }
