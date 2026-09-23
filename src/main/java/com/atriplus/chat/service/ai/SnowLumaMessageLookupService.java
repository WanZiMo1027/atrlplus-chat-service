package com.atriplus.chat.service.ai;


import com.atriplus.chat.client.dto.OneBotActionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

@Service
@Profile("snowluma")
@Slf4j
public class SnowLumaMessageLookupService {

    private final RestClient restClient;

    public SnowLumaMessageLookupService(@Qualifier("snowLumaLookupRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 查询消息详情
     * @param messageId 消息ID
     * @return 消息详情
     */
    public Optional<JsonNode> getMessage(String messageId) {
        try {
            long id = Long.parseLong(messageId); // 负数 ID 也可以
            OneBotActionResponse response = restClient.post()
                    .uri("/get_msg")
                    .body(Map.of("message_id", id))
                    .retrieve()
                    .body(OneBotActionResponse.class);

            if (response == null || response.retcode() != 0
                    || response.data() == null || !response.data().isObject()) {
                log.warn("get_msg 未取得消息详情，messageId={}, retcode={}",
                        messageId, response == null ? "无响应" : response.retcode());
                return Optional.empty();
            }

            JsonNode data = response.data();
            if (!messageId.equals(data.path("message_id").asText())) {
                log.warn("get_msg 返回了不匹配的消息 ID，messageId={}", messageId);
                return Optional.empty();
            }

            return Optional.of(data);
        } catch (NumberFormatException | RestClientException exception) {
            log.warn("get_msg 查询失败，messageId={}", messageId, exception);
            return Optional.empty();
        }
    }
}
