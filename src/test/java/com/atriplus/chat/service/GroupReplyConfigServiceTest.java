package com.atriplus.chat.service;

import com.atriplus.chat.dto.GroupReplyConfig;
import com.atriplus.chat.mapper.GroupReplyConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupReplyConfigServiceTest {
    private static final long GROUP_ID = 1104399740L;
    private final GroupReplyConfigMapper mapper = mock(GroupReplyConfigMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final Map<String, String> cache = new HashMap<>();
    private final GroupReplyConfigService service = new GroupReplyConfigService(mapper, redis);

    @BeforeEach
    void cacheOperations() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> cache.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            cache.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            if (cache.containsKey(key)) return false;
            cache.put(key, invocation.getArgument(1));
            return true;
        });
    }

    @Test
    void firstGroupIsEnabledAndWarmCacheAvoidsDatabase() {
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(new GroupReplyConfig(GROUP_ID, "睦子开发xtt", true));

        assertTrue(service.registerAndIsEnabled(GROUP_ID, "睦子开发xtt"));
        assertTrue(service.registerAndIsEnabled(GROUP_ID, "睦子开发xtt"));

        verify(mapper, times(1)).upsertName(GROUP_ID, "睦子开发xtt");
        verify(mapper, times(1)).findByGroupId(GROUP_ID);
    }

    @Test
    void changedNameDoesNotReenableDisabledGroup() {
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(
                new GroupReplyConfig(GROUP_ID, "旧名称", false),
                new GroupReplyConfig(GROUP_ID, "新名称", false));

        assertFalse(service.registerAndIsEnabled(GROUP_ID, "旧名称"));
        assertFalse(service.registerAndIsEnabled(GROUP_ID, "新名称"));

        verify(mapper).upsertName(GROUP_ID, "新名称");
        assertEquals("false", cache.get("atriplus:chat:group-reply:enabled:" + GROUP_ID));
    }

    @Test
    void missingNameUsesDatabaseNameAndDoesNotReplaceIt() {
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(new GroupReplyConfig(GROUP_ID, "已有群名", true));

        assertTrue(service.registerAndIsEnabled(GROUP_ID, null));
        verify(mapper).upsertName(GROUP_ID, null);
        assertEquals("已有群名", cache.get("atriplus:chat:group-reply:name:" + GROUP_ID));
    }

    @Test
    void adminUpdateOverwritesCacheAndLateFillCannotUndoIt() {
        when(mapper.updateEnabled(GROUP_ID, false)).thenReturn(1);
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(new GroupReplyConfig(GROUP_ID, "测试群", false));

        service.updateEnabled(GROUP_ID, false);
        values.setIfAbsent("atriplus:chat:group-reply:enabled:" + GROUP_ID, "true", Duration.ofMinutes(5));

        assertFalse(service.isEnabled(GROUP_ID));
        verify(mapper, times(1)).findByGroupId(GROUP_ID);
    }

    @Test
    void expiredStatusIsReadFromDatabase() {
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(new GroupReplyConfig(GROUP_ID, "测试群", false));

        assertFalse(service.isEnabled(GROUP_ID));
        assertFalse(service.isEnabled(GROUP_ID));
        verify(mapper, times(1)).findByGroupId(GROUP_ID);
    }

    @Test
    void redisReadFailureFallsBackToDatabaseButDatabaseFailureIsNotAllowed() {
        when(values.get(anyString())).thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        when(mapper.findByGroupId(GROUP_ID)).thenReturn(new GroupReplyConfig(GROUP_ID, "测试群", false));
        assertFalse(service.isEnabled(GROUP_ID));

        when(mapper.findByGroupId(GROUP_ID)).thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.isEnabled(GROUP_ID));
    }

    @Test
    void updateUnknownGroupReturnsNotFound() {
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.updateEnabled(GROUP_ID, false)).getStatusCode().value());
    }
}
