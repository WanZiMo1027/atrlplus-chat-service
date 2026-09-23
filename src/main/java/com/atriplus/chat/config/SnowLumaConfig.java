package com.atriplus.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** SnowLuma 模式专用的 HTTP 客户端配置。 */
@Configuration
@Profile("snowluma")
@EnableConfigurationProperties(SnowLumaProperties.class)
@Slf4j
public class SnowLumaConfig {

    @Bean
    @Primary
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

    @Bean("snowLumaLookupRestClient")
    public RestClient snowLumaLookupRestClient(SnowLumaProperties properties) {
        // 详情查询在后台执行；独立超时避免 SnowLuma 卡住时耗尽刷新线程。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1500);
        requestFactory.setReadTimeout(2000);
        RestClient.Builder configured = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);
        if (properties.accessToken() != null && !properties.accessToken().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken());
        }
        return configured.build();
    }
}
