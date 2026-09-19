package com.atriplus.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SnowLuma HTTP API 与事件上报鉴权配置。 */
@ConfigurationProperties(prefix = "snowluma")
public record SnowLumaProperties(
        String baseUrl,
        String accessToken,
        String webhookToken
) {
}
