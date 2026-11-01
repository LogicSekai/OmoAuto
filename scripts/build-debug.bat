@echo off
setlocal
rem Build a debug APK (package com.logicsekai.omoauto.debug) into dist\
call "%~dp0_env.bat" || exit /b 1
call "%~dp0_gradle.bat" :app:assembleDebug || goto :fail
if not exist "%OMO_ROOT%\dist" mkdir "%OMO_ROOT%\dist"
copy /y "%OMO_ROOT%\app\build\outputs\apk\debug\app-debug.apk" "%OMO_ROOT%\dist\OmoAuto-%OMO_VERSION%-debug.apk" >nul
echo.
echo [OmoAuto] Selesai: dist\OmoAuto-%OMO_VERSION%-debug.apk
exit /b 0
:fail
echo [OmoAuto] Build debug GAGAL.
exit /b 1
