@echo off
rem Runs the Gradle wrapper from the project root with the detected JDK.
rem Usage: call "%~dp0_gradle.bat" <tasks...>
pushd "%OMO_ROOT%"
call gradlew.bat %*
set "GRADLE_EXIT=%ERRORLEVEL%"
popd
exit /b %GRADLE_EXIT%
