package com.atriplus.chat.service;

import com.atriplus.chat.client.dto.AgentChatResponse;
import com.atriplus.chat.dto.ChatTarget;
import com.atriplus.chat.dto.ChatType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SnowLumaReplyGatewayTest {

    @Test
    void sendsGroupReplyAndChecksOneBotEnvelope() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://snowluma:3000");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SnowLumaReplyGateway gateway = new SnowLumaReplyGateway(builder.build());

        server.expect(once(), requestTo("http://snowluma:3000/send_group_msg"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"group_id\":30003,\"message\":\"回复内容\"}"))
                .andRespond(withSuccess(
                        "{\"status\":\"ok\",\"retcode\":0,\"data\":{\"message_id\":1}}",
                        MediaType.APPLICATION_JSON
                ));

        assertDoesNotThrow(() -> gateway.send(
                "qq:10001:group:30003",
                new ChatTarget(10001L, ChatType.GROUP, 30003L),
                new AgentChatResponse(true, "回复内容")
        ));
        server.verify();
    }
}
