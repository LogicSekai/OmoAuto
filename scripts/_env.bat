@echo off
rem ============================================================================
rem  OmoAuto - shared environment setup (called by the other scripts)
rem  Finds JDK 17+, the Android SDK and adb, and writes local.properties.
rem  Sets: OMO_ROOT, JAVA_HOME, ANDROID_SDK, ADB, OMO_VERSION, OMO_VERSION_CODE
rem ============================================================================
for %%I in ("%~dp0..") do set "OMO_ROOT=%%~fI"

rem ---------- JDK 17..23 ----------
rem Gradle 8.11 runs on JDK 17-23. Newer JDKs (e.g. 26) are skipped automatically; set
rem OMO_JAVA_HOME to force a specific JDK.
set "OMO_MIN_JDK=17"
set "OMO_MAX_JDK=23"
set "OMO_JDK="
set "OMO_TOO_NEW="
if defined OMO_JAVA_HOME call :try_jdk "%OMO_JAVA_HOME%"
if not defined OMO_JDK if defined JAVA_HOME call :try_jdk "%JAVA_HOME%"
if not defined OMO_JDK call :try_jdk "%ProgramFiles%\Android\Android Studio\jbr"
if not defined OMO_JDK call :try_jdk "%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not defined OMO_JDK call :scan_dir "%ProgramFiles%\Eclipse Adoptium"
if not defined OMO_JDK call :scan_dir "%ProgramFiles%\Java"
if not defined OMO_JDK call :scan_dir "%ProgramFiles%\Microsoft"
if not defined OMO_JDK call :scan_dir "%ProgramFiles%\Zulu"
if not defined OMO_JDK call :scan_dir "%ProgramFiles%\BellSoft"
if not defined OMO_JDK call :scan_dir "%USERPROFILE%\.jdks"
if not defined OMO_JDK call :scan_dir "%USERPROFILE%\.gradle\jdks"
if not defined OMO_JDK call :try_java_on_path
if not defined OMO_JDK goto :no_jdk
set "JAVA_HOME=%OMO_JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ---------- Android SDK ----------
set "ANDROID_SDK="
call :read_local_sdk
if not defined ANDROID_SDK if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools" set "ANDROID_SDK=%ANDROID_HOME%"
if not defined ANDROID_SDK if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools" set "ANDROID_SDK=%ANDROID_SDK_ROOT%"
if not defined ANDROID_SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_SDK (
  echo [OmoAuto] Android SDK tidak ditemukan.
  echo           Install lewat Android Studio, atau set ANDROID_HOME ke folder SDK.
  exit /b 1
)
set "ANDROID_HOME=%ANDROID_SDK%"
call :write_local_properties

rem ---------- adb ----------
set "ADB="
if exist "%ANDROID_SDK%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK%\platform-tools\adb.exe"
if not defined ADB for /f "delims=" %%A in ('where adb 2^>nul') do if not defined ADB set "ADB=%%A"

rem ---------- Version from gradle.properties ----------
set "OMO_VERSION=0.0.0"
set "OMO_VERSION_CODE=0"
for /f "usebackq tokens=1,* delims==" %%A in ("%OMO_ROOT%\gradle.properties") do (
  if "%%A"=="omo.versionName" set "OMO_VERSION=%%B"
  if "%%A"=="omo.versionCode" set "OMO_VERSION_CODE=%%B"
)

echo [OmoAuto] JDK : %JAVA_HOME% (Java %JAVA_MAJOR%)
echo [OmoAuto] SDK : %ANDROID_SDK%
if defined ADB echo [OmoAuto] adb : %ADB%
if not defined ADB echo [OmoAuto] adb : tidak ditemukan - install Android SDK Platform-Tools
echo [OmoAuto] Versi %OMO_VERSION% (%OMO_VERSION_CODE%)
exit /b 0

rem ============================================================================
:no_jdk
if defined OMO_TOO_NEW (
  echo [OmoAuto] Hanya ditemukan JDK yang terlalu baru: %OMO_TOO_NEW%
  echo           Gradle 8.11 butuh JDK 17 sampai 23. Install Temurin 17 atau 21, misalnya:
  echo           winget install EclipseAdoptium.Temurin.21.JDK
) else (
  echo [OmoAuto] JDK 17-23 tidak ditemukan. Install Android Studio atau Temurin JDK 17/21.
)
echo           Atau set OMO_JAVA_HOME ke folder JDK yang ingin dipakai.
exit /b 1

:scan_dir
if not exist "%~1" exit /b 0
for /d %%D in ("%~1\*") do (
  if not defined OMO_JDK call :try_jdk "%%~fD"
)
exit /b 0

:try_jdk
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\bin\java.exe" exit /b 0
call :java_major "%CANDIDATE%\bin\java.exe"
if %JAVA_MAJOR% LSS %OMO_MIN_JDK% exit /b 0
if %JAVA_MAJOR% GTR %OMO_MAX_JDK% (
  set "OMO_TOO_NEW=%CANDIDATE%"
  exit /b 0
)
set "OMO_JDK=%CANDIDATE%"
exit /b 0

:try_java_on_path
for /f "delims=" %%J in ('where java 2^>nul') do (
  if not defined OMO_JDK call :try_java_exe "%%J"
)
exit /b 0

:try_java_exe
rem Resolve the JDK folder of a java.exe on PATH; launcher shims (Oracle "javapath") are skipped.
for %%D in ("%~dp1..") do set "JAVA_DIR=%%~fD"
call :try_jdk "%JAVA_DIR%"
exit /b 0

:java_major
rem Prints java.specification.version (8 -> "1.8", 17 -> "17") via a temp file to avoid quoting issues.
set "JAVA_MAJOR=0"
set "JAVA_SPEC=0"
set "OMO_PROPS=%TEMP%\omoauto_java_props.txt"
"%~1" -XshowSettings:properties -version 2>"%OMO_PROPS%" >nul
for /f "tokens=2 delims==" %%V in ('findstr /c:"java.specification.version" "%OMO_PROPS%"') do set "JAVA_SPEC=%%V"
del "%OMO_PROPS%" >nul 2>&1
set "JAVA_SPEC=%JAVA_SPEC: =%"
for /f "tokens=1 delims=." %%M in ("%JAVA_SPEC%") do set "JAVA_MAJOR=%%M"
exit /b 0

:read_local_sdk
set "LOCAL_SDK="
if not exist "%OMO_ROOT%\local.properties" exit /b 0
for /f "usebackq tokens=1,* delims==" %%A in ("%OMO_ROOT%\local.properties") do (
  if "%%A"=="sdk.dir" set "LOCAL_SDK=%%B"
)
if not defined LOCAL_SDK exit /b 0
set "LOCAL_SDK=%LOCAL_SDK:\\=\%"
set "LOCAL_SDK=%LOCAL_SDK:\:=:%"
if exist "%LOCAL_SDK%" set "ANDROID_SDK=%LOCAL_SDK%"
exit /b 0

:write_local_properties
if exist "%OMO_ROOT%\local.properties" findstr /b /c:"sdk.dir=" "%OMO_ROOT%\local.properties" >nul && exit /b 0
set "ESCAPED=%ANDROID_SDK:\=\\%"
set "ESCAPED=%ESCAPED::=\:%"
>>"%OMO_ROOT%\local.properties" echo sdk.dir=%ESCAPED%
echo [OmoAuto] local.properties dibuat (sdk.dir)
exit /b 0
