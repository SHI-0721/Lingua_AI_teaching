# 验证记录 · 0.6.0（2026-10-06）

## 0.6.0 最新结果

构建、APK 打包与 Android lint 通过。core 13 项通过；app 29 项通过、1 项需要云端快照的可选测试跳过。新增教学闭环、已知表达变体、反馈校验、重复提交防护、课程恢复、Room v4→v5 保留历史、HTTPS 与响应大小校验。后端模拟验证通过，不调用付费模型。浅色/深色聊天截图已检查；尚无连接设备，未完成真机安装与真实语音/联网模型验收。详见 outputs/AI_TEACHER_ACCEPTANCE.md。

以下保留旧版本验证记录。

## 当前版本

新增官方词典资产、搜索和出处展示验收；1→3 与 2→3 升级覆盖保留记录；无双语例句的题型回退、选择题去重均有自动化测试。构建与 lint 结果详见 ../outputs/ANDROID_ACCEPTANCE.md。

下方为 0.2.0 历史测试范围说明，不代表已完成真实设备验收。

## 自动化验证

- core：11 个测试，覆盖复习调度、题型、答案规范化、打卡统计与规则评分。
- app：12 个 Robolectric 测试，覆盖原有流程、韩中训练、听力隐藏答案、收藏、多例句、日历、语音状态、数据库持久化与升级。
- 数据库升级测试从真实版本 1 schema 建表，确认升级保留单词学习状态并生成独立例句。
- 每日目标测试验证重复词不重复计数、目标固定、关闭后重新打开仍保留记录。
- TTS 测试覆盖初始化、语言切换、重复播放、停止/释放、不支持语言、失败和超时。
- NativePreviewTest 使用 Robolectric 原生图形渲染生成首页浅/深色、日历和主动回忆截图，并人工检查布局；不是实际设备截图。

构建命令：

```powershell
./build.ps1 -Tasks ':core:test',':app:testDebugUnitTest',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug'
```

## 设备验证限制

本机没有已连接并授权的 Android 手机。此前尝试两版官方模拟器、Android 15 / 9 镜像及软件渲染配置，模拟器仍无法正常启动；没有修改系统虚拟化或安装驱动。因此尚未验证真实手机安装、键盘、实际 TTS 音质及设备端 instrumentation 测试。

连接手机后运行 install.ps1，再运行 build.ps1 -Tasks ':app:connectedDebugAndroidTest'。特别检查韩语语音包缺失时提示、重复发音、退出页面后停止播放、重启后打卡记录、深浅主题及小屏滚动。

Robolectric 和 APK 编译成功不等同于已在真实 Android 设备运行。AI 评分、在线 TTS、导入/同步与年度热力图属于后续功能。
