package com.atriplus.chat.service;

import com.atriplus.chat.config.SnowLumaProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowLumaWebhookAuthenticatorTest {

    private static final String TOKEN = "webhook-secret";
    private final SnowLumaWebhookAuthenticator authenticator = new SnowLumaWebhookAuthenticator(
            new SnowLumaProperties("http://127.0.0.1:3000", "api-secret", TOKEN)
    );

    @Test
    void acceptsSnowLumaHmacSha1Signature() throws Exception {
        byte[] payload = "{\"post_type\":\"message\",\"raw_message\":\"你好\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertTrue(authenticator.isAuthorized(null, sign(payload), payload));
    }

    @Test
    void retainsBearerCompatibility() {
        assertTrue(authenticator.isAuthorized("Bearer " + TOKEN, null, new byte[0]));
    }

    @Test
    void rejectsMissingOrInvalidCredentials() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        assertFalse(authenticator.isAuthorized(null, null, payload));
        assertFalse(authenticator.isAuthorized(null, "sha1=invalid", payload));
        assertFalse(authenticator.isAuthorized("Bearer wrong", null, payload));
    }

    private String sign(byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(TOKEN.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return "sha1=" + HexFormat.of().formatHex(mac.doFinal(payload));
    }
}
