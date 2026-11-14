@echo off
REM 统一验证构建：单测 + release 包（一次跑完，避免两次构建抢锁）
setlocal
set JAVA_HOME=O:\dev\tools\android\jdk-17.0.20.1+1
set ANDROID_HOME=O:\dev\tools\android\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set GRADLE_USER_HOME=O:\dev\tools\gradle-home
set TMP=O:\dev\tools\tmp
set TEMP=O:\dev\tools\tmp
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d O:\dev\enroute-android
del /q build-verify-done.txt 2>nul
echo === verify build: testDebugUnitTest + assembleRelease === > build-verify-log.txt
call O:\dev\tools\android\gradle-8.11.1\bin\gradle.bat :app:testDebugUnitTest :app:assembleRelease --no-daemon --console=plain >> build-verify-log.txt 2>&1
echo EXIT=%ERRORLEVEL% >> build-verify-log.txt
echo DONE > build-verify-done.txt
endlocal
