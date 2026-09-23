# chat-service 协作说明

## 服务定位

本仓库是 QQ 消息接入与回复网关（Spring Boot，`chat-service`）。它接收 SnowLuma OneBot v11 HTTP 事件或旧的 RabbitMQ 消息，统一为内部聊天消息，按会话在 Redis 中短暂聚合，再调用 `agent-service` 的 `/api/agent/chat`，最后通过当前 profile 的回复出口发送多条文字、可选引用和表情。

## 技术栈与启动

- Java 21、Spring Boot 3.5.16、Maven Wrapper；主要依赖 Web、Validation、Redis、OpenFeign、Nacos、RabbitMQ。
- 常用命令（Windows）：
  - `./mvnw.cmd test`：运行全部测试。
  - `./mvnw.cmd package`：测试并打包，产物在 `target/`。
  - `./mvnw.cmd spring-boot:run`：按默认 `snowluma` profile 启动。
- `application.yml` 的默认 profile 是 `snowluma`；切换旧 RabbitMQ 链路使用 `./mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=rabbitmq`（或设置 `SPRING_PROFILES_ACTIVE`）。两个 profile 的适配器 Bean 通过 `@Profile` 隔离，不能假设它们同时存在。
- 默认共享 Redis `localhost:6379` 和 Nacos `127.0.0.1:8848`；Agent Feign 客户端使用 Nacos 服务名 `agent-service`。

## 配置与凭据

`src/main/resources/application.yml` 导入可选的 `application-local.yml`；本地文件只存凭据，已被 `.gitignore` 忽略，绝不能提交或写入日志。关键配置：

- 通用：`NACOS_PASSWORD`、Redis 地址。
- `snowluma`：`SNOWLUMA_BASE_URL`（默认 `http://127.0.0.1:3000`）、`SNOWLUMA_ACCESS_TOKEN`（调用 OneBot API）、`SNOWLUMA_WEBHOOK_TOKEN`（事件推送校验；未配置时鉴权放行）。
- `rabbitmq`：`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`。

可参考 `docs/snowluma-onebot.example.json` 配置 SnowLuma：HTTP server 监听 3000，HTTP client 将事件 POST 到 `/onebot/v11/events`，并发送 `X-Signature`/Bearer 凭据。示例中的 token 只是占位符；不要把真实 token 写进仓库、测试输出或提交信息。

## 统一消息模型与主流程

核心内部类型是 `IncomingChatMessage(eventId, conversationId, senderName, text, target, snapshot)`，其中 SnowLuma 快照保留消息段、原始正文和消息 ID；缓冲后序列化为 `BufferedMessage`。`ReplyGateway` 抽象了两种回复出口。

### SnowLuma profile（默认）

1. `OneBotEventController` 接收原始字节，先用 `SnowLumaWebhookAuthenticator` 校验 `X-Signature: sha1=<HMAC-SHA1(raw-body)>`，兼容 `Authorization: Bearer`，再解析 JSON。
2. `OneBotEventMapper` 只处理 `post_type=message`，忽略机器人自己的消息、缺少关键 ID 的事件和非 group/private 消息；提取 text 段，同时保留只有 @、引用、图片等非文本段的消息，供后续描述服务处理。
3. 会话键必须保持 `qq:<selfId>:<group|private>:<targetId>`；事件键为 `<selfId>:<messageId>`。群聊发送者优先使用群名片，其次昵称。
4. `EventIdempotencyService` 用 Redis 7 天已处理标记和 1 天处理锁去重。成功接收后写入 Redis 缓冲，立即返回 HTTP 200；后台每秒扫描到期会话。
5. `MessageBufferService` 使用 Lua 保证追加/截取原子性：默认防抖 5 秒，单批最多 5 条，达到 5 条立即刷新；后台通过 `OneBotMessageDescriptionService` 查询一次 `get_msg`，把发送者、QQ 号、时间、文本、@、单层引用和媒体元数据整理成受限长度的描述，再调用 `AgentClient`。
6. Agent 返回的 `replyToEventId` 必须属于当前批次；网关将 `selfId:messageId` 转换为 OneBot 数字消息 ID。`SnowLumaReplyGateway` 让第一条文字携带引用段，后续文字各自独立发送，最后发送表情。表情通过 `MemeService` 从 `memes/custom-faces.json` 按语义标签随机选启用项；标签找不到时不影响已有文字回复。OneBot `retcode != 0` 必须抛错。

