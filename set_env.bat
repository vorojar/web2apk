@echo off
REM 设置 Android APK 生成器环境变量

set JAVA_HOME=D:\mycode\apk\tools\jdk\jdk-17.0.17+10
set PATH=%JAVA_HOME%\bin;%PATH%

set ANDROID_HOME=D:\mycode\apk\tools\android-sdk
set ANDROID_SDK_ROOT=D:\mycode\apk\tools\android-sdk
set PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\build-tools\34.0.0;%PATH%

echo 环境变量已设置
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
