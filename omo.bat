@echo off
setlocal
title OmoAuto build
cd /d "%~dp0"
:menu
echo.
echo  ================================================
echo   OmoAuto  -  menu build
echo  ================================================
echo   1. Build APK debug
echo   2. Build + install debug lewat USB
echo   3. Build + install debug lewat Wi-Fi ADB
echo   4. Build APK release
echo   5. Build + install release lewat USB
echo   6. Build + install release lewat Wi-Fi ADB
echo   7. AAB release  (Play Store - produksi)
echo   8. AAB beta     (Play Store - pengujian)
echo   9. Jalankan unit test
echo   S. Buat kunci signing rilis
echo   C. Bersihkan build
echo   Q. Keluar
echo.
choice /c 123456789SCQ /n /m "Pilih: "
set "CH=%ERRORLEVEL%"
if "%CH%"=="1" call scripts\build-debug.bat
if "%CH%"=="2" call scripts\install-usb.bat debug
if "%CH%"=="3" call scripts\install-wifi.bat debug
if "%CH%"=="4" call scripts\build-release.bat
if "%CH%"=="5" call scripts\install-usb.bat release
if "%CH%"=="6" call scripts\install-wifi.bat release
if "%CH%"=="7" call scripts\bundle-release.bat
if "%CH%"=="8" call scripts\bundle-beta.bat
if "%CH%"=="9" call scripts\test.bat
if "%CH%"=="10" call scripts\signing-setup.bat
if "%CH%"=="11" call scripts\clean.bat
if "%CH%"=="12" exit /b 0
echo.
pause
goto :menu