### RabbitMQ profile（兼容链路）

- `MessageIngestController` 接收 `POST /internal/messages` 的 `MessageDetectedEvent`；`MessageEventConsumer` 监听 `atriplus.qq.message.queue`，手动 ACK，解析失败或三次处理失败进入 DLQ。
- 队列/交换机常量集中在 `RabbitMqConfig`：回复交换机 `atriplus.qq.reply.exchange`、回复 routing key `qq.reply`、消息队列 `atriplus.qq.message.queue`、死信相关队列/交换机见该类。修改名称时要同步生产者和消费者。
- `MessageIngestService` 兼容旧事件 payload（`senderName`、`text`、`conversationName`）；缺失会话名时使用 `unknown`。旧消息可能没有 `ChatTarget` 或 `snapshot`，因此不要在 Rabbit 路径强制要求 SnowLuma 目标。
- 当前工作树中的 `ReplyEventPublisher` 仍保留 Rabbit 协议骨架，但 `publish`/`send` 暂未实际发送；不要把 Rabbit 回复链路当作已启用功能，恢复时需同步升级 `QQReplyEvent`（当前仍是旧的 `reply` 单字符串协议）和下游消费者测试。

## Agent 契约

发送给 Agent 的请求保持：

```json
{"conversationId":"qq:...","messages":[{"eventId":"...","senderName":"...","text":"..."}]}
```

响应包含 `shouldReply`、`replies`、可空 `replyToEventId` 和 `meme`。`replies` 最多 3 条；SnowLuma 路径支持逐条发送文字、第一条文字引用当前批次消息、纯表情和文字加表情。RabbitMQ 的旧 DTO 仍只有单个 `reply` 字段，且当前发布器未发送。修改 DTO、会话 ID、聚合策略或表情标签时，必须同时检查 `E:\ATRLPLUS-AGENTS` 的 Agent 服务契约。

## 代码组织与测试

- `controller/`：SnowLuma webhook 与 Rabbit 内部入口。
- `service/`：事件映射、幂等、Redis 缓冲、Agent 调用、回复网关和表情库。
- `client/`：Agent Feign DTO 与 OneBot action DTO。
- `config/`：消息刷新线程池、SnowLuma `RestClient`、Rabbit 拓扑和 profile 配置。
- `event/onebot/`：只声明业务需要的 OneBot v11 字段，并忽略未知字段。
- 测试重点：`OneBotEventMapperTest`（ID/会话/自消息过滤）、`OneBotMessageDescriptionServiceTest`（消息段、@、单层引用、媒体元数据和长度限制）、`SnowLumaWebhookAuthenticatorTest`（HMAC/Bearer）、`SnowLumaReplyGatewayTest`（多条文字、引用顺序、纯表情、错误码）、`MemeServiceTest`（精确标签和禁用项）、`AgentChatResponseTest`（四字段兼容性）以及默认上下文不加载 Rabbit Bean 的 `ChatServiceApplicationTests`。
- 修改主流程时保留解释关键决策的注释，并为 profile、重试/幂等、Redis Lua 批处理和协议边界补回归测试。不要只依赖手工联调。

## 常见故障排查

1. webhook 返回 401：检查原始请求体是否被改写、HMAC token 是否一致，以及 `X-Signature` 的 `sha1=` 前缀；未配置 webhook token 时才会放行。
2. 消息重复：检查 `eventId` 是否稳定、Redis 是否可用，以及锁/已处理 key 是否被误删；不要仅靠内存去重。
3. Agent 没收到消息：检查 Redis 缓冲 key、5 秒防抖/5 条批量阈值、刷新线程池和 Nacos `agent-service` 发现结果。
4. SnowLuma 不发消息：确认 `ChatTarget` 未丢失、`shouldReply` 为 true、`replies`/`meme` 至少一个有内容、引用 ID 能解析为当前批次的 `selfId:messageId`，且 OneBot 返回 `retcode=0`。
5. 修改 Rabbit 消费逻辑时同时验证手动 ACK、三次重试和 DLQ 行为，避免消息无限重入。

## 安全与工作树纪律

不要记录 access token、webhook token、Nacos/RabbitMQ 密码；不要提交 `application-local.yml`、`target/` 或真实 SnowLuma 配置。工作树可能已有用户修改，开始改动前先查看 `git status`/`git diff`，只触碰本次任务相关文件，不使用重置或清理命令覆盖他人改动。
