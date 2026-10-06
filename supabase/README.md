# 云端词库接入 · 0.3.0

## 0.5.0 难度标签（2026-10-06 云端已执行）

完整备份、13 批执行文件、迁移后快照和验证截图位于 outputs/cloud-difficulty-20261006。6,003 条官方初级义项已回填，重复执行 13 批均为 0 更新。公开 API 仍为 8 条示例词；官方词保持未发布。

004_difficulty_tags.sql 是新增 JSONB 字段的非破坏性迁移；005_existing_dictionary_levels.sql 只为已存在的 6,003 个初级义项补充官方词典标签，保留其他体系标签与已有 krdict 标签，不更改 published 或个人数据。005 文件较大，应使用数据库直连执行，或先拆成小批次；不建议一次粘贴到 SQL Editor。

标签格式：difficulty_tags=[{"framework":"krdict","level":"beginner","source":"韩国国立国语院《韩国语基础词典》","confidence":1.0,"version":"official-json-20260919"}]。中级使用 intermediate，高级使用 advanced；未分级用空数组。每个义项可有多个不同体系，TOPIK 标签需独立可信依据，不从词典等级换算。

prepare_official_dictionary.py 和 export_android_dictionary.py 支持 --level 初级对应的韩语参数 초급、중급、고급 或 all；默认只处理初级。--version 可指定源版本。新 SQL 导入需先执行 003 和 004；所有导入仍为 published=false。App 兼容没有 difficulty_tags 的旧云端响应，新增词不会自动加入学习。

根目录 supabase.local.properties 配置 SUPABASE_URL（HTTPS 项目根地址）及 SUPABASE_PUBLISHABLE_KEY。只允许公开客户端密钥，禁止数据库密码或管理员密钥。该文件已被 Git 忽略。公开密钥仍会包含于 APK；安全依赖服务端 RLS，而不是隐藏客户端密钥。

App 设置页点击“更新词库”：按当前语言分页读取已发布且未下架的单词与例句；完整下载并校验后事务写入 Room。不会上传个人记录，也不会覆盖熟练度、收藏、复习时间和历史。连接超时、权限异常或校验失败不影响离线学习。

本版为手动全量拉取，不是实时订阅；暂不自动删除已下载但云端下架的内容。完成时间只显示在当前 ViewModel 生命周期，不是持久化同步日志。在线音频 URL 目前只保存，播放仍使用系统 TTS。

云端配置已验证可读取 8 个单词及 15 个例句。手机端实际联网流程需安装新版后验证。管理员可在 Supabase 控制台编辑内容并设 published=true，客户端只能读取。
