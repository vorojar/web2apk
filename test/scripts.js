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

// ==================== 防截屏 ====================

function enableSecureMode() {
    if (!checkWeb2APK('secureResult')) return;
    Web2APK.setSecureMode(true);
    showResult('secureResult', '🔒 防截屏已开启，现在尝试截屏试试');
}

function disableSecureMode() {
    if (!checkWeb2APK('secureResult')) return;
    Web2APK.setSecureMode(false);
    showResult('secureResult', '🔓 防截屏已关闭');
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

// ==================== APP 唤端 ====================

function testOpenTaobao() {
    if (!checkWeb2APK('openAppResult')) return;
    // 打开淘宝，未安装则打开网页版
    const success = Web2APK.openAppOrFallback(
        'taobao://m.taobao.com',
        'https://m.taobao.com'
    );
    if (success) {
        showResult('openAppResult', '✅ 正在打开淘宝 APP...');
    } else {
        showResult('openAppResult', '⚠️ 淘宝未安装，已打开网页版');
    }
}

function testOpenWeixin() {
    if (!checkWeb2APK('openAppResult')) return;
    const success = Web2APK.openApp('weixin://');
    if (success) {
        showResult('openAppResult', '✅ 正在打开微信...');
    } else {
        showResult('openAppResult', '❌ 微信未安装', false);
    }
}

function testOpenMap() {
    if (!checkWeb2APK('openAppResult')) return;
    // 尝试打开高德地图导航到天安门
    const lat = 39.908823;
    const lng = 116.397470;
    const name = '天安门';

    // 先检测安装了哪个地图
    if (Web2APK.isAppInstalled('com.autonavi.minimap')) {
        // 高德地图
        Web2APK.openApp(`amapuri://route/plan?dlat=${lat}&dlon=${lng}&dname=${encodeURIComponent(name)}&dev=0&t=0`);
        showResult('openAppResult', '✅ 正在打开高德地图导航...');
    } else if (Web2APK.isAppInstalled('com.baidu.BaiduMap')) {
        // 百度地图
        Web2APK.openApp(`baidumap://map/direction?destination=${lat},${lng}&destination_name=${encodeURIComponent(name)}&mode=driving`);
        showResult('openAppResult', '✅ 正在打开百度地图导航...');
    } else {
        // 都没有，打开网页版高德
        Web2APK.openApp(`https://uri.amap.com/navigation?to=${lng},${lat},${encodeURIComponent(name)}`);
        showResult('openAppResult', '⚠️ 未安装地图 APP，已打开网页版');
    }
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

// ==================== 后台音频播放 ====================

function startBackgroundAudio() {
    const audio = document.getElementById('bgAudioPlayer');

    // 先播放音频
    audio.play().then(() => {
        // 如果在 WebAPK 环境中，启用后台音频 + 前台服务
        if (typeof Web2APK !== 'undefined') {
            Web2APK.enableBackgroundAudio(true);
            Web2APK.startForegroundService('音乐播放中', '正在播放: SoundHelix Song 1');
            showResult('bgAudioResult', '🎵 音乐播放中，后台模式已启用');
        } else {
            showResult('bgAudioResult', '🎵 音乐播放中（浏览器环境，无后台支持）');
        }
    }).catch(err => {
        showResult('bgAudioResult', '❌ 播放失败: ' + err.message);
    });
}

function stopBackgroundAudio() {
    const audio = document.getElementById('bgAudioPlayer');
    audio.pause();
    audio.currentTime = 0;

    // 如果在 WebAPK 环境中，禁用后台音频 + 停止前台服务
    if (typeof Web2APK !== 'undefined') {
        Web2APK.enableBackgroundAudio(false);
        Web2APK.stopForegroundService();
    }
    showResult('bgAudioResult', '⏹️ 音乐已停止');
}

// ==================== 录音功能 ====================

let recordingTimerInterval = null;
let recordingStartTime = 0;

function startRecording() {
    if (!checkWeb2APK('recordingResult')) return;
    Web2APK.startRecording();
}

function stopRecording() {
    if (!checkWeb2APK('recordingResult')) return;
    Web2APK.stopRecording();
}

function cancelRecording() {
    if (!checkWeb2APK('recordingResult')) return;
    Web2APK.cancelRecording();
}

// 录音开始回调
function onRecordingStarted() {
    document.getElementById('btnStartRecord').disabled = true;
    document.getElementById('btnStopRecord').disabled = false;
    document.getElementById('btnCancelRecord').disabled = false;
    document.getElementById('recordingTimer').style.display = 'block';
    document.getElementById('recordingPlayback').style.display = 'none';

    recordingStartTime = Date.now();
    updateRecordingTimer();
    recordingTimerInterval = setInterval(updateRecordingTimer, 100);

    showResult('recordingResult', '🔴 正在录音...');
}

// 录音完成回调
function onRecordingComplete(base64, durationMs) {
    stopRecordingTimer();

    document.getElementById('btnStartRecord').disabled = false;
    document.getElementById('btnStopRecord').disabled = true;
    document.getElementById('btnCancelRecord').disabled = true;

    const seconds = (durationMs / 1000).toFixed(1);
    showResult('recordingResult', `✅ 录音完成，时长 ${seconds} 秒`);

    // 播放录音
    const audio = document.getElementById('recordingPlayback');
    audio.src = 'data:audio/mp4;base64,' + base64;
    audio.style.display = 'block';
}

// 录音取消回调
function onRecordingCancelled() {
    stopRecordingTimer();

    document.getElementById('btnStartRecord').disabled = false;
    document.getElementById('btnStopRecord').disabled = true;
    document.getElementById('btnCancelRecord').disabled = true;
    document.getElementById('recordingPlayback').style.display = 'none';

    showResult('recordingResult', '❌ 录音已取消');
}

// 录音错误回调
function onRecordingError(error) {
    stopRecordingTimer();

    document.getElementById('btnStartRecord').disabled = false;
    document.getElementById('btnStopRecord').disabled = true;
    document.getElementById('btnCancelRecord').disabled = true;

    const errorMessages = {
        'PERMISSION_DENIED': '麦克风权限被拒绝',
        'ALREADY_RECORDING': '已在录音中',
        'NOT_RECORDING': '未在录音',
        'STOP_FAILED': '停止录音失败'
    };
    const msg = errorMessages[error] || error;
    showResult('recordingResult', '❌ 错误: ' + msg);
}

function updateRecordingTimer() {
    const elapsed = Date.now() - recordingStartTime;
    const seconds = Math.floor(elapsed / 1000);
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    document.getElementById('recordingTimer').textContent =
        `⏱️ ${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

function stopRecordingTimer() {
    if (recordingTimerInterval) {
        clearInterval(recordingTimerInterval);
        recordingTimerInterval = null;
    }
    document.getElementById('recordingTimer').style.display = 'none';
}

// ==================== 视频录制功能 ====================

function startVideoRecording() {
    if (!checkWeb2APK('videoRecordingResult')) return;
    showResult('videoRecordingResult', '📹 正在启动系统相机...');
    document.getElementById('videoRecordingPlayback').style.display = 'none';
    Web2APK.startVideoRecording();
}

// 视频录制完成回调
function onVideoRecordingComplete(base64, durationMs) {
    const seconds = (durationMs / 1000).toFixed(1);
    showResult('videoRecordingResult', `✅ 录制完成，时长 ${seconds} 秒`);

    // 播放视频
    const video = document.getElementById('videoRecordingPlayback');
    video.src = 'data:video/mp4;base64,' + base64;
    video.style.display = 'block';
}

// 视频录制取消回调
function onVideoRecordingCancelled() {
    showResult('videoRecordingResult', '❌ 录制已取消');
    document.getElementById('videoRecordingPlayback').style.display = 'none';
}

// 视频录制错误回调
function onVideoRecordingError(message) {
    showResult('videoRecordingResult', '❌ 录制失败: ' + message, false);
    document.getElementById('videoRecordingPlayback').style.display = 'none';
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

// ==================== 应用更新 ====================

function checkAppVersion() {
    if (!checkWeb2APK('updateResult')) return;
    try {
        const info = JSON.parse(Web2APK.getAppVersion());
        showResult('updateResult', `📦 当前版本: ${info.versionName} (${info.versionCode})`);
    } catch (e) {
        showResult('updateResult', '❌ 获取版本失败: ' + e.message, false);
    }
}

function checkInstallPermission() {
    if (!checkWeb2APK('updateResult')) return;
    const canInstall = Web2APK.canInstallPackages();
    if (canInstall) {
        showResult('updateResult', '✅ 已有安装权限');
    } else {
        showResult('updateResult', '⚠️ 未授权安装权限，点击下方按钮授权', false);
    }
}

function requestInstallPermission() {
    if (!checkWeb2APK('updateResult')) return;
    Web2APK.requestInstallPermission();
    showResult('updateResult', '📝 正在跳转设置页面，请授权后返回');
}

function testDownloadUpdate() {
    if (!checkWeb2APK('updateResult')) return;
    // 使用一个小的测试 APK
    const testApkUrl = 'https://maikami.com/web2apk/test.apk';
    showResult('updateResult', '⏬ 开始下载更新...');
    Web2APK.downloadUpdate(testApkUrl, '正在下载更新');
}

function testInstallUpdate() {
    if (!checkWeb2APK('updateResult')) return;
    Web2APK.installUpdate();
}

// 更新下载完成回调
function onUpdateDownloaded() {
    showResult('updateResult', '✅ 下载完成，点击"安装更新"按钮安装');
}

// 更新错误回调
function onUpdateError(message) {
    showResult('updateResult', '❌ 更新失败: ' + message, false);
}

// ==================== 字体缩放 ====================

function getTextZoom() {
    if (!checkWeb2APK('fontResult')) return;
    const zoom = Web2APK.getTextZoom();
    const systemScale = Web2APK.getSystemFontScale();
    showResult('fontResult', `📏 当前缩放: ${zoom}%\n📱 系统设置: ${systemScale}%`);
}

function setTextZoom(percent) {
    if (!checkWeb2APK('fontResult')) return;
    Web2APK.setTextZoom(percent);
    showResult('fontResult', `✅ 已设置字体缩放为 ${percent}%`);
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
