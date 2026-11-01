@echo off
rem Installs %OMO_APK% and launches the app. Args: adb device selector (-d  or  -s ip:port)
set "SEL=%*"
echo [OmoAuto] Memasang %OMO_PKG% ...
"%ADB%" %SEL% install -r "%OMO_APK%" > "%TEMP%\omoauto_install.txt" 2>&1
type "%TEMP%\omoauto_install.txt"
findstr /c:"Success" "%TEMP%\omoauto_install.txt" >nul && goto :launch
findstr /c:"INSTALL_FAILED_UPDATE_INCOMPATIBLE" /c:"signatures do not match" "%TEMP%\omoauto_install.txt" >nul || goto :failed
echo.
echo [OmoAuto] Versi di HP ditandatangani kunci lain. Uninstall dulu? (data aplikasi akan terhapus)
choice /c YN /n /m "Uninstall lalu pasang ulang [Y/N]? "
if errorlevel 2 goto :failed
"%ADB%" %SEL% uninstall %OMO_PKG%
"%ADB%" %SEL% install -r "%OMO_APK%" || goto :failed

:launch
"%ADB%" %SEL% shell am start -n %OMO_PKG%/com.logicsekai.omoauto.MainActivity >nul
echo [OmoAuto] Terpasang dan dijalankan.
exit /b 0
:failed
echo [OmoAuto] Pemasangan GAGAL.
exit /b 1
