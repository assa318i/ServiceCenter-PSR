@echo off
rem Build the Windows installer for "PSR Service Center" (Inno Setup 6).
rem   installer\make-installer.bat                      - no bundled Java (PC must have Java 8+)
rem   installer\make-installer.bat "C:\path\to\jre8"    - bundle your own JRE (folder with bin\javaw.exe)
rem   set PSR_VERSION=1.1.0 && installer\make-installer.bat   - version used in setup file name
rem Keep this file ASCII-only: cmd.exe mis-parses batches that switch codepage mid-run.
setlocal
cd /d "%~dp0"

echo [1/4] program jar
call ..\build.bat || goto :err
rem build.bat does its own "cd /d", so the working folder must be restored here
cd /d "%~dp0"

echo [2/4] dist folder: jar, lib, run.bat, documentation as HTML
node ..\tools\prepare-dist.js || goto :err

echo [3/4] bundled Java
if "%~1"=="" (
  echo       skipped: component "jre" is packed only if build\dist\jre exists
  goto :iscc
)
if not exist "%~1\bin\javaw.exe" goto :badjre
if exist ..\build\dist\jre rmdir /s /q ..\build\dist\jre
xcopy /e /i /y "%~1" ..\build\dist\jre >nul || goto :err
echo       packing into the installer: %~1

:iscc
echo [4/4] Inno Setup compiler
set "ISCC="
if exist "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%ProgramFiles%\Inno Setup 6\ISCC.exe" set "ISCC=%ProgramFiles%\Inno Setup 6\ISCC.exe"
if not defined ISCC if exist "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not defined ISCC (
  for /f "delims=" %%I in ('where iscc 2^>nul') do if not defined ISCC set "ISCC=%%I"
)
if defined ISCC goto :compile

echo.
echo Inno Setup 6 not found - installer\setup.iss is ready but there is nothing to compile it with.
echo Install it:  winget install --id JRSoftware.InnoSetup --silent
echo   or download from https://jrsoftware.org/isdl.php , install, then run this file again.
echo build\dist already contains the portable layout and HTML docs - checkable without an installer.
exit /b 3

:compile
set "VDEF="
if defined PSR_VERSION set "VDEF=/DMyAppVersion=%PSR_VERSION%"
echo       %ISCC%
"%ISCC%" %VDEF% /Qp setup.iss || goto :err
echo.
echo DONE: build\dist\PSR-ServiceCenter-Setup-*.exe
echo On another PC: copy that single file and run it - no admin rights required.
echo The "Finished" page offers the checklist, the docs and starting the program.
goto :eof

:badjre
echo [ERROR] "%~1" has no bin\javaw.exe - give the JRE root folder (the one with bin\java.exe).
exit /b 1

:err
echo BUILD ABORTED
exit /b 1
