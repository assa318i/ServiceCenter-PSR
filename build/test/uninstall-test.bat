@echo off
rem Removes the silent test install made by install-test.bat (keeps data\ if it was filled).
setlocal
set "TARGET=%TEMP%\psr-inst-test"
if not exist "%TARGET%\unins000.exe" (echo NOT_INSTALLED & exit /b 0)
start "" /wait "%TARGET%\unins000.exe" /VERYSILENT /SUPPRESSMSGBOXES /NOCANCEL
echo EXIT=%ERRORLEVEL%
if exist "%TARGET%" (echo DATA_KEPT_IN=%TARGET%) else (echo FULLY_REMOVED)
exit /b %ERRORLEVEL%
