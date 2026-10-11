# TencentDB L1 控制面板

控制面板由 chat-service 提供，入口为 `http://localhost:8080/admin/`。侧边栏包含 TencentDB 的“记忆页”“提示词页”、QQ 群“回复控制”和“图片表情包管理”的“图片”“表情包”。TencentDB 凭据仍由 agent-service 持有；媒体预览由浏览器直接读取保存的原始 URL。

## 启用

两个服务必须使用相同的 `ADMIN_INTERNAL_TOKEN`。该值只放在环境变量或各自被忽略的 `application-local.yml`，不要提交或打印。首次启用前确认 chat-service 的 PostgreSQL 用户可以创建 `admin_l1_*` 表。部署群回复开关前，先执行 [`qq-group-reply-config.sql`](sql/qq-group-reply-config.sql)；群消息处理依赖该表，即使管理页面未启用也需要建表。

| 服务 | 环境变量 | 说明 |
| --- | --- | --- |
| chat-service | `ADMIN_ENABLED=true` | 启用页面、管理 API 和管理表初始化 |
| chat-service | `ADMIN_PASSWORD` | 管理员密码；页面用户名固定为 `admin` |
| 两个服务 | `ADMIN_INTERNAL_TOKEN` | chat-service 调用 Agent 内部接口的共享令牌 |
| agent-service | `ADMIN_INTERNAL_ENABLED=true` | 启用受令牌保护的 L1 管理接口 |

缺少必需的密码或内部令牌时，已启用的管理功能会拒绝启动。默认状态下管理页面和 API 不开放。部署到内网时，仍应只在可信网段开放服务端口。

## 构建与启动

需要 Java 21 和 Node.js/npm。Windows 下在 chat-service 仓库执行 `./mvnw.cmd package`；Maven 的 `prepare-package` 阶段会运行 `npm ci` 和 Vue 构建，再将产物复制进 Spring Boot JAR 的 `static/admin/`。Agent 仓库使用其 Maven Wrapper 构建，Java 版本为 17。开发 Vue 页面时，在 `admin-web/` 运行 `npm run dev`，Vite 会把 `/api/admin` 代理到本机 chat-service 的 8080 端口。

先启动 PostgreSQL、Redis、Nacos 和 TencentDB MemoryCore，再启动 agent-service 与 chat-service。控制面板的数据接口依赖 Nacos 中的 `agent-service`；远端暂时不可用时，已保存草稿仍可查看和编辑，但无法发布或修改记忆。

开发时先启动启用了管理功能的 chat-service，再运行 Vite。`npm run dev` 只启动 Vue 页面；它不会启动 chat-service。修改 `ADMIN_ENABLED` 等环境变量后需要重启 Java 服务。Vite 默认代理到 `http://127.0.0.1:8080`；若 chat-service 使用其他端口，请调整 `admin-web/vite.config.ts` 中的代理目标。浏览器打开 Vite 输出的 `/admin/` 地址后，从 `/admin/login` 登录。

遇到 Vite 的 `ECONNREFUSED`，先检查其代理目标上的 chat-service 是否已监听；若页面显示“管理功能未启用”或 API 返回 403，检查 chat-service 的 `ADMIN_ENABLED=true`、`ADMIN_PASSWORD` 和 `ADMIN_INTERNAL_TOKEN`，然后重启服务。两个 Java 服务的内部令牌还须一致，且 Agent 要启用 `ADMIN_INTERNAL_ENABLED=true`。

## 行为与限制

- L1 记忆页查询当前配置的 TencentDB team、agent、user 下的全部原子记忆；可以分页、按类型筛选、按正文关键词搜索、编辑和单条删除。关键词搜索需要逐页读取 L1，记忆量很大时可能较慢。删除没有 TencentDB 恢复接口，操作前会保存条目快照到 PostgreSQL 审计表。
- L1 提示词页将草稿和发布分开。首次保存草稿时记录远端 Prompt ID 与版本；发布前再次比较，版本变化返回冲突。历史版本可载入草稿后重新发布。
- TencentDB 未绑定自定义 L1 Prompt 时返回 `builtin:l1` 和空正文，内置规则的实际正文无法从该接口读取。Agent 仓库的 `prompts/tencent-l1-qq-group.txt` 是单独的项目模板；可从面板“载入项目模板”放入编辑区，保存草稿不影响线上，发布后才会绑定为生效版本。
- 原有 `/internal/memory/l1-prompt/init` 在已有自定义绑定时不再覆盖生效提示词；启用 Agent 管理接口时，该入口也要求内部令牌。
- 当前 TencentDB MemoryCore 的 L1 查询响应不提供可靠的 `session_id`，因此记忆页不支持按 QQ 群筛选。人格和模型参数尚未加入面板。
- 回复控制页列出已收到消息并记录的 QQ 群。新群默认允许回复；管理员关闭后，该群消息在后台刷新时跳过 Agent 调用。群名称和回复状态在 Redis 缓存 5 分钟，管理员切换开关会同步更新状态缓存。
- 图片表情包管理页分别为 `/admin/media/images` 和 `/admin/media/memes`，管理 `media_description` 接收媒体描述库，不修改用于发送回复表情的 `memes/custom-faces.json`。上线前执行更新后的 [`media-description.sql`](sql/media-description.sql)，地址保存和识别说明见 [媒体描述文档](media-description.md)。媒体管理直接使用 chat-service 的 PostgreSQL，不依赖 Agent 在线。
- 两页支持每页 20 条、描述关键词搜索、识别状态筛选、缩略图与原图预览、URL 新增和描述编辑。新增可填写 OneBot 文件 ID，留空自动生成 `manual:<UUID>`；不支持文件上传。图片描述为画面内容，表情包还可编辑文字、情绪、聊天含义及 0～1 的置信度。人工修改立即供后续消息使用，后台识别不会覆盖它。
- 编辑时文件 ID 和类型固定；更换 URL 后预览使用新地址。旧记录缺少地址时显示“暂无预览地址”，仍可只修改描述。URL 过期或浏览器无法访问时显示“预览不可用”，描述仍保留。服务不下载本地副本。
- 删除会永久忽略该文件 ID、隐藏记录；同一文件再次收到也不重新入库，无面板恢复入口。删除确认框说明此行为，已删除的 ID 也不能手动重新新增。编辑和删除携带 `revision`，与后台识别或其他管理员冲突时返回 409，保留编辑内容并提示刷新重试。

媒体 API 前缀为 `/api/admin/media`：`GET` 按 `kind/query/status/page/pageSize` 返回 `items/total`，`POST` 新增，`PUT /{id}` 修改，`DELETE /{id}` 删除。新增/编辑正文使用 `sourceUrl`、`description` 对象；新增包含 `kind`、可选 `fileId`，编辑/删除必须包含 `revision`。数字 ID 在 JSON 中以字符串返回，避免浏览器精度损失。全部接口复用管理员会话，写操作要求 CSRF；响应返回原始 URL 和描述，不包含下载状态或磁盘路径。

PostgreSQL 管理表为 `admin_l1_prompt_state`、`admin_l1_prompt_history` 和 `admin_l1_audit`，仅在启用面板时通过 `CREATE TABLE IF NOT EXISTS` 建立。发布失败保留草稿；远端发布成功而本地确认中断时，下一次读取会根据待确认记录与远端生效内容对账。
