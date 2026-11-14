@echo off
REM 打 release 包（正式签名），用于分发前验证
setlocal
set JAVA_HOME=O:\dev\tools\android\jdk-17.0.20.1+1
set ANDROID_HOME=O:\dev\tools\android\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set GRADLE_USER_HOME=O:\dev\tools\gradle-home
set TMP=O:\dev\tools\tmp
set TEMP=O:\dev\tools\tmp
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d O:\dev\enroute-android
echo === assembleRelease === > build-release-log.txt
call O:\dev\tools\android\gradle-8.11.1\bin\gradle.bat :app:assembleRelease --no-daemon --console=plain >> build-release-log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build-release-log.txt
endlocal
