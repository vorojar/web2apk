@echo off
chcp 65001 >nul
echo ========================================
echo   网页转APK生成器 - 环境安装
echo ========================================
echo.

cd /d "%~dp0"

echo 正在运行安装脚本...
python setup_env.py

echo.
echo 安装完成! 按任意键退出...
pause >nul
