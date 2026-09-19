package com.atriplus.chat.service;

import com.atriplus.chat.config.SnowLumaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 验证 SnowLuma HTTP 事件推送的签名或 Bearer Token。 */
@Component
@Profile("snowluma")
@RequiredArgsConstructor
public class SnowLumaWebhookAuthenticator {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private final SnowLumaProperties properties;

    public boolean isAuthorized(String authorization, String signature, byte[] payload) {
        String token = properties.webhookToken();
        if (token == null || token.isBlank()) {
            return true;
        }

        // SnowLuma httpClients 使用 X-Signature: sha1=<HMAC-SHA1(raw-body)>。
        if (signature != null && matchesSignature(token, signature, payload)) {
            return true;
        }

        // 保留 OneBot 常见的 Bearer 方式，便于兼容其他客户端和旧配置。
        String expectedAuthorization = "Bearer " + token;
        return authorization != null && MessageDigest.isEqual(
                expectedAuthorization.getBytes(StandardCharsets.UTF_8),
                authorization.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean matchesSignature(String token, String signature, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(token.getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
            String expectedSignature = "sha1=" + HexFormat.of().formatHex(mac.doFinal(payload));
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", exception);
        }
    }
}
