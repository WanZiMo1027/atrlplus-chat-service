package com.atriplus.chat.service.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SnowLumaMessageLookupServiceTest {

    @Test
    void postsNegativeMessageIdAndReturnsDetail() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://snowluma.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://snowluma.test/get_msg"))
                .andExpect(content().json("{\"message_id\":-1037619435}"))
                .andRespond(withSuccess("""
                        {"status":"ok","retcode":0,"data":{"message_id":-1037619435,"message":[]}}
                        """, MediaType.APPLICATION_JSON));

        var result = new SnowLumaMessageLookupService(builder.build()).getMessage("-1037619435");

        assertEquals("-1037619435", result.orElseThrow().path("message_id").asText());
        server.verify();
    }

    @Test
    void rejectsMismatchedMessageId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://snowluma.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://snowluma.test/get_msg"))
                .andRespond(withSuccess("""
                        {"status":"ok","retcode":0,"data":{"message_id":2,"message":[]}}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(new SnowLumaMessageLookupService(builder.build()).getMessage("1").isEmpty());
        server.verify();
    }
}
