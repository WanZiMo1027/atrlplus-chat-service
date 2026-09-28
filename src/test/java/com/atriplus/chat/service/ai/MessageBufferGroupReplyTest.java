package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.AgentClient;
import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.client.dto.DescribedMessage;
import com.atriplus.chat.client.dto.OneBotMessageSnapshot;
import com.atriplus.chat.dto.BufferedMessage;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.service.GroupReplyConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MessageBufferGroupReplyTest {
    private static final long GROUP_ID = 1104399740L;
    private final ObjectMapper json = new ObjectMapper();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final AgentClient agent = mock(AgentClient.class);
    private final ReplyGateway gateway = mock(ReplyGateway.class);
    private final OneBotMessageDescriptionService describer = mock(OneBotMessageDescriptionService.class);
    private final SnowLumaMessageLookupService lookup = mock(SnowLumaMessageLookupService.class);
    private final GroupReplyConfigService groupConfig = mock(GroupReplyConfigService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<OneBotMessageDescriptionService> describerProvider = mock(ObjectProvider.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SnowLumaMessageLookupService> lookupProvider = mock(ObjectProvider.class);
    private final MessageBufferService buffer = new MessageBufferService(
            redis, json, agent, gateway, describerProvider, lookupProvider, groupConfig, Runnable::run);

    @Test
    void disabledGroupStopsBeforeDescriptionAndAgent() throws Exception {
        BufferedMessage message = groupMessage();
        JsonNode detail = groupDetail();
        prepare(message);
        when(lookup.getMessage("123")).thenReturn(Optional.of(detail));
        when(groupConfig.registerAndIsEnabled(GROUP_ID, "睦子开发xtt")).thenReturn(false);

        flush();

        verify(groupConfig).registerAndIsEnabled(GROUP_ID, "睦子开发xtt");
        verifyNoInteractions(describer, agent, gateway);
    }

    @Test
    void missingDetailStillChecksGroupByEventId() throws Exception {
        prepare(groupMessage());
        when(lookup.getMessage("123")).thenReturn(Optional.empty());
        when(groupConfig.registerAndIsEnabled(GROUP_ID, null)).thenReturn(false);

        flush();

        verify(groupConfig).registerAndIsEnabled(GROUP_ID, null);
        verifyNoInteractions(agent, gateway);
    }

    @Test
    void mismatchedDetailDoesNotSetWrongGroupName() throws Exception {
        prepare(groupMessage());
        JsonNode wrongGroup = json.readTree("""
                {"message_id":123,"message_type":"group","group_id":999,
                 "group_name":"其他群","message":[]}
                """);
        when(lookup.getMessage("123")).thenReturn(Optional.of(wrongGroup));
        when(groupConfig.registerAndIsEnabled(GROUP_ID, null)).thenReturn(false);

        flush();

        verify(groupConfig).registerAndIsEnabled(GROUP_ID, null);
        verifyNoInteractions(agent, gateway);
    }

    @Test
    void enabledGroupReusesDetailAndSendsReply() throws Exception {
        BufferedMessage message = groupMessage();
        JsonNode detail = groupDetail();
        prepare(message);
        when(lookup.getMessage("123")).thenReturn(Optional.of(detail));
        when(groupConfig.registerAndIsEnabled(GROUP_ID, "睦子开发xtt")).thenReturn(true);
        when(groupConfig.isEnabled(GROUP_ID)).thenReturn(true);
        when(describer.describeMessage(any(BufferedMessage.class), eq(Optional.of(detail))))
                .thenReturn(new DescribedMessage("描述", List.of()));
        AgentChatResponse response = new AgentChatResponse(true, List.of("你好"), null, null);
        when(agent.chat(any())).thenReturn(response);

        flush();

        verify(lookup, times(1)).getMessage("123");
        verify(describer).describeMessage(any(BufferedMessage.class), eq(Optional.of(detail)));
        verify(groupConfig).isEnabled(GROUP_ID);
        verify(gateway).send(eq("qq:1:group:" + GROUP_ID), eq(message.target()), same(response), isNull());
    }

    @Test
    void disablingWhileAgentRunsPreventsSend() throws Exception {
        prepare(groupMessage());
        when(lookup.getMessage("123")).thenReturn(Optional.of(groupDetail()));
        when(groupConfig.registerAndIsEnabled(GROUP_ID, "睦子开发xtt")).thenReturn(true);
        when(groupConfig.isEnabled(GROUP_ID)).thenReturn(false);
        when(describer.describeMessage(any(BufferedMessage.class), any()))
                .thenReturn(new DescribedMessage("描述", List.of()));
        when(agent.chat(any())).thenReturn(new AgentChatResponse(true, List.of("你好"), null, null));

        flush();

        verify(agent).chat(any());
        verifyNoInteractions(gateway);
    }

    @Test
    void databaseFailurePreventsAgentCall() throws Exception {
        prepare(groupMessage());
        when(lookup.getMessage("123")).thenReturn(Optional.of(groupDetail()));
        when(groupConfig.registerAndIsEnabled(GROUP_ID, "睦子开发xtt"))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        flush();

        verifyNoInteractions(agent, gateway);
    }

    @Test
    void privateAndLegacyMessagesBypassGroupConfig() throws Exception {
        BufferedMessage privateMessage = new BufferedMessage("1:123", "测试", "你好",
                new ChatTarget(1, ChatType.PRIVATE, 42), snapshot());
        prepare(privateMessage);
        when(describer.describeMessage(any(BufferedMessage.class)))
                .thenReturn(new DescribedMessage("私聊描述", List.of()));
        when(agent.chat(any())).thenReturn(new AgentChatResponse(true, List.of("收到"), null, null));
        flush();
        verifyNoInteractions(groupConfig, lookup);

        reset(agent, gateway, describer);
        prepare(new BufferedMessage("legacy", "测试", "旧消息", null, null));
        when(agent.chat(any())).thenReturn(new AgentChatResponse(true, List.of("收到"), null, null));
        flush();
        verifyNoInteractions(groupConfig, lookup, describer);
        verify(agent).chat(any());
        verify(gateway).send(anyString(), isNull(), any(), isNull());
    }

    private BufferedMessage groupMessage() {
        return new BufferedMessage("1:123", "测试", "你好",
                new ChatTarget(1, ChatType.GROUP, GROUP_ID), snapshot());
    }

    private OneBotMessageSnapshot snapshot() {
        return new OneBotMessageSnapshot("123", 42L, List.of(), "你好");
    }

    private JsonNode groupDetail() throws Exception {
        return json.readTree("""
                {"message_id":123,"message_type":"group","group_id":1104399740,
                 "group_name":"睦子开发xtt","message":[]}
                """);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void prepare(BufferedMessage message) throws Exception {
        when(describerProvider.getObject()).thenReturn(describer);
        when(lookupProvider.getObject()).thenReturn(lookup);
        doReturn(List.of(json.writeValueAsString(message))).when(redis)
                .execute(any(RedisScript.class), anyList(), any(), any(), any(), any());
    }

    private void flush() {
        ReflectionTestUtils.invokeMethod(buffer, "flush", "qq:1:group:" + GROUP_ID, true);
    }
}
