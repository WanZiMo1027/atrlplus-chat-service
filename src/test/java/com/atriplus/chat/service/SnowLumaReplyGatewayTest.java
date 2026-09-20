package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import com.atriplus.chat.dto.Meme;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SnowLumaReplyGatewayTest {

    private static final ChatTarget GROUP_TARGET = new ChatTarget(10001L, ChatType.GROUP, 30003L);
    private static final ChatTarget PRIVATE_TARGET = new ChatTarget(10001L, ChatType.PRIVATE, 20002L);
    private static final String MEME_URL = "https://example.test/meme.gif";

    @Test
    void sendsTextOnlyGroupReply() {
        TestContext context = context();
        context.server().expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"group_id\":30003,\"message\":\"回复内容\"}"))
                .andRespond(oneBotSuccess());

        assertDoesNotThrow(() -> context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(true, "回复内容", null)
        ));
        context.server().verify();
    }

    @Test
    void sendsTextThenMemeAsSeparateMessages() {
        TestContext context = contextWithMeme("开心");
        context.server().expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(content().json("{\"group_id\":30003,\"message\":\"先发文字\"}"))
                .andRespond(oneBotSuccess());
        context.server().expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(content().json(groupImageJson()))
                .andRespond(oneBotSuccess());

        context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(true, "先发文字", "开心")
        );
        context.server().verify();
    }

    @Test
    void sendsMemeOnlyToPrivateChat() {
        TestContext context = contextWithMeme("开心");
        context.server().expect(once(), requestTo("http://snowluma:3000/send_private_msg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(privateImageJson()))
                .andRespond(oneBotSuccess());

        context.gateway().send(
                "qq:10001:private:20002",
                PRIVATE_TARGET,
                new AgentChatResponse(true, null, "开心")
        );
        context.server().verify();
    }

    @Test
    void missingMemeDoesNotPreventTextReply() {
        TestContext context = context();
        when(context.memeService().findByTag("不存在")).thenReturn(Optional.empty());
        context.server().expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(content().json("{\"group_id\":30003,\"message\":\"仍然回复\"}"))
                .andRespond(oneBotSuccess());

        assertDoesNotThrow(() -> context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(true, "仍然回复", "不存在")
        ));
        context.server().verify();
    }

    @Test
    void missingMemeOnlyProducesNoRequest() {
        TestContext context = context();
        when(context.memeService().findByTag("不存在")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(true, null, "不存在")
        ));
        context.server().verify();
    }

    @Test
    void shouldReplyFalseSkipsTextAndMeme() {
        TestContext context = context();

        context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(false, "不应发送", "开心")
        );

        context.server().verify();
        verifyNoInteractions(context.memeService());
    }

    @Test
    void nonZeroOneBotRetcodeIsPropagated() {
        TestContext context = context();
        context.server().expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andRespond(withSuccess(
                        "{\"status\":\"failed\",\"retcode\":100,\"message\":\"failed\",\"wording\":\"denied\"}",
                        MediaType.APPLICATION_JSON
                ));

        assertThrows(IllegalStateException.class, () -> context.gateway().send(
                "qq:10001:group:30003",
                GROUP_TARGET,
                new AgentChatResponse(true, "回复内容", null)
        ));
    }

    private TestContext contextWithMeme(String tag) {
        TestContext context = context();
        Meme meme = new Meme("meme-1", "开心表情", List.of(tag), MEME_URL, "md5", true);
        when(context.memeService().findByTag(tag)).thenReturn(Optional.of(meme));
        return context;
    }

    private TestContext context() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://snowluma:3000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MemeService memeService = mock(MemeService.class);
        return new TestContext(
                new SnowLumaReplyGateway(builder.build(), memeService),
                server,
                memeService
        );
    }

    private static ResponseCreator oneBotSuccess() {
        return withSuccess(
                "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",
                MediaType.APPLICATION_JSON
        );
    }

    private static String groupImageJson() {
        return "{\"group_id\":30003,\"message\":[{\"type\":\"image\",\"data\":{"
                + "\"file\":\"" + MEME_URL + "\",\"sub_type\":1,\"summary\":\"[动画表情]\"}}]}";
    }

    private static String privateImageJson() {
        return "{\"user_id\":20002,\"message\":[{\"type\":\"image\",\"data\":{"
                + "\"file\":\"" + MEME_URL + "\",\"sub_type\":1,\"summary\":\"[动画表情]\"}}]}";
    }

    private record TestContext(
            SnowLumaReplyGateway gateway,
            MockRestServiceServer server,
            MemeService memeService
    ) {
    }
}
