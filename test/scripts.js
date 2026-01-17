/**
 * test/scripts.js - WebAPK 功能测试 JavaScript
 * 每个功能模块按区块组织，便于维护和添加
 */

// ==================== 工具函数 ====================

function showResult(elementId, message, isSuccess = true) {
    const el = document.getElementById(elementId);
    if (el) {
        el.textContent = message;
        el.style.color = isSuccess ? '#07C160' : '#ff4444';
    }
}

function checkWeb2APK(resultElementId) {
    if (typeof Web2APK === 'undefined') {
        showResult(resultElementId, '⚠️ 未检测到 Web2APK 环境', false);
        return false;
    }
    return true;
}

// ==================== 网络状态 ====================

function checkNetwork() {
    if (!checkWeb2APK('networkResult')) return;
    const status = Web2APK.getNetworkStatus();
    const icon = status === 'none' ? '❌' : '✅';
    showResult('networkResult', `${icon} 当前网络: ${status}`);
}

function onNetworkChange(isConnected, type) {
    if (isConnected) {
        showResult('networkResult', `✅ 网络已连接: ${type}`);
    } else {
        showResult('networkResult', '❌ 网络已断开', false);
    }
}

// ==================== 通知 ====================

function sendNotification() {
    if (!checkWeb2APK('notifyResult')) return;
    Web2APK.sendNotification('Web2APK', '这是一条立即发送的测试通知！');
    showResult('notifyResult', '✅ 已发送通知');
}

function scheduleNotification() {
    if (!checkWeb2APK('notifyResult')) return;
    Web2APK.scheduleNotification('Web2APK', '闹钟响了！这是5秒后的提醒。', 5000);
    showResult('notifyResult', '⏰ 已设置5秒后提醒（请尝试关闭APP）');
}

// ==================== 状态栏颜色 ====================

function changeColor(color) {
    if (typeof Web2APK !== 'undefined') {
        Web2APK.setStatusBarColor(color);
    }
    let meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
        meta = document.createElement('meta');
        meta.name = "theme-color";
        document.head.appendChild(meta);
    }
    meta.content = color;
}

// ==================== 缓存清理 ====================

function clearCache() {
    if (!checkWeb2APK('cacheResult')) return;
    Web2APK.clearCache(true);
    showResult('cacheResult', '✅ 缓存清理指令已发送');
}

// ==================== 剪贴板 ====================

function testCopyClipboard() {
    if (!checkWeb2APK('clipboardResult')) return;
    Web2APK.copyToClipboard('Web2APK 测试文字 - ' + new Date().toLocaleString());
    showResult('clipboardResult', '✅ 已复制到剪贴板');
}

function testReadClipboard() {
    if (!checkWeb2APK('clipboardResult')) return;
    const text = Web2APK.readClipboard();
    if (text) {
        showResult('clipboardResult', `📋 剪贴板内容: ${text}`);
    } else {
        showResult('clipboardResult', '⚠️ 剪贴板为空或无读取权限', false);
    }
}

// ==================== 生物识别 ====================

function testBiometric() {
    if (!checkWeb2APK('biometricResult')) return;
    const canAuth = Web2APK.canAuthenticate();
    if (canAuth !== 0) {
        showResult('biometricResult', `❌ 设备不支持生物识别 (错误码: ${canAuth})`, false);
        return;
    }
    showResult('biometricResult', '🔐 正在验证...');
    Web2APK.authenticate('身份验证', '请验证您的身份', '取消');
}

function onAuthSuccess() {
    showResult('biometricResult', '✅ 验证成功！');
}

function onAuthError(code, message) {
    showResult('biometricResult', `❌ 验证失败: ${message}`, false);
}

// ==================== 第三方登录 ====================

function testGoogleLogin() {
    if (!checkWeb2APK('loginResult')) return;
    if (!Web2APK.isGoogleLoginAvailable()) {
        showResult('loginResult', '❌ Google 登录不可用（未配置 Client ID 或无 Google 服务）', false);
        return;
    }
    showResult('loginResult', '🌐 正在唤起 Google 登录...');
    Web2APK.loginGoogle();
}

function onGoogleLoginSuccess(idToken, email, displayName, photoUrl) {
    const result = document.getElementById('loginResult');
    result.innerHTML = `✅ Google 登录成功！<br>👤 ${displayName}<br>📧 ${email}`;
    result.style.color = '#07C160';
}

function onGoogleLoginError(code, message) {
    showResult('loginResult', `❌ Google 登录失败: ${message}`, false);
}

function loginWechat() {
    showResult('loginResult', '⚠️ 微信登录尚未实现（需企业资质）');
}

// ==================== 扫码 ====================

function scanQRCode() {
    if (!checkWeb2APK('scanResult')) return;
    showResult('scanResult', '正在启动相机...');
    Web2APK.scanQRCode();
}

function onScanResult(text) {
    showResult('scanResult', `✅ 扫码结果: ${text}`);
    if (typeof Web2APK !== 'undefined') {
        Web2APK.vibrate(100);
    }
}

