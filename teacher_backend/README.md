# Lingua 教学网关

当前仅完成本地代码与自动化验证，**未部署、未创建账户、未配置模型密钥、未调用付费模型**。

## 接口

App → HTTPS `/v1/teach` → 由服务器配置的聊天模型。请求只含当前语言、题目、参考内容和回答，不上传完整历史。返回 questionId、grade、feedback、hint；服务器和 App 均校验。模型不能直接修改 Room、Supabase 或学习进度。

App 的连接设置需要 HTTPS 完整端点和已有 Supabase Auth 用户的短期 access token。令牌仅留内存，重启需重新配置；不是 publishable key、模型 API Key 或管理员密钥。普通登录界面、令牌自动刷新尚未实现，需要用户确认账户方案后接入。

## 服务器环境变量（仅服务器侧）

- `SUPABASE_URL`、`SUPABASE_PUBLISHABLE_KEY`：项目地址及客户端公开密钥，不允许 service-role。
- `TEACHER_ALLOWED_USERS`：明确授权的 Supabase Auth 用户 UUID，逗号分隔；默认空，拒绝所有付费调用。
- `MODEL_CHAT_URL`：模型供应商的完整 HTTPS chat/completions 地址。禁止重定向。
- `MODEL_API_KEY`、`MODEL_NAME`：服务端密钥和支持 JSON 模式的模型；无默认值。
- `MODEL_DISABLE_THINKING=1`：需要时按供应商文档关闭思考模式。
- `TEACHER_DAILY_REQUESTS`：每用户每日调用上限，默认 30。
- `TEACHER_GLOBAL_DAILY_REQUESTS`：全局每日调用上限，默认 100。

调用量由 SQLite 持久化；每次最多 700 输出 token，输入最多 16KB。超时/失败仍消耗额度，因为供应商可能已计费。相同请求缓存结果，不重复计费调用。这里是调用量限制，不是精确人民币预算；真正金额上限须另设供应商账户预算。

参考官方协议：[Qwen JSON 输出](https://www.alibabacloud.com/help/zh/model-studio/qwen-structured-output)、[Supabase 用户令牌验证](https://supabase.com/docs/guides/auth/jwts)。模型 JSON 格式能力不等于教学事实一定正确；不确定时应返回 UNGRADED，最终由用户确认掌握程度。

## 本地验证

`python -B teacher_backend/test_server.py`：不需要密钥，使用本地 mock，覆盖校验、认证闸门、额度、幂等及 HTTP 请求。

`python -B teacher_backend/server.py`：只监听 127.0.0.1:8787；没有配置时拒绝真实模型调用。测试结束关闭服务。本次没有留下后台服务。

## 上线前需要确认

1. 服务器/托管位置和费用预算；不是擅自新建云资源。
2. 模型供应商、模型、地域及允许发送练习回答的范围。
3. 登录方案与授权用户名单，不自动开放给全项目用户。
4. 部署平台的 HTTPS、连接并发限制和请求速率限制。Python 标准库 HTTP 服务仅用于私人小规模验证，不宜直接暴露公网；正式部署应置于受控 HTTPS 代理后。

本版一次请求只评估当前题目。后续自由追问和动态生成新语法课需额外校验内容与额度；目前课程取自本地词库/模块内容，AI 负责灵活评价与提示。
