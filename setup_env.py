#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
环境自动安装脚本
自动下载并配置 JDK、Android SDK 和 Gradle Wrapper
"""

import os
import sys
import subprocess
import urllib.request
import zipfile
import tarfile
import shutil
from pathlib import Path

# 配置
BASE_DIR = Path(__file__).parent.absolute()
TOOLS_DIR = BASE_DIR / 'tools'
ANDROID_SDK_DIR = TOOLS_DIR / 'android-sdk'
JDK_DIR = TOOLS_DIR / 'jdk'

# 下载链接 (可能需要更新)
JDK_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.9_9.zip"
CMDLINE_TOOLS_URL = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
GRADLE_WRAPPER_URL = "https://github.com/AyraHikari/gradle-wrapper.jar/raw/main/gradle-wrapper.jar"


def print_step(step, message):
    """打印步骤信息"""
    print(f"\n{'='*60}")
    print(f"[步骤 {step}] {message}")
    print('='*60)


def download_file(url, dest_path, description="文件"):
    """下载文件，显示进度"""
    print(f"正在下载 {description}...")
    print(f"URL: {url}")

    try:
        def progress_hook(block_num, block_size, total_size):
            downloaded = block_num * block_size
            if total_size > 0:
                percent = min(100, downloaded * 100 / total_size)
                bar = '=' * int(percent // 2) + '>' + ' ' * (50 - int(percent // 2))
                print(f"\r[{bar}] {percent:.1f}%", end='', flush=True)

        urllib.request.urlretrieve(url, dest_path, progress_hook)
        print()  # 换行
        return True
    except Exception as e:
        print(f"\n下载失败: {e}")
        return False


def extract_zip(zip_path, dest_dir):
    """解压 ZIP 文件"""
    print(f"正在解压 {zip_path.name}...")
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(dest_dir)
    print("解压完成")


def check_java():
    """检查 Java 是否已安装"""
    try:
        result = subprocess.run(['java', '-version'], capture_output=True, text=True)
        if result.returncode == 0:
            version_line = result.stderr.split('\n')[0]
            print(f"检测到 Java: {version_line}")
            return True
    except:
        pass
    return False


def check_android_sdk():
    """检查 Android SDK"""
    android_home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if android_home and Path(android_home).exists():
        print(f"检测到 Android SDK: {android_home}")
        return True
    return False


def setup_jdk():
    """安装 JDK"""
    print_step(1, "设置 JDK")

    if check_java():
        print("Java 已安装，跳过...")
        return True

    print("未检测到 Java，准备下载 OpenJDK 17...")

    JDK_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = TOOLS_DIR / 'openjdk.zip'

    if not download_file(JDK_URL, zip_path, "OpenJDK 17"):
        print("JDK 下载失败！请手动安装 JDK 17")
        print("下载地址: https://adoptium.net/temurin/releases/")
        return False

    extract_zip(zip_path, JDK_DIR)
    zip_path.unlink()  # 删除压缩包

    # 找到实际的 JDK 目录
    jdk_folders = list(JDK_DIR.glob('jdk-*'))
    if jdk_folders:
        actual_jdk = jdk_folders[0]
        print(f"JDK 安装完成: {actual_jdk}")
        return actual_jdk

    return True


def setup_android_sdk():
    """安装 Android SDK 命令行工具"""
    print_step(2, "设置 Android SDK")

    if check_android_sdk():
        print("Android SDK 已配置，跳过...")
        return True

    print("未检测到 Android SDK，准备下载命令行工具...")

    ANDROID_SDK_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = TOOLS_DIR / 'cmdline-tools.zip'

    if not download_file(CMDLINE_TOOLS_URL, zip_path, "Android 命令行工具"):
        print("下载失败！请手动安装 Android Studio 或 SDK")
        print("下载地址: https://developer.android.com/studio")
        return False

    extract_zip(zip_path, ANDROID_SDK_DIR)
    zip_path.unlink()

    # 重新组织目录结构
    cmdline_src = ANDROID_SDK_DIR / 'cmdline-tools'
    cmdline_dest = ANDROID_SDK_DIR / 'cmdline-tools' / 'latest'

    if cmdline_src.exists() and not cmdline_dest.exists():
        temp_dir = ANDROID_SDK_DIR / 'temp_cmdline'
        shutil.move(cmdline_src, temp_dir)
        cmdline_tools_dir = ANDROID_SDK_DIR / 'cmdline-tools'
        cmdline_tools_dir.mkdir(exist_ok=True)
        shutil.move(temp_dir, cmdline_dest)

    print("Android 命令行工具安装完成")
    return True


def setup_gradle_wrapper():
    """下载 Gradle Wrapper JAR"""
    print_step(3, "设置 Gradle Wrapper")

    wrapper_dir = BASE_DIR / 'android-template' / 'gradle' / 'wrapper'
    wrapper_jar = wrapper_dir / 'gradle-wrapper.jar'

    if wrapper_jar.exists():
        print("gradle-wrapper.jar 已存在，跳过...")
        return True

    wrapper_dir.mkdir(parents=True, exist_ok=True)

    if not download_file(GRADLE_WRAPPER_URL, wrapper_jar, "Gradle Wrapper"):
        # 尝试备用方案
        print("尝试备用下载地址...")
        backup_url = "https://raw.githubusercontent.com/nicholasbishop/gradle-wrapper-jar/main/gradle-wrapper.jar"
        if not download_file(backup_url, wrapper_jar, "Gradle Wrapper (备用)"):
            print("下载失败！请手动下载 gradle-wrapper.jar")
            return False

    print("Gradle Wrapper 设置完成")
    return True


def accept_android_licenses():
    """接受 Android SDK 许可证"""
    print_step(4, "接受 Android SDK 许可证")

    sdkmanager = ANDROID_SDK_DIR / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'

    if not sdkmanager.exists():
        android_home = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
        if android_home:
            sdkmanager = Path(android_home) / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'

    if sdkmanager.exists():
        print("接受许可证...")
        try:
            # 创建一个输入 "y" 的进程
            process = subprocess.Popen(
                [str(sdkmanager), '--licenses'],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            # 发送多个 'y' 来接受所有许可
            process.communicate(input='y\n' * 20, timeout=60)
            print("许可证接受完成")
        except Exception as e:
            print(f"许可证接受过程出现问题: {e}")
    else:
        print("未找到 sdkmanager，跳过许可证步骤")


def install_android_build_tools():
    """安装 Android Build Tools"""
    print_step(5, "安装 Android 构建工具")

    android_home = os.environ.get('ANDROID_HOME') or str(ANDROID_SDK_DIR)
    sdkmanager = Path(android_home) / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'

    if not sdkmanager.exists():
        print("未找到 sdkmanager，跳过...")
        return

    packages = [
        'platform-tools',
        'build-tools;34.0.0',
        'platforms;android-34'
    ]

    for pkg in packages:
        print(f"安装 {pkg}...")
        try:
            subprocess.run(
                [str(sdkmanager), '--install', pkg],
                check=True,
                timeout=300
            )
        except Exception as e:
            print(f"安装 {pkg} 失败: {e}")


def create_env_script():
    """创建环境变量设置脚本"""
    print_step(6, "创建环境变量脚本")

    # 查找 JDK 路径
    jdk_path = None
    jdk_folders = list(JDK_DIR.glob('jdk-*'))
    if jdk_folders:
        jdk_path = jdk_folders[0]

    # Windows BAT 脚本
    bat_content = f'''@echo off
REM 设置 Android APK 生成器环境变量

'''
    if jdk_path:
        bat_content += f'''set JAVA_HOME={jdk_path}
set PATH=%JAVA_HOME%\\bin;%PATH%
'''

    bat_content += f'''set ANDROID_HOME={ANDROID_SDK_DIR}
set ANDROID_SDK_ROOT={ANDROID_SDK_DIR}
set PATH=%ANDROID_HOME%\\cmdline-tools\\latest\\bin;%ANDROID_HOME%\\platform-tools;%PATH%

echo 环境变量已设置
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%
'''

    env_bat = BASE_DIR / 'set_env.bat'
    env_bat.write_text(bat_content, encoding='utf-8')
    print(f"创建 {env_bat}")

    # 启动脚本
    start_bat = BASE_DIR / 'start.bat'
    start_content = f'''@echo off
cd /d "{BASE_DIR}"
call set_env.bat
python app.py
pause
'''
    start_bat.write_text(start_content, encoding='utf-8')
    print(f"创建 {start_bat}")


def install_python_deps():
    """安装 Python 依赖"""
    print_step(7, "安装 Python 依赖")

    requirements = BASE_DIR / 'requirements.txt'
    requirements.write_text('''flask>=2.0.0
Pillow>=9.0.0
''', encoding='utf-8')

    print("安装 Flask 和 Pillow...")
    subprocess.run([sys.executable, '-m', 'pip', 'install', '-r', str(requirements)])
    print("Python 依赖安装完成")


def main():
    """主函数"""
    print('''
╔══════════════════════════════════════════════════════════════╗
║           网页转APK生成器 - 环境自动安装脚本                 ║
╚══════════════════════════════════════════════════════════════╝
''')

    TOOLS_DIR.mkdir(exist_ok=True)

    # 安装 Python 依赖
    install_python_deps()

    # 设置 Gradle Wrapper
    setup_gradle_wrapper()

    # 设置 JDK
    jdk_result = setup_jdk()

    # 设置 Android SDK
    sdk_result = setup_android_sdk()

    if sdk_result and jdk_result:
        # 创建环境变量脚本
        create_env_script()

        # 接受许可证
        accept_android_licenses()

        # 安装构建工具
        install_android_build_tools()

    print('''
╔══════════════════════════════════════════════════════════════╗
║                       安装完成!                              ║
╠══════════════════════════════════════════════════════════════╣
║  使用方法:                                                   ║
║  1. 双击 start.bat 启动服务                                  ║
║  2. 浏览器访问 http://localhost:5000                         ║
║  3. 输入网址、上传图标、填写应用名，点击生成                 ║
╚══════════════════════════════════════════════════════════════╝
''')

    if not jdk_result:
        print("警告: JDK 安装可能未完成，请手动安装 JDK 17")

    if not sdk_result:
        print("警告: Android SDK 安装可能未完成，请手动配置")


if __name__ == '__main__':
    main()
