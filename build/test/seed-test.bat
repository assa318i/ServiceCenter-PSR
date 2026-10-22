@echo off
rem Reads data\backup-dirs.txt written by the installer (ANSI) or Notepad (UTF-8/BOM).
rem Runs in an empty temp folder so the real project database is never touched.
setlocal
cd /d "%~dp0..\.."
set "PSR=%CD%"
set "SEED=%TEMP%\psr-seed"
if exist "%SEED%" rmdir /s /q "%SEED%"
mkdir "%SEED%"
javac --release 8 -encoding UTF-8 -cp "%PSR%\build\classes;%PSR%\lib\sqlite-jdbc.jar" -d "%PSR%\build\test\classes" "%PSR%\build\test\CopiesSeedE2E.java" || (echo COMPILE FAIL & exit /b 1)
cd /d "%SEED%"
java -cp "%PSR%\build\test\classes;%PSR%\build\classes;%PSR%\lib\*" psr.sc.CopiesSeedE2E > "%PSR%\build\test\seed.out" 2>&1
echo EXIT=%ERRORLEVEL%
exit /b %ERRORLEVEL%