// ==================== 设备信息 ====================

function testDeviceInfo() {
    if (!checkWeb2APK('deviceInfoResult')) return;
    try {
        const info = JSON.parse(Web2APK.getDeviceInfo());
        const text = `📱 型号: ${info.model}\n🏷️ 品牌: ${info.brand}\n🤖 Android: ${info.androidVersion}\n📦 APP: ${info.appVersion}`;
        showResult('deviceInfoResult', text);
    } catch (e) {
        showResult('deviceInfoResult', '❌ 获取失败: ' + e.message, false);
    }
}

function testDeviceId() {
    if (!checkWeb2APK('deviceInfoResult')) return;
    const deviceId = Web2APK.getDeviceId();
    showResult('deviceInfoResult', `🔑 设备ID: ${deviceId}`);
}

function getAppVersion() {
    if (!checkWeb2APK('deviceInfoResult')) return;
    const version = Web2APK.getVersion();
    showResult('deviceInfoResult', `📱 APP 版本: ${version}`);
}

function showUA() {
    const ua = navigator.userAgent;
    const result = document.getElementById('deviceInfoResult');
    const hasWeb2APK = ua.indexOf('Web2APK') !== -1;
    result.textContent = '🔍 User-Agent:\n' + ua + (hasWeb2APK ? '\n\n✅ 检测到 Web2APK 标识！' : '');
    result.style.color = hasWeb2APK ? '#07C160' : '#888';
}

// ==================== 电话/短信 ====================

function testDialPhone() {
    if (!checkWeb2APK('phoneResult')) return;
    Web2APK.dialPhone('10086');
    showResult('phoneResult', '📞 正在跳转拨号界面 (10086)...');
}

function testSendSMS() {
    if (!checkWeb2APK('phoneResult')) return;
    Web2APK.sendSMS('10086', 'Web2APK 短信测试 - 请忽略此消息');
    showResult('phoneResult', '💬 正在跳转短信界面...');
}

// ==================== JS 相机 ====================

function testTakePhoto() {
    if (!checkWeb2APK('jsCameraResult')) return;
    showResult('jsCameraResult', '📷 正在启动相机...');
    Web2APK.takePhoto();
}

function onCameraResult(dataUrl) {
    showResult('jsCameraResult', '✅ 拍照成功！');
    const preview = document.getElementById('jsCameraPreview');
    const content = document.getElementById('jsCameraContent');
    if (preview && content) {
        preview.classList.add('active');
        content.innerHTML = `<img src="${dataUrl}" alt="拍照结果">`;
    }
}

function onCameraError(message) {
    showResult('jsCameraResult', `❌ ${message}`, false);
}

// ==================== 设备控制 ====================

let screenOn = false;

function toggleScreenOn() {
    if (!checkWeb2APK('deviceResult')) return;
    screenOn = !screenOn;
    Web2APK.keepScreenOn(screenOn);
    showResult('deviceResult', screenOn ? '💡 屏幕常亮已开启' : '🌙 屏幕常亮已关闭');
}

function testVibrate() {
    if (!checkWeb2APK('deviceResult')) return;
    Web2APK.vibrate(1000);
    showResult('deviceResult', '📳 已振动 1000ms');
}

// ==================== 前台服务 ====================

function startFgService() {
    if (!checkWeb2APK('fgServiceResult')) return;
    Web2APK.startForegroundService('Web2APK', '正在后台运行...');
    showResult('fgServiceResult', '✅ 前台服务已启动');
}

function updateFgNotification() {
    if (!checkWeb2APK('fgServiceResult')) return;
    Web2APK.updateForegroundNotification('Web2APK', '运行中... ' + new Date().toLocaleTimeString());
    showResult('fgServiceResult', '🔄 通知已更新');
}

function stopFgService() {
    if (!checkWeb2APK('fgServiceResult')) return;
    Web2APK.stopForegroundService();
    showResult('fgServiceResult', '⏹️ 服务已停止');
}

// ==================== 桌面小组件 ====================

function testUpdateWidget() {
    if (!checkWeb2APK('widgetResult')) return;
    Web2APK.updateWidget(JSON.stringify({
        title: "今日待办",
        content: "3 项未完成",
        subtitle: "最近：买菜",
        icon: "📋",
        badge: 3,
        clickUrl: "/todo/list"
    }));
    showResult('widgetResult', '✅ 小组件数据已更新');
}

function testPinWidget() {
    if (!checkWeb2APK('widgetResult')) return;
    const success = Web2APK.requestPinWidget();
    if (success) {
        showResult('widgetResult', '📌 请在弹出的对话框中确认添加');
    } else {
        showResult('widgetResult', '❌ 不支持添加小组件到桌面', false);
    }
}

// ==================== 分享 ====================

function testShare() {
    if (!checkWeb2APK('shareResult')) return;
    Web2APK.share('WebAPK 功能测试', '我正在使用 Web2APK 生成的应用！', 'https://github.com');
    showResult('shareResult', '✅ 已调用系统分享');
}

