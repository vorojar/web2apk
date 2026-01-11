@echo off
chcp 65001 >nul
cd /d "%~dp0"

REM 设置 JDK 环境变量（直接使用绝对路径）
set "JAVA_HOME=%~dp0tools\jdk\jdk-17.0.17+10"
set "PATH=%JAVA_HOME%\bin;%PATH%"

REM 设置 Android SDK 环境变量
set "ANDROID_HOME=%~dp0tools\android-sdk"
set "ANDROID_SDK_ROOT=%~dp0tools\android-sdk"
set "PATH=%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo ========================================
echo   网页转APK生成器
echo ========================================
echo.
echo 正在启动服务...
echo 请访问: http://localhost:5000
echo.
echo 按 Ctrl+C 停止服务
echo ========================================
echo.

python app.py

pause
