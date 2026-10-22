@echo off
rem Прогон всех тестов программы в одном окне (вывод в build\test\all.out — кириллица в консоли искажается).
setlocal
cd /d "%~dp0..\.."
set CP=build\classes;build\test\classes;lib\sqlite-jdbc.jar;lib\slf4j-api.jar;lib\slf4j-nop.jar;lib\javax-mail.jar;lib\javax-activation.jar;lib\zxing-core.jar
javac --release 8 -encoding UTF-8 -cp "%CP%" -d build\test\classes ^
  build\test\FilterE2E.java build\test\PresetE2E.java build\test\TasksE2E.java ^
  build\test\StreamE2E.java build\test\ConflictE2E.java build\test\PagTest.java ^
  build\test\FilterTest.java build\test\RolesCheck.java build\test\FormatsCheck.java ^
  build\test\UsersCheck.java build\test\CalendarCheck.java ^
  || (echo BUILD FAIL > build\test\all.out & exit /b 1)
if exist build\test\all.out del build\test\all.out
for %%T in (psr.sc.FilterE2E psr.sc.PresetE2E psr.sc.TasksE2E psr.sc.StreamE2E psr.sc.ConflictE2E psr.sc.PagTest FilterTest psr.sc.RolesCheck FormatsCheck) do (
  echo ===== %%T >> build\test\all.out
  java -cp "%CP%" %%T >> build\test\all.out 2>&1 || echo FAILED %%T >> build\test\all.out
)
rem Сотрудники и табель — каждый на своей базе: проверки пишут себе строки и не должны
rem трогать рабочих людей из data\psr.db.
echo ===== psr.sc.UsersCheck >> build\test\all.out
java -Dpsr.data=build\test\tmp-users -cp "%CP%" psr.sc.UsersCheck >> build\test\all.out 2>&1 || echo FAILED USERS >> build\test\all.out
echo ===== psr.sc.CalendarCheck >> build\test\all.out
java -Dpsr.data=build\test\tmp-cal -cp "%CP%" psr.sc.CalendarCheck >> build\test\all.out 2>&1 || echo FAILED CAL >> build\test\all.out
rem find.exe с путём: в PATH может лежать GNU find из git, а для него "/c" — это начало
rem обхода всего диска C. Маркер FAILED тоже ASCII: в файл попадают байты разной кодировки.
%SystemRoot%\System32\find.exe /c "FAIL" build\test\all.out
exit /b 0
