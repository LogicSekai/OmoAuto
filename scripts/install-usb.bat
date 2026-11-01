@echo off
setlocal
rem Build and install OmoAuto on a phone connected by USB, then launch it.
rem Usage: install-usb.bat [debug^|release]     (default: debug)
set "VARIANT=%~1"
if "%VARIANT%"=="" set "VARIANT=debug"
call "%~dp0_env.bat" || exit /b 1
if not defined ADB goto :no_adb
call "%~dp0_variant.bat" %VARIANT% || exit /b 1

"%ADB%" -d get-state >nul 2>&1
if errorlevel 1 goto :no_device

call "%~dp0_gradle.bat" %OMO_TASK% || goto :build_failed
call "%~dp0_install.bat" -d || exit /b 1
exit /b 0

:no_adb
echo [OmoAuto] adb tidak ditemukan. Install "Android SDK Platform-Tools" lewat Android Studio SDK Manager.
exit /b 1
:no_device
echo [OmoAuto] Tidak ada HP yang terhubung lewat USB (atau lebih dari satu).
echo           Aktifkan Opsi developer - USB debugging, sambungkan kabel, lalu izinkan komputer ini di HP.
"%ADB%" devices
exit /b 1
:build_failed
echo [OmoAuto] Build GAGAL.
exit /b 1
