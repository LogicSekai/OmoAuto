@echo off
setlocal EnableExtensions
rem Build and install OmoAuto over Wi-Fi ADB, then launch it.
rem Usage: install-wifi.bat [ip:port] [debug^|release]
rem   Without ip:port a menu offers: connect, pair (Android 11+), or enable via USB (tcpip 5555).
set "TARGET=%~1"
set "VARIANT=%~2"
if /i "%TARGET%"=="debug" (set "VARIANT=debug" & set "TARGET=")
if /i "%TARGET%"=="release" (set "VARIANT=release" & set "TARGET=")
if "%VARIANT%"=="" set "VARIANT=debug"
call "%~dp0_env.bat" || exit /b 1
if not defined ADB (
  echo [OmoAuto] adb tidak ditemukan. Install Android SDK Platform-Tools.
  exit /b 1
)
call "%~dp0_variant.bat" %VARIANT% || exit /b 1

set "LAST_FILE=%~dp0.wifi-device"
set "LAST="
if exist "%LAST_FILE%" set /p LAST=<"%LAST_FILE%"

if not defined TARGET call :choose_target
if not defined TARGET (
  echo [OmoAuto] Tidak ada perangkat dipilih.
  exit /b 1
)

echo [OmoAuto] Menyambung ke %TARGET% ...
"%ADB%" connect %TARGET% | findstr /i /c:"connected to" >nul
if errorlevel 1 (
  echo [OmoAuto] Gagal tersambung ke %TARGET%. Pastikan HP dan komputer di Wi-Fi yang sama dan Wireless debugging aktif.
  exit /b 1
)
>"%LAST_FILE%" echo %TARGET%

call "%~dp0_gradle.bat" %OMO_TASK% || (
  echo [OmoAuto] Build GAGAL.
  exit /b 1
)
call "%~dp0_install.bat" -s %TARGET% || exit /b 1
exit /b 0

rem ----------------------------------------------------------------------------
:choose_target
echo.
echo  ADB lewat Wi-Fi
echo  -----------------------------------------------
if defined LAST echo  1. Sambungkan ke perangkat terakhir (%LAST%)
if not defined LAST echo  1. Sambungkan (IP:port dari Wireless debugging)
echo  2. Pair perangkat baru (Android 11+, pakai kode pairing)
echo  3. Aktifkan ADB Wi-Fi lewat kabel USB (Android 10 ke bawah)
echo.
choice /c 123 /n /m "Pilih [1-3]: "
set "CH=%ERRORLEVEL%"
if "%CH%"=="1" goto :opt_connect
if "%CH%"=="2" goto :opt_pair
if "%CH%"=="3" goto :opt_usb
exit /b 0

:opt_connect
if defined LAST (
  set "TARGET=%LAST%"
  exit /b 0
)
set /p TARGET=IP:port (lihat Opsi developer - Wireless debugging): 
exit /b 0

:opt_pair
echo Di HP: Opsi developer - Wireless debugging - Pair device with pairing code.
set /p PAIR_ADDR=IP:port PAIRING (yang tampil di dialog pairing): 
set /p PAIR_CODE=Kode pairing 6 digit: 
"%ADB%" pair %PAIR_ADDR% %PAIR_CODE%
if errorlevel 1 (
  echo [OmoAuto] Pairing gagal.
  exit /b 0
)
set /p TARGET=IP:port koneksi (di layar Wireless debugging, BUKAN port pairing): 
exit /b 0

:opt_usb
"%ADB%" -d get-state >nul 2>&1
if errorlevel 1 (
  echo [OmoAuto] Sambungkan HP lewat USB dulu dengan USB debugging aktif.
  exit /b 0
)
"%ADB%" -d tcpip 5555
timeout /t 2 /nobreak >nul
set /p PHONE_IP=IP HP di Wi-Fi (Pengaturan - Tentang ponsel - Status): 
set "TARGET=%PHONE_IP%:5555"
echo Sekarang kabel USB boleh dilepas.
exit /b 0
