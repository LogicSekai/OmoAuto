@echo off
setlocal
rem Build the Android App Bundle (AAB) for beta testing (Play internal/closed/open testing).
rem Same package name as release; versionName gets a "-beta" suffix.
call "%~dp0_env.bat" || exit /b 1
if not exist "%OMO_ROOT%\keystore.properties" (
  echo [OmoAuto] keystore.properties belum ada. Jalankan scripts\signing-setup.bat terlebih dahulu.
  exit /b 1
)
call "%~dp0_gradle.bat" :app:bundleBeta -Pomo.distribution=play || goto :fail
if not exist "%OMO_ROOT%\dist" mkdir "%OMO_ROOT%\dist"
copy /y "%OMO_ROOT%\app\build\outputs\bundle\beta\app-beta.aab" "%OMO_ROOT%\dist\OmoAuto-%OMO_VERSION%-%OMO_VERSION_CODE%-beta.aab" >nul
echo.
echo [OmoAuto] Selesai: dist\OmoAuto-%OMO_VERSION%-%OMO_VERSION_CODE%-beta.aab
echo           Upload ke Play Console - Pengujian (internal/tertutup/terbuka).
exit /b 0
:fail
echo [OmoAuto] Bundle beta GAGAL.
exit /b 1
