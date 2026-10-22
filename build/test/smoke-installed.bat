@echo off
rem Smoke-test: the same classpath the installed run.bat uses (jar + lib\*), in a clean folder.
cd /d "%~dp0"
set PSR=%~dp0..\..\
set SMOKE=%TEMP%\psr-smoke
if exist "%SMOKE%" rmdir /s /q "%SMOKE%"
mkdir "%SMOKE%"
cd /d "%SMOKE%"
java -cp "%PSR%build\dist\psr-service-center.jar;%PSR%build\dist\lib\*;%PSR%build\test\classes" psr.sc.ConflictE2E
echo EXIT=%ERRORLEVEL%
echo --- files created under the clean data dir ---
dir /s /b "%SMOKE%"
