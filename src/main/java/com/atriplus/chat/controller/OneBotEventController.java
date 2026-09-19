package com.atriplus.chat.controller;

import com.atriplus.chat.dto.IncomingChatMessage;
import com.atriplus.chat.event.onebot.OneBotEvent;
import com.atriplus.chat.service.EventIdempotencyService;
import com.atriplus.chat.service.MessageIngestService;
import com.atriplus.chat.service.OneBotEventMapper;
import com.atriplus.chat.service.SnowLumaWebhookAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Optional;

/** SnowLuma HTTP 事件上报入口。 */
@RestController
@RequestMapping("/onebot/v11/events")
@Profile("snowluma")
@RequiredArgsConstructor
public class OneBotEventController {

    private final OneBotEventMapper eventMapper;
    private final MessageIngestService messageIngestService;
    private final EventIdempotencyService eventIdempotencyService;
    private final SnowLumaWebhookAuthenticator webhookAuthenticator;
    private final ObjectMapper objectMapper;

    /**
     * 处理 SnowLuma OneBot 事件上报。
     * @param authorization Bearer 授权头，作为兼容鉴权方式。
     * @param signature SnowLuma HTTP 推送携带的 HMAC-SHA1 签名。
     * @param payload OneBot 事件的原始 JSON 字节。
     * @return ResponseEntity<Void> 204 No Content 成功。
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody byte[] payload) {
        // 主要流程：先用原始请求体验证签名，成功后才解析和处理事件。
        if (!webhookAuthenticator.isAuthorized(authorization, signature, payload)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        final OneBotEvent event;
        try {
            event = objectMapper.readValue(payload, OneBotEvent.class);
        } catch (IOException exception) {
            return ResponseEntity.badRequest().build();
        }

        Optional<IncomingChatMessage> mapped = eventMapper.map(event);
        if (mapped.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        IncomingChatMessage message = mapped.get();
        if (eventIdempotencyService.isProcessed(message.eventId())) {
            return ResponseEntity.ok().build();
        }
        if (!eventIdempotencyService.tryStartProcessing(message.eventId())) {
            return ResponseEntity.accepted().build();
        }

        try {
            // 主要流程：事件只需可靠写入 Redis 缓冲，Agent 调用由后台刷新任务完成。
            messageIngestService.ingest(message);
            eventIdempotencyService.markProcessed(message.eventId());
            return ResponseEntity.ok().build();
        } finally {
            eventIdempotencyService.releaseProcessingLock(message.eventId());
        }
    }

}
