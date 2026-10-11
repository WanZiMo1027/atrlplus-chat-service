# 图片与动画表情描述

上线前在 chat-service 使用的 PostgreSQL 数据库执行 [建表脚本](sql/media-description.sql)。`image.file` 是唯一键；同一个文件第二次出现时读取 `ready` 描述。

设置 `DEEPSEEK_API_KEY`（或 `MEDIA_VISION_API_KEY`）后，默认使用 `deepseek-flash`。如需其他兼容服务，可设置 `MEDIA_VISION_BASE_URL` 和 `MEDIA_VISION_MODEL`。密钥只放环境变量或未跟踪的 `application-local.yml`。

- `[图片]`：首次同步识别，当前批次只发送文字描述。识别或数据库写入失败不会中断消息批次。
- `[动画表情]`：首次将原图 URL 通过 `images` 发给 Agent，同时后台识别并写库；命中缓存后只发送 `content`、`text`、`emotion`、`memeMeaning`、`confidence` 描述。
- 被引用消息中的图片只查询已有描述，不重复发送原图或发起识别。

媒体 URL 不写入消息描述；Agent 的 Redis 短期记忆会过滤 `ImageContent`。首次表情能否成功进入模型仍取决于 Agent 侧对该 URL 的读取结果。

## 图片表情包管理与 URL 预览

部署前，在 **chat-service 的 PostgreSQL 数据库**执行更新后的 [媒体建表脚本](sql/media-description.sql)。同一脚本兼容首次建表和旧表增量升级，可重复执行，保留现有文件 ID、描述和识别状态。旧记录的创建时间使用原更新时间；旧记录缺少 URL 时不从文件 ID 推断地址。

接收图片或动画表情时，先保存有效的 HTTP/HTTPS 原始地址，再查询描述缓存。缓存命中也更新地址，保留人工描述；再次接收没有有效地址时保留原地址。引用消息只读取已有描述，不更新地址、不发起识别。

管理页支持图片和表情包的分页、搜索、筛选、预览和增删改。浏览器直接使用保存的 URL 展示原图，GIF 保留动画；识别失败也可通过有效地址预览。没有地址显示“暂无预览地址”，地址失效或浏览器无法访问时显示“预览不可用”，仍可查看和修改描述。

服务不再下载本地副本，不提供本地预览接口，不运行下载线程池、重试扫描或文件清理任务；原有 MEDIA_STORAGE_* 配置不再使用。此前已部署的下载字段、清理表和磁盘副本保留，当前服务不再读取或处理它们，不需要删除这些历史数据。

识别任务仍通过 recognition_claimed_at 和 revision 防止过期结果覆盖人工描述。删除保留 deleted_at 标记；同一文件 ID 后续不再入库、识别或向 Agent 转发原图，只生成通用媒体占位文字。编辑和删除要求版本匹配，冲突返回 409；已删除的文件 ID 禁止重新新增。

### 回归测试


常规 `./mvnw.cmd test` 运行接收、地址校验、管理接口和鉴权测试。实际 PostgreSQL 迁移及条件更新回归需要设置 `MEDIA_TEST_DATABASE_URL`，可选 `MEDIA_TEST_DATABASE_USER`（默认 `postgres`）和 `MEDIA_TEST_DATABASE_PASSWORD`（默认空）。测试只在随机命名的 `media_test_*` schema 内建表并在结束后删除该 schema；未配置测试连接时明确跳过这组测试。使用独立测试数据库，不使用线上连接。
