● FCM 发送消息有三种方式：                                                                                                                                                                                                                                                                                                                  
  
  1. Firebase 控制台（最简单，适合测试）                                                                                                                                                                                                                                                                                                       
  1. 打开 https://console.firebase.google.com/                                                                                                                                                                                                                                                                                              
  2. 选择你的项目 → Messaging → 创建第一个广告系列
  3. 选择 Firebase 通知消息
  4. 填写标题和内容
  5. 选择目标：单个设备（需要 FCM Token）或 应用（全量推送）
  6. 点击发送

  ---
  2. 服务端 API（正式使用）

  方式 A：使用 Firebase Admin SDK（推荐）

  Python 示例：
  import firebase_admin
  from firebase_admin import credentials, messaging

  # 初始化（只需一次）
  cred = credentials.Certificate('serviceAccountKey.json')  # 从 Firebase 下载
  firebase_admin.initialize_app(cred)

  # 发送给单个设备
  message = messaging.Message(
      notification=messaging.Notification(
          title='标题',
          body='内容',
      ),
      data={
          'url': 'https://example.com/page',  # 自定义数据，点击跳转
      },
      token='用户的FCM Token',  # 从 APP 获取
  )
  response = messaging.send(message)
  print('发送成功:', response)

  安装：
  pip install firebase-admin

  获取 serviceAccountKey.json：
  Firebase Console → 项目设置 → 服务账号 → 生成新的私钥

  ---
  方式 B：HTTP API（无 SDK）

  curl -X POST \
    'https://fcm.googleapis.com/v1/projects/YOUR_PROJECT_ID/messages:send' \
    -H 'Authorization: Bearer ACCESS_TOKEN' \
    -H 'Content-Type: application/json' \
    -d '{
      "message": {
        "token": "用户的FCM Token",
        "notification": {
          "title": "标题",
          "body": "内容"
        },
        "data": {
          "url": "https://example.com/page"
        }
      }
    }'

  ---
  3. 网页端获取 Token

  APP 端调用 JS 接口获取 Token，发送到你的服务器保存：

  // 注册推送，获取 Token
  Web2APK.registerPush();

  // 回调函数
  function onPushRegistered(success, token) {
      if (success) {
          console.log('FCM Token:', token);
          // 发送到你的服务器保存
          fetch('/api/save-token', {
              method: 'POST',
              body: JSON.stringify({ token: token })
          });
      }
  }