// ==================== 应用评分 ====================

function testOpenPlayStore() {
    if (!checkWeb2APK('rateResult')) return;
    Web2APK.openPlayStore();
    showResult('rateResult', '⭐ 正在跳转到 Google Play...');
}

// ==================== 图片预览 ====================

function testPreviewSingle() {
    if (!checkWeb2APK('imagePreviewResult')) return;
    Web2APK.previewImage('https://picsum.photos/1200/800');
    showResult('imagePreviewResult', '🖼️ 正在打开图片预览...');
}

function testPreviewMultiple() {
    if (!checkWeb2APK('imagePreviewResult')) return;
    const images = JSON.stringify([
        'https://picsum.photos/1200/800?random=1',
        'https://picsum.photos/800/1200?random=2',
        'https://picsum.photos/1000/1000?random=3',
        'https://picsum.photos/1600/900?random=4'
    ]);
    Web2APK.previewImages(images, 0);
    showResult('imagePreviewResult', '📚 正在打开多图预览（左右滑动切换）...');
}

// ==================== 位置 ====================

function getLocation() {
    const result = document.getElementById('locationResult');
    result.textContent = '正在获取位置...';

    if (!navigator.geolocation) {
        showResult('locationResult', '您的浏览器不支持定位', false);
        return;
    }

    navigator.geolocation.getCurrentPosition(
        function (pos) {
            result.textContent = `纬度: ${pos.coords.latitude.toFixed(6)}\n经度: ${pos.coords.longitude.toFixed(6)}\n精度: ${pos.coords.accuracy.toFixed(0)}米`;
        },
        function (err) {
            showResult('locationResult', `获取失败: ${err.message}`, false);
        },
        { enableHighAccuracy: true, timeout: 10000 }
    );
}

// ==================== JS 弹窗 ====================

function testAlert() {
    alert('这是一个 alert() 弹窗！\n\n如果你看到的是原生样式的弹窗，说明 JS 弹窗支持已启用。');
    showResult('jsResult', 'alert() 测试完成');
}

function testConfirm() {
    const result = confirm('这是一个 confirm() 弹窗。\n\n请选择"确定"或"取消"：');
    showResult('jsResult', `confirm() 返回: ${result ? '确定' : '取消'}`);
}

function testPrompt() {
    const result = prompt('这是一个 prompt() 弹窗。\n\n请输入你的名字：', '访客');
    if (result !== null) {
        showResult('jsResult', `prompt() 返回: ${result}`);
    } else {
        showResult('jsResult', 'prompt() 已取消');
    }
}

// ==================== 文件上传 ====================

function handleFile(input, type) {
    const file = input.files[0];
    if (!file) return;

    const preview = document.getElementById('uploadPreview');
    const content = document.getElementById('previewContent');
    const info = document.getElementById('fileInfo');

    preview.classList.add('active');
    info.textContent = `文件名: ${file.name}\n大小: ${(file.size / 1024).toFixed(1)} KB\n类型: ${file.type || '未知'}`;

    if (type === 'image' || (type === 'auto' && file.type.startsWith('image/'))) {
        const reader = new FileReader();
        reader.onload = function (e) {
            content.innerHTML = `<img src="${e.target.result}" alt="预览">`;
        };
        reader.readAsDataURL(file);
    } else if (type === 'video' || (type === 'auto' && file.type.startsWith('video/'))) {
        const url = URL.createObjectURL(file);
        content.innerHTML = `<video src="${url}" controls style="max-height:200px"></video>`;
    } else {
        content.innerHTML = '<div style="text-align:center;padding:20px;color:#666;"><div style="font-size:40px;margin-bottom:10px;">📎</div><div>文件已选择</div></div>';
    }
}

// ==================== 初始化 ====================

document.addEventListener('DOMContentLoaded', function () {
    // 绑定文件上传事件
    const cameraInput = document.getElementById('cameraInput');
    const imageInput = document.getElementById('imageInput');
    const videoInput = document.getElementById('videoInput');
    const fileInput = document.getElementById('fileInput');

    if (cameraInput) cameraInput.addEventListener('change', function () { handleFile(this, 'image'); });
    if (imageInput) imageInput.addEventListener('change', function () { handleFile(this, 'image'); });
    if (videoInput) videoInput.addEventListener('change', function () { handleFile(this, 'video'); });
    if (fileInput) fileInput.addEventListener('change', function () { handleFile(this, 'auto'); });

    // 初始化 Plyr 播放器（如果存在）
    if (typeof Plyr !== 'undefined') {
        const player = document.getElementById('player');
        if (player) new Plyr(player);
    }

    // 更新小组件（如果可用）
    if (typeof Web2APK !== 'undefined') {
        try {
            Web2APK.updateWidget(JSON.stringify({
                title: "功能测试",
                content: "点击打开测试页",
                icon: "🧪"
            }));
        } catch (e) { }
    }
});
