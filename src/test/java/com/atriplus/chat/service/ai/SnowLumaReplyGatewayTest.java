package com.atriplus.chat.service.ai;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SnowLumaReplyGatewayTest {

    @Test
    void sendsReplySegmentBeforeTextSegment() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://snowluma:3000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SnowLumaReplyGateway gateway = new SnowLumaReplyGateway(
                builder.build(),
                mock(MemeService.class)
        );

        server.expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(content().json("""
                        {
                          "group_id": 30003,
                          "message": [
                            {"type": "reply", "data": {"id": 100001}},
                            {"type": "text", "data": {"text": "我买NK就要把现在的琴出掉"}}
                          ]
                        }
                        """))
                .andRespond(withSuccess(
                        "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":999}}",
                        MediaType.APPLICATION_JSON
                ));

        // 主要流程：引用 ID 只附加到第一条文字消息。
        gateway.send(
                "qq:10001:group:30003",
                new ChatTarget(10001L, ChatType.GROUP, 30003L),
                new AgentChatResponse(
                        true,
                        List.of("我买NK就要把现在的琴出掉"),
                        "10001:100001",
                        null
                ),
                100001L
        );

        server.verify();
    }
}
