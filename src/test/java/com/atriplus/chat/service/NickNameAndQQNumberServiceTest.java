package com.atriplus.chat.service;

import com.atriplus.chat.mapper.NickNameAndQQNumber;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NickNameAndQQNumberServiceTest {

    private final NickNameAndQQNumber mapper = mock(NickNameAndQQNumber.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final NickNameAndQQNumberService service =
            new NickNameAndQQNumberService(mapper, redisTemplate);

    @Test
    void returnsCachedNickNameWithoutQueryingPostgres() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("atriplus:chat:qq-nickname:3763604733")).thenReturn("大肥鱼");

        assertEquals(Optional.of("大肥鱼"), service.getNickName("3763604733"));
        verify(mapper, never()).getNickName(3763604733L);
    }

    @Test
    void loadsNickNameFromPostgresAndCachesIt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(mapper.getNickName(3763604733L)).thenReturn(List.of("大肥鱼"));

        assertEquals(Optional.of("大肥鱼"), service.getNickName("3763604733"));
        verify(valueOperations).set(
                "atriplus:chat:qq-nickname:3763604733",
                "大肥鱼",
                Duration.ofHours(6)
        );
    }

    @Test
    void ignoresNonNumericOneBotAtTargets() {
        assertEquals(Optional.empty(), service.getNickName("all"));
        verify(mapper, never()).getNickName(org.mockito.ArgumentMatchers.anyLong());
    }
}
