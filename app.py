#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
网页转APK生成器 - 后端服务
"""

import os
import sys
import json
import shutil
import subprocess
import uuid
import re
import zipfile
import secrets
import string
from pathlib import Path
from flask import Flask, render_template, request, send_file, Response
from PIL import Image
import time

app = Flask(__name__)

# 配置
BASE_DIR = Path(__file__).parent.absolute()
TEMPLATE_DIR = BASE_DIR / 'android-template'
OUTPUT_DIR = BASE_DIR / 'output'
UPLOAD_DIR = BASE_DIR / 'uploads'

# 确保目录存在
OUTPUT_DIR.mkdir(exist_ok=True)
UPLOAD_DIR.mkdir(exist_ok=True)

# 图标尺寸配置
ICON_SIZES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}


def stream_response(generator):
    """SSE 流式响应"""
    def generate():
        for data in generator:
            yield f"data: {json.dumps(data, ensure_ascii=False)}\n\n"
    return Response(generate(), mimetype='text/event-stream')


def send_progress(message, percent):
    """发送进度消息"""
    return {'type': 'progress', 'message': message, 'percent': percent}


def send_done(message):
    """发送完成消息"""
    return {'type': 'done', 'message': message}


def send_error(message):
    """发送错误消息"""
    return {'type': 'error', 'message': message}


def send_success(filename):
    """发送成功消息"""
    return {'type': 'success', 'filename': filename}


def validate_package_name(package_name):
    """验证包名格式"""
    pattern = r'^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$'
    return bool(re.match(pattern, package_name.lower()))


def generate_password(length=16):
    """生成随机密码"""
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for _ in range(length))


def generate_keystore(keystore_path, app_name, store_password, key_password, key_alias='key0'):
    """使用 keytool 生成签名证书"""
    # 查找 keytool
    tools_dir = BASE_DIR / 'tools'
    jdk_dirs = list((tools_dir / 'jdk').glob('jdk-*'))

    if jdk_dirs:
        keytool = jdk_dirs[0] / 'bin' / ('keytool.exe' if sys.platform == 'win32' else 'keytool')
    else:
        keytool = 'keytool'  # 使用系统 PATH 中的 keytool

    # 生成证书
    cmd = [
        str(keytool),
        '-genkeypair',
        '-keystore', str(keystore_path),
        '-alias', key_alias,
        '-keyalg', 'RSA',
        '-keysize', '2048',
        '-validity', '36500',  # 100年有效期
        '-storepass', store_password,
        '-keypass', key_password,
        '-dname', f'CN={app_name}, OU=App, O=App, L=City, ST=State, C=CN',
        '-storetype', 'PKCS12'
    ]

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise Exception(f'生成证书失败: {result.stderr}')

    return True


def process_icon(icon_path, build_dir):
    """处理图标，生成各种尺寸"""
    try:
        img = Image.open(icon_path)
        # 转换为 RGBA
        if img.mode != 'RGBA':
            img = img.convert('RGBA')

        for folder, size in ICON_SIZES.items():
            target_dir = build_dir / 'app' / 'src' / 'main' / 'res' / folder
            target_dir.mkdir(parents=True, exist_ok=True)

            resized = img.resize((size, size), Image.Resampling.LANCZOS)
            resized.save(target_dir / 'ic_launcher.png', 'PNG')

            # 圆形图标
            resized.save(target_dir / 'ic_launcher_round.png', 'PNG')

        return True
    except Exception as e:
        return str(e)


def build_apk(app_name, package_name, url, icon_path, existing_keystore=None, screen_orientation='unspecified', fullscreen=False, splash_color='#f8f9fa', version_name='1.0', status_bar_color='#000000'):
    """构建 APK 的生成器函数

    existing_keystore: 可选，用户上传的已有证书信息
        {
            'path': keystore文件路径,
            'store_password': 证书密码,
            'key_alias': 密钥别名,
            'key_password': 密钥密码
        }
    screen_orientation: 屏幕方向 ('unspecified', 'portrait', 'landscape')
    fullscreen: 是否全屏沉浸式模式
    splash_color: 启动画面背景色 (十六进制颜色值)
    version_name: 版本号 (显示给用户)
    status_bar_color: 状态栏颜色 (十六进制颜色值)
    """
    build_id = str(uuid.uuid4())[:8]
    build_dir = OUTPUT_DIR / f'build_{build_id}'

    # 判断是使用已有证书还是生成新证书
    use_existing = existing_keystore is not None
    if use_existing:
        store_password = existing_keystore['store_password']
        key_password = existing_keystore['key_password']
        key_alias = existing_keystore['key_alias']
    else:
        store_password = generate_password()
        key_password = store_password  # 使用相同密码简化用户操作
        key_alias = 'key0'

    try:
        # 步骤 1: 验证参数
        yield send_progress('验证参数...', 5)
        time.sleep(0.3)

        if not validate_package_name(package_name):
            yield send_error(f'包名格式不正确: {package_name}')
            return

        yield send_done('参数验证通过')

        # 步骤 2: 复制模板项目
        yield send_progress('复制 Android 模板项目...', 10)
        time.sleep(0.3)

        if build_dir.exists():
            shutil.rmtree(build_dir)
        shutil.copytree(TEMPLATE_DIR, build_dir)

        yield send_done('模板项目复制完成')

        # 步骤 3: 处理图标
        yield send_progress('处理应用图标...', 15)
        time.sleep(0.3)

        icon_result = process_icon(icon_path, build_dir)
        if icon_result is not True:
            yield send_error(f'图标处理失败: {icon_result}')
            return

        yield send_done('图标处理完成')

        # 步骤 4: 处理签名证书
        keystore_path = build_dir / 'release.keystore'

        if use_existing:
            yield send_progress('使用已有证书...', 20)
            time.sleep(0.3)
            # 复制用户上传的证书
            shutil.copy(existing_keystore['path'], keystore_path)
            yield send_done('证书已加载')
        else:
            yield send_progress('生成签名证书...', 20)
            time.sleep(0.3)
            try:
                generate_keystore(keystore_path, app_name, store_password, key_password, key_alias)
            except Exception as e:
                yield send_error(f'生成证书失败: {str(e)}')
                return
            yield send_done('签名证书生成完成')

        # 步骤 5: 修改配置文件
        yield send_progress('修改应用配置...', 30)
        time.sleep(0.3)

        # 修改 strings.xml
        strings_path = build_dir / 'app' / 'src' / 'main' / 'res' / 'values' / 'strings.xml'
        strings_content = f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">{app_name}</string>
    <string name="web_url">{url}</string>
</resources>
'''
        strings_path.write_text(strings_content, encoding='utf-8')

        # 修改 colors.xml (启动画面背景色 + 状态栏颜色)
        colors_path = build_dir / 'app' / 'src' / 'main' / 'res' / 'values' / 'colors.xml'
        colors_content = f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_background">{splash_color}</color>
    <color name="status_bar_color">{status_bar_color}</color>
</resources>
'''
        colors_path.write_text(colors_content, encoding='utf-8')

        # 修改 build.gradle 中的包名、版本号和签名配置
        gradle_path = build_dir / 'app' / 'build.gradle'
        gradle_content = gradle_path.read_text(encoding='utf-8')
        gradle_content = gradle_content.replace('com.webapk.app', package_name)

        # 生成 versionCode (基于时间戳，确保递增)
        version_code = int(time.strftime('%Y%m%d%H'))
        gradle_content = gradle_content.replace('versionCode 1', f'versionCode {version_code}')
        gradle_content = gradle_content.replace('versionName "1.0"', f'versionName "{version_name}"')

        # 添加签名配置
        signing_config = f'''
    signingConfigs {{
        release {{
            storeFile file('../release.keystore')
            storePassword '{store_password}'
            keyAlias '{key_alias}'
            keyPassword '{key_password}'
        }}
    }}
'''
        # 在 buildTypes 之前插入签名配置
        gradle_content = gradle_content.replace(
            '    buildTypes {',
            signing_config + '    buildTypes {'
        )
        # 给 release buildType 添加签名配置
        gradle_content = gradle_content.replace(
            'release {\n            minifyEnabled false',
            'release {\n            signingConfig signingConfigs.release\n            minifyEnabled false'
        )
        gradle_path.write_text(gradle_content, encoding='utf-8')

        # 修改 AndroidManifest.xml 中的包名和屏幕方向
        manifest_path = build_dir / 'app' / 'src' / 'main' / 'AndroidManifest.xml'
        manifest_content = manifest_path.read_text(encoding='utf-8')
        manifest_content = manifest_content.replace('com.webapk.app', package_name)

        # 添加屏幕方向设置
        if screen_orientation != 'unspecified':
            manifest_content = manifest_content.replace(
                'android:configChanges="orientation|screenSize|keyboardHidden"',
                f'android:configChanges="orientation|screenSize|keyboardHidden"\n            android:screenOrientation="{screen_orientation}"'
            )

        manifest_path.write_text(manifest_content, encoding='utf-8')

        # 重命名包目录
        old_package_dir = build_dir / 'app' / 'src' / 'main' / 'java' / 'com' / 'webapk' / 'app'
        new_package_parts = package_name.split('.')
        new_package_dir = build_dir / 'app' / 'src' / 'main' / 'java' / '/'.join(new_package_parts)
        new_package_dir.parent.mkdir(parents=True, exist_ok=True)

        # 移动 MainActivity.kt
        if old_package_dir.exists():
            for f in old_package_dir.glob('*'):
                content = f.read_text(encoding='utf-8')
                content = content.replace('package com.webapk.app', f'package {package_name}')

                # 如果启用全屏模式，修改为沉浸式（隐藏系统栏）
                if fullscreen and f.name == 'MainActivity.kt':
                    # 添加额外导入
                    content = content.replace(
                        'import androidx.core.view.WindowCompat',
                        'import androidx.core.view.WindowCompat\nimport androidx.core.view.WindowInsetsCompat\nimport androidx.core.view.WindowInsetsControllerCompat'
                    )
                    # 替换 hideSplashScreen 中只改颜色的逻辑为隐藏系统栏
                    content = content.replace(
                        '// 只改状态栏颜色，不改布局（避免跳动）\n        window.statusBarColor = Color.BLACK',
                        '''// 全屏模式：隐藏系统栏
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }'''
                    )
                    # 全屏模式不需要给内容区域添加 padding
                    content = content.replace(
                        '''// 给内容区域添加顶部 padding = 状态栏高度
        val statusBarHeight = getStatusBarHeight()
        swipeRefresh.setPadding(0, statusBarHeight, 0, 0)
        errorView.setPadding(0, statusBarHeight, 0, 0)''',
                        '// 全屏模式：不需要 padding'
                    )

                new_file = new_package_dir / f.name
                new_package_dir.mkdir(parents=True, exist_ok=True)
                new_file.write_text(content, encoding='utf-8')

            # 删除旧目录
            shutil.rmtree(build_dir / 'app' / 'src' / 'main' / 'java' / 'com' / 'webapk')

        yield send_done('配置修改完成')

        # 步骤 6: 执行 Gradle 构建
        yield send_progress('开始编译 APK (这可能需要几分钟)...', 40)

        # 设置 Gradle 环境
        gradle_wrapper = build_dir / 'gradlew.bat' if sys.platform == 'win32' else build_dir / 'gradlew'

        if sys.platform != 'win32':
            os.chmod(gradle_wrapper, 0o755)

        # 执行构建
        env = os.environ.copy()

        # 自动检测并设置环境变量
        tools_dir = BASE_DIR / 'tools'
        jdk_dirs = list((tools_dir / 'jdk').glob('jdk-*'))
        if jdk_dirs:
            env['JAVA_HOME'] = str(jdk_dirs[0])
        if (tools_dir / 'android-sdk').exists():
            env['ANDROID_HOME'] = str(tools_dir / 'android-sdk')
            env['ANDROID_SDK_ROOT'] = str(tools_dir / 'android-sdk')

        # 打印环境变量用于调试
        print(f"[DEBUG] JAVA_HOME: {env.get('JAVA_HOME', 'NOT SET')}")
        print(f"[DEBUG] ANDROID_HOME: {env.get('ANDROID_HOME', 'NOT SET')}")

        process = subprocess.Popen(
            [str(gradle_wrapper), 'assembleRelease', '--no-daemon'],
            cwd=str(build_dir),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            env=env
        )

        build_progress = 40
        build_output = []
        for line in iter(process.stdout.readline, ''):
            line = line.strip()
            if line:
                build_output.append(line)
                print(f"[GRADLE] {line}")  # 在终端显示
                # 解析构建进度
                if 'CONFIGURING' in line.upper():
                    build_progress = 50
                    yield send_progress('配置项目...', build_progress)
                elif 'COMPILING' in line.upper() or 'COMPILE' in line.upper():
                    build_progress = min(build_progress + 5, 70)
                    yield send_progress('编译源码...', build_progress)
                elif 'PROCESSING' in line.upper():
                    build_progress = min(build_progress + 3, 80)
                    yield send_progress('处理资源...', build_progress)
                elif 'PACKAGING' in line.upper() or 'PACKAGE' in line.upper():
                    build_progress = 85
                    yield send_progress('打包 APK...', build_progress)
                elif 'BUILD SUCCESSFUL' in line.upper():
                    build_progress = 95
                    yield send_progress('构建成功!', build_progress)

        process.wait()

        if process.returncode != 0:
            # 获取最后几行错误信息
            error_lines = [l for l in build_output if 'error' in l.lower() or 'failed' in l.lower()]
            error_msg = error_lines[-1] if error_lines else '未知错误'
            print(f"[ERROR] Build failed: {error_msg}")
            yield send_error(f'Gradle 构建失败: {error_msg}')
            return

        yield send_done('APK 编译完成')

        # 步骤 7: 打包 APK + 证书 + 说明文件为 ZIP
        yield send_progress('打包下载文件...', 98)
        time.sleep(0.3)

        apk_source = build_dir / 'app' / 'build' / 'outputs' / 'apk' / 'release' / 'app-release.apk'
        if not apk_source.exists():
            yield send_error('APK 文件未找到，构建可能失败')
            return

        # 使用应用名作为文件名
        safe_name = re.sub(r'[^\w\-]', '_', app_name)
        zip_filename = f'{safe_name}_{build_id}.zip'
        zip_path = OUTPUT_DIR / zip_filename

        # 创建说明文件
        readme_content = f'''===== {app_name} 签名证书信息 =====

请妥善保管此文件夹中的所有文件！

证书文件: release.keystore
证书密码: {store_password}
密钥别名: {key_alias}
密钥密码: {key_password}

重要提示:
1. 更新APP时必须使用同一个证书签名，否则无法覆盖安装
2. 如果丢失证书，将无法更新已发布的APP
3. 建议将整个文件夹备份到安全的地方

证书有效期: 100年
生成时间: {time.strftime("%Y-%m-%d %H:%M:%S")}
'''

        # 创建 ZIP 文件
        with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
            # 添加 APK
            zf.write(apk_source, f'{safe_name}.apk')
            # 添加证书
            zf.write(keystore_path, 'release.keystore')
            # 添加说明文件
            zf.writestr('证书信息-请妥善保管.txt', readme_content)

        # 清理构建目录
        shutil.rmtree(build_dir)

        yield send_success(zip_filename)

    except Exception as e:
        yield send_error(f'构建过程出错: {str(e)}')
        # 清理
        if build_dir.exists():
            try:
                shutil.rmtree(build_dir)
            except:
                pass


@app.route('/')
def index():
    """首页"""
    return render_template('index.html')


@app.route('/build', methods=['POST'])
def build():
    """构建 APK"""
    try:
        app_name = request.form.get('appName', '').strip()
        package_name = request.form.get('packageName', '').strip().lower()
        url = request.form.get('url', '').strip()
        icon = request.files.get('icon')
        screen_orientation = request.form.get('screenOrientation', 'unspecified').strip()
        fullscreen = request.form.get('fullscreen', '0') == '1'
        splash_color = request.form.get('splashColor', '#f8f9fa').strip()
        status_bar_color = request.form.get('statusBarColor', '#000000').strip()

        if not all([app_name, package_name, url, icon]):
            return stream_response([send_error('请填写所有必填字段')])

        # 保存图标
        icon_filename = f'{uuid.uuid4()}.png'
        icon_path = UPLOAD_DIR / icon_filename
        icon.save(icon_path)

        # 处理用户上传的证书
        existing_keystore = None
        keystore_file = request.files.get('keystore')
        if keystore_file:
            keystore_password = request.form.get('keystorePassword', '').strip()
            key_alias = request.form.get('keyAlias', 'key0').strip()
            key_password = request.form.get('keyPassword', '').strip() or keystore_password

            if not keystore_password:
                return stream_response([send_error('请填写证书密码')])

            # 保存证书文件
            keystore_filename = f'{uuid.uuid4()}.keystore'
            keystore_path = UPLOAD_DIR / keystore_filename
            keystore_file.save(keystore_path)

            existing_keystore = {
                'path': keystore_path,
                'store_password': keystore_password,
                'key_alias': key_alias,
                'key_password': key_password
            }

            # 获取版本号
            version_name = request.form.get('versionName', '1.0').strip() or '1.0'
        else:
            version_name = '1.0'

        # 返回流式响应
        return stream_response(build_apk(app_name, package_name, url, icon_path, existing_keystore, screen_orientation, fullscreen, splash_color, version_name, status_bar_color))

    except Exception as e:
        return stream_response([send_error(f'服务器错误: {str(e)}')])


@app.route('/download/<filename>')
def download(filename):
    """下载文件（APK 或 ZIP）"""
    file_path = OUTPUT_DIR / filename
    if file_path.exists() and file_path.suffix in ('.apk', '.zip'):
        return send_file(file_path, as_attachment=True)
    return '文件不存在', 404


if __name__ == '__main__':
    print('=' * 50)
    print('网页转APK生成器')
    print('=' * 50)
    print(f'请访问: http://localhost:5000')
    print('=' * 50)
    app.run(host='0.0.0.0', port=5000, debug=True, threaded=True)
