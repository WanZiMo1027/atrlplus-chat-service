package com.atriplus.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/** SnowLuma 模式专用的 HTTP 客户端配置。 */
@Configuration
@Profile("snowluma")
@EnableConfigurationProperties(SnowLumaProperties.class)
@Slf4j
public class SnowLumaConfig {

    @Bean
    public RestClient snowLumaRestClient(RestClient.Builder builder, SnowLumaProperties properties) {
        // 只记录凭据是否存在，绝不把 Token 内容写入日志。
        log.info("SnowLuma config loaded: baseUrl={}, accessTokenConfigured={}, webhookTokenConfigured={}",
                properties.baseUrl(),
                properties.accessToken() != null && !properties.accessToken().isBlank(),
                properties.webhookToken() != null && !properties.webhookToken().isBlank());
        RestClient.Builder configured = builder.baseUrl(properties.baseUrl());
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
            configured.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.accessToken()
            );
        }
        return configured.build();
    }
}
