@echo off
rem PSR Service Center launcher (installed layout).
rem Uses the bundled "jre" folder when present, otherwise Java from PATH.
rem Data lives in the "data" folder next to this file.
cd /d "%~dp0"
set JAVA=javaw
if exist "%~dp0jre\bin\javaw.exe" set JAVA=%~dp0jre\bin\javaw.exe
start "PSR Service Center" "%JAVA%" -cp "psr-service-center.jar;lib\*" psr.sc.App
