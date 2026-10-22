@echo off
rem Silent test install of the built setup into %TEMP%\psr-inst-test (ASCII only, CRLF).
setlocal
set "TARGET=%TEMP%\psr-inst-test"
set "SETUP=%~dp0..\dist\PSR-ServiceCenter-Setup-1.0.0.exe"
if exist "%TARGET%" rmdir /s /q "%TARGET%"
start "" /wait "%SETUP%" /VERYSILENT /SUPPRESSMSGBOXES /NOCANCEL /NORESTART /DIR="%TARGET%" /LOG="%TEMP%\psr-inst.log"
echo EXIT=%ERRORLEVEL%
if exist "%TARGET%" (echo INSTALLED) else (echo NOT_INSTALLED)
exit /b %ERRORLEVEL%
