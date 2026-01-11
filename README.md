# 网页转APK生成器

一个简单易用的网页转Android APK工具，可以将任意网页打包成Android应用。

![演示截图](snap1.png)

## 功能特性

- 将任意网址打包成Android APK应用
- 自定义应用名称、包名和图标
- 实时显示构建进度
- 自动处理图标尺寸适配
- 纯本地运行，无需联网（首次构建除外）

## 环境要求

- Windows 10/11
- Python 3.8+
- 约 1GB 磁盘空间（用于 JDK 和 Android SDK）

## 快速开始

### 1. 安装环境

首次使用需要安装依赖环境：

```bash
# 方式一：运行安装脚本
python setup_env.py

# 方式二：双击运行
install.bat
```

安装脚本会自动下载并配置：
- OpenJDK 17
- Android SDK (build-tools, platform-tools)
- Gradle Wrapper
- Python 依赖 (Flask, Pillow)

### 2. 启动服务

```bash
# 双击运行
start.bat
```

### 3. 生成APK

1. 打开浏览器访问 http://localhost:5000
2. 填写应用信息：
   - **应用名称**：显示在手机上的应用名
   - **包名**：如 `com.example.myapp`（小写字母、数字和点）
   - **网址**：要打包的网页地址
   - **图标**：建议 512x512 PNG 图片
3. 点击"生成 APK"按钮
4. 等待构建完成后下载 APK 文件

## 项目结构

```
apk/
├── app.py                 # Flask 后端服务
├── setup_env.py           # 环境安装脚本
├── start.bat              # 启动脚本
├── install.bat            # 安装脚本
├── set_env.bat            # 环境变量设置
├── requirements.txt       # Python 依赖
├── templates/
│   └── index.html         # 网页界面
├── static/                # 静态资源
├── android-template/      # Android 项目模板
│   ├── app/
│   │   └── src/main/
│   │       ├── java/      # Kotlin 源码
│   │       ├── res/       # 资源文件
│   │       └── AndroidManifest.xml
│   ├── gradle/
│   ├── build.gradle
│   └── gradlew.bat
├── tools/                 # 开发工具（自动下载）
│   ├── jdk/               # OpenJDK 17
│   └── android-sdk/       # Android SDK
└── output/                # 构建输出目录
```

## 技术栈

- **后端**：Python + Flask
- **前端**：HTML + CSS + JavaScript
- **Android**：Kotlin + WebView
- **构建**：Gradle 8.0

## 生成的 APK 说明

生成的 APK 是一个基于 WebView 的 Android 应用，特性：

- 全屏显示网页内容
- 支持 JavaScript
- 支持本地存储 (localStorage)
- 支持文件上传
- 最低支持 Android 7.0 (API 24)
- 目标版本 Android 14 (API 34)

## 常见问题

### Q: 构建失败怎么办？

1. 确保已运行 `install.bat` 完成环境安装
2. 检查终端输出的错误信息
3. 确保网络正常（首次构建需要下载依赖）

### Q: 首次构建很慢？

首次构建需要下载 Gradle 依赖，可能需要 3-5 分钟，后续构建会快很多（约 30 秒）。

### Q: 如何修改 APK 的更多属性？

编辑 `android-template/app/build.gradle` 文件可以修改：
- `versionCode` - 版本号
- `versionName` - 版本名称
- `minSdk` - 最低支持的 Android 版本

### Q: 生成的 APK 可以上架应用商店吗？

生成的是 Debug 签名的 APK，如需上架应用商店，需要：
1. 创建正式签名密钥
2. 使用 Release 构建
3. 符合应用商店的其他要求

## 许可证

MIT License

## 更新日志

### v1.0.0
- 初始版本
- 支持网页转 APK
- 自定义应用名称、包名、图标
- 实时构建进度显示
