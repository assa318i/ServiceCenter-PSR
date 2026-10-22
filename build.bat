@echo off
rem Сборка "Сервис центр от PSR" — jar для Windows 7/10/11 (нужна Java 8+)
cd /d %~dp0
if not exist build mkdir build
if not exist build\classes\fonts mkdir build\classes\fonts
del build\sources.txt 2>nul
for %%f in (src\psr\sc\*.java) do echo %%f>> build\sources.txt
javac --release 8 -encoding UTF-8 -cp "lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\javax-mail.jar;lib\javax-activation.jar;lib\zxing-core.jar" -d build\classes @build\sources.txt || goto :err
copy /y resources\fonts\*.ttf build\classes\fonts\ >nul
jar cfe build\psr-service-center.jar psr.sc.App -C build\classes .
if errorlevel 1 goto :err
echo.
echo ГОТОВО: build\psr-service-center.jar
echo Запуск: java -cp "build\psr-service-center.jar;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar;lib\javax-mail.jar;lib\javax-activation.jar;lib\zxing-core.jar" psr.sc.App
goto :eof
:err
echo ОШИБКА СБОРКИ
exit /b 1
