@echo off
REM Round 8: home dashboard (profile / credit progress / score stats / free rooms / teacher search)
setlocal
set JAVA_HOME=O:\dev\tools\android\jdk-17.0.20.1+1
set ANDROID_HOME=O:\dev\tools\android\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set GRADLE_USER_HOME=O:\dev\tools\gradle-home
set TMP=O:\dev\tools\tmp
set TEMP=O:\dev\tools\tmp
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d O:\dev\enroute-android
del /q build-done.txt 2>nul
echo === gradle test + assembleDebug (round 8) === > build-log.txt
call O:\dev\tools\android\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain >> build-log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build-log.txt
echo DONE > build-done.txt
endlocal
