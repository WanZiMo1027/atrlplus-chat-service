package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.AgentClient;
import com.atriplus.chat.client.dto.AgentChatRequest;
import com.atriplus.chat.client.dto.ChatMessageItem;
import com.atriplus.chat.client.dto.DescribedMessage;
import com.atriplus.chat.config.MessageBufferProperties;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.service.GroupReplyConfigService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 消息聚合与会话串行调度；等待防抖或最短间隔时不占用刷新线程。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageBufferService {
    private final RedisMessageBufferStore bufferStore;
    private final ObjectMapper objectMapper;
    private final AgentClient agentClient;
    private final ReplyGateway replyGateway;
    private final ObjectProvider<OneBotMessageDescriptionService> messageDescriber;
    private final ObjectProvider<SnowLumaMessageLookupService> messageLookup;
    private final GroupReplyConfigService groupReplyConfigService;
    private final TaskExecutor messageFlushExecutor;
    private final ScheduledExecutorService leaseScheduler;
    private final Clock clock;
    private final MessageBufferProperties properties;

    /** 新消息入口只入队，取消五条立即刷新，webhook 不等待模型。 */
    public void addMessage(String conversationId, BufferedMessage message) {
        try {
            long size = bufferStore.add(conversationId, objectMapper.writeValueAsString(message), clock.millis());
            log.info("消息进入缓冲区: conversationId={}, count={}", conversationId, size);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("序列化消息失败", exception);
        }
    }

    /** 每秒扫描到期会话；Redis 先抢占资格，防止重复任务进入线程池。 */
    @Scheduled(fixedDelay = 1000)
    public void flushExpiredBuffers() {
        try {
            for (String conversationId : bufferStore.due(clock.millis())) triggerFlush(conversationId);
        } catch (Exception exception) {
            log.error("扫描消息缓冲区失败，未绕过 Redis 调用 Agent", exception);
        }
    }

    /** 处理资格覆盖线程池排队，提交失败时保留全部消息并恢复到期索引。 */
    private void triggerFlush(String conversationId) {
        ProcessingLease lease = new ProcessingLease(conversationId);
        try {
            if (!bufferStore.acquire(conversationId, lease.token, clock.millis())) return;
            try {
                lease.renewal = leaseScheduler.scheduleAtFixedRate(() -> renew(lease),
                        properties.lockRenewMillis(), properties.lockRenewMillis(), TimeUnit.MILLISECONDS);
                messageFlushExecutor.execute(() -> flush(lease));
            } catch (Exception exception) {
                finish(lease);
                throw exception;
            }
        } catch (Exception exception) {
            log.error("提交消息处理失败，conversationId={}", conversationId, exception);
        }
    }

    /** 续租独立于刷新线程，失败后禁止新的调用或发送。 */
    private void renew(ProcessingLease lease) {
        if (!lease.valid.get()) return;
        try {
            if (!bufferStore.renew(lease.id, lease.token, clock.millis())) {
                lease.valid.set(false);
                log.warn("消息处理锁已失效，conversationId={}", lease.id);
            }
        } catch (Exception exception) {
            lease.valid.set(false);
            log.error("消息处理锁续租失败，conversationId={}", lease.id, exception);
        }
    }

    /** 一轮包含描述、模型决策与发送；仅消费预算允许的队列前缀。 */
    private void flush(ProcessingLease lease) {
        long startedAt = clock.millis();
        List<String> snapshot = List.of();
        boolean consumed = false;
        int batchCount = 0;
        try {
            if (!owns(lease)) return;
            snapshot = bufferStore.begin(lease.id, lease.token, startedAt);
            if (snapshot.isEmpty()) return;
            List<BufferedMessage> messages = snapshot.stream().map(this::parseMessage).toList();
            BufferedMessage firstGroupMessage = messages.stream()
                    .filter(message -> message.target() != null && message.target().type() == ChatType.GROUP
                            && message.snapshot() != null).findFirst().orElse(null);
            Long groupId = firstGroupMessage == null ? null : firstGroupMessage.target().targetId();
            Optional<JsonNode> firstDetail = Optional.empty();
            if (firstGroupMessage != null) {
                // 主要流程：先查询群开关；关闭的群不生成描述，也不请求 Agent。
                firstDetail = messageLookup.getObject().getMessage(firstGroupMessage.snapshot().messageId());
                String groupName = firstDetail
                        .filter(detail -> "group".equals(detail.path("message_type").asText())
                                && detail.path("group_id").asLong(-1) == groupId)
                        .map(detail -> detail.path("group_name").asText(null)).orElse(null);
                if (!groupReplyConfigService.registerAndIsEnabled(groupId, groupName)) return;
            }

            AgentBatchBudget budget = new AgentBatchBudget(properties);
            List<ChatMessageItem> items = new ArrayList<>();
            for (BufferedMessage message : messages) {
                if (!owns(lease)) return;
                // 主要流程：首条群消息复用详情；旧链路没有快照时仍只传文字。
                DescribedMessage described = message.snapshot() == null
                        ? new DescribedMessage(message.text(), List.of())
                        : message == firstGroupMessage
                                ? messageDescriber.getObject().describeMessage(message, firstDetail)
                                : messageDescriber.getObject().describeMessage(message);
                ChatMessageItem accepted = budget.accept(new ChatMessageItem(message.eventId(),
                        message.senderName(), described.text(), described.images()));
                if (accepted == null) break;
                items.add(accepted);
            }
            batchCount = items.size();
            // 主要流程：原子消费连续前缀，新消息与超预算后缀不会被旧快照删除。
            if (!owns(lease) || !bufferStore.consume(lease.id, lease.token, batchCount)) {
                lease.valid.set(false);
                return;
            }
            consumed = true;
            if (items.isEmpty() || !owns(lease)) return;

            var response = agentClient.chat(new AgentChatRequest(lease.id, List.copyOf(items)));
            List<BufferedMessage> selected = messages.subList(0, batchCount);
            ChatTarget target = selected.stream().map(BufferedMessage::target)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            // 引用只能映射本轮实际发送给 Agent 的消息，不能引用预算外后缀。
            Long replyToMessageId = resolveReplyToMessageId(selected, response.replyToEventId());
            if (groupId != null && !groupReplyConfigService.isEnabled(groupId)) return;
            if (!owns(lease)) return;
            replyGateway.sendWhileOwned(lease.id, target, response, replyToMessageId, () -> owns(lease));
        } catch (Exception exception) {
            // 沿用现有失败策略，不自动重放失败批次或部分已发送的回复。
            log.error("描述消息、调用 Agent 或发送回复失败，conversationId={}", lease.id, exception);
        } finally {
            if (!consumed && !snapshot.isEmpty() && lease.valid.get()) {
                try {
                    // 描述或群开关阶段失败只丢弃当前快照，新消息继续排队。
                    bufferStore.consume(lease.id, lease.token, snapshot.size());
                } catch (Exception exception) {
                    log.error("清理失败快照失败，conversationId={}", lease.id, exception);
                }
            }
            long nextAt = finish(lease);
            log.info("消息批次结束: conversationId={}, count={}, durationMillis={}, nextAt={}",
                    lease.id, batchCount, clock.millis() - startedAt, nextAt);
        }
    }

    /** 请求和发送之前都检查资格；Redis 故障时不允许绕过处理锁。 */
    private boolean owns(ProcessingLease lease) {
        if (!lease.valid.get()) return false;
        try {
            if (!bufferStore.owns(lease.id, lease.token)) lease.valid.set(false);
            return lease.valid.get();
        } catch (Exception exception) {
            lease.valid.set(false);
            throw exception;
        }
    }

    /** 统一结束所有分支，原子安排下一轮；等待由到期扫描承担。 */
    private long finish(ProcessingLease lease) {
        if (lease.renewal != null) lease.renewal.cancel(false);
        try {
            return bufferStore.finish(lease.id, lease.token, clock.millis());
        } catch (Exception exception) {
            log.error("结束消息批次失败，等待锁租期恢复，conversationId={}", lease.id, exception);
            return -1;
        }
    }

    /** 解析队列中的兼容消息格式。 */
    private BufferedMessage parseMessage(String json) {
        try {
            return objectMapper.readValue(json, BufferedMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("BufferedMessage JSON 解析失败", exception);
        }
    }

    /** 验证引用事件属于本轮，并提取 OneBot 的数字消息 ID。 */
    private Long resolveReplyToMessageId(List<BufferedMessage> messages, String replyToEventId) {
        if (replyToEventId == null || replyToEventId.isBlank()) return null;
        return messages.stream().filter(message -> replyToEventId.equals(message.eventId()))
                .findFirst().map(BufferedMessage::eventId).map(this::extractOneBotMessageId)
                .orElseGet(() -> {
                    log.warn("replyToEventId 无法映射到本轮消息，已忽略: {}", replyToEventId);
                    return null;
                });
    }

    /** eventId 格式为 selfId:messageId，提取最后一段。 */
    private Long extractOneBotMessageId(String eventId) {
        int index = eventId.lastIndexOf(':');
        if (index < 0 || index == eventId.length() - 1) return null;
        try {
            return Long.parseLong(eventId.substring(index + 1));
        } catch (NumberFormatException exception) {
            log.warn("无法从 eventId 提取 OneBot message_id: {}", eventId);
            return null;
        }
    }

    /** 每轮独立持有者标识；续租与刷新线程共享失效状态。 */
    private static final class ProcessingLease {
        private final String id;
        private final String token = UUID.randomUUID().toString();
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private ScheduledFuture<?> renewal;

        private ProcessingLease(String id) {
            this.id = id;
        }
    }
}
