@echo off
setlocal
rem Runs all JVM unit tests (protocol modules + streaming logic).
call "%~dp0_env.bat" || exit /b 1
call "%~dp0_gradle.bat" :core:common:test :core:protocol-aa:test :core:protocol-omoway:test :core:data:testDebugUnitTest :core:streaming:testDebugUnitTest || (
  echo [OmoAuto] Ada tes yang GAGAL. Laporan: core\*\build\reports\tests\
  exit /b 1
)
echo [OmoAuto] Semua tes lolos.
exit /b 0
