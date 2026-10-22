@echo off
rem Запуск "Сервис центр от PSR" (нужна Java 8 или новее)
cd /d %~dp0
start javaw -cp "build\psr-service-center.jar;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar;lib\javax-mail.jar;lib\javax-activation.jar;lib\zxing-core.jar" psr.sc.App
