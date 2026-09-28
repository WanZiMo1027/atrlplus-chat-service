package com.atriplus.chat.service;

import com.atriplus.chat.dto.GroupReplyConfig;
import com.atriplus.chat.mapper.GroupReplyConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/** 群配置以 PostgreSQL 为准，Redis 只缓存热群的名称和开关。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroupReplyConfigService {
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String NAME_PREFIX = "atriplus:chat:group-reply:name:";
    private static final String STATUS_PREFIX = "atriplus:chat:group-reply:enabled:";

    private final GroupReplyConfigMapper mapper;
    private final StringRedisTemplate redis;

    /** 首次见群建档，名称变化时更新名称；这两种写入都不覆盖 enabled。 */
    public boolean registerAndIsEnabled(long groupId, String groupName) {
        String name = normalizeName(groupName);
        String knownName = cachedName(groupId);
        if (knownName == null || (name != null && !name.equals(knownName))) {
            // 主要流程：仅首次见群或真实群名变化时写库，冲突更新只改群名。
            mapper.upsertName(groupId, name);
            GroupReplyConfig config = requiredConfig(groupId);
            cacheName(groupId, config.groupName());
            cacheStatusIfAbsent(groupId, config.enabled());
            Boolean latest = cachedStatus(groupId);
            return latest == null ? config.enabled() : latest;
        }
        return isEnabled(groupId);
    }

    /** 发送前重查缓存或数据库，避免处理 Agent 期间被关闭的群继续收到回复。 */
    public boolean isEnabled(long groupId) {
        Boolean cached = cachedStatus(groupId);
        if (cached != null) return cached;
        GroupReplyConfig config = mapper.findByGroupId(groupId);
        if (config == null) return false;
        cacheStatusIfAbsent(groupId, config.enabled());
        Boolean latest = cachedStatus(groupId);
        return latest == null ? config.enabled() : latest;
    }

    public List<GroupReplyConfig> listAll() {
        return mapper.listAll();
    }

    public GroupReplyConfig updateEnabled(long groupId, boolean enabled) {
        if (mapper.updateEnabled(groupId, enabled) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "群配置不存在");
        }
        GroupReplyConfig config = requiredConfig(groupId);
        // 主要流程：管理员先写数据库，再覆盖共享缓存，让后续消息立即读取新状态。
        try {
            redis.opsForValue().set(statusKey(groupId), Boolean.toString(enabled), CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn("群回复状态已入库，但 Redis 缓存同步失败，groupId={}", groupId, exception);
        }
        return config;
    }

    private GroupReplyConfig requiredConfig(long groupId) {
        GroupReplyConfig config = mapper.findByGroupId(groupId);
        if (config == null) throw new IllegalStateException("群配置写入后未找到，groupId=" + groupId);
        return config;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 255 ? value : value.substring(0, 255);
    }

    private String cachedName(long groupId) {
        try {
            return redis.opsForValue().get(NAME_PREFIX + groupId);
        } catch (DataAccessException exception) {
            log.warn("读取群名称缓存失败，回查数据库，groupId={}", groupId, exception);
            return null;
        }
    }

    private Boolean cachedStatus(long groupId) {
        try {
            String value = redis.opsForValue().get(statusKey(groupId));
            if ("true".equals(value)) return true;
            if ("false".equals(value)) return false;
            return null;
        } catch (DataAccessException exception) {
            log.warn("读取群回复状态缓存失败，回查数据库，groupId={}", groupId, exception);
            return null;
        }
    }

    private void cacheName(long groupId, String name) {
        try {
            redis.opsForValue().set(NAME_PREFIX + groupId, name, CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn("写入群名称缓存失败，groupId={}", groupId, exception);
        }
    }

    private void cacheStatusIfAbsent(long groupId, boolean enabled) {
        try {
            redis.opsForValue().setIfAbsent(statusKey(groupId), Boolean.toString(enabled), CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn("写入群回复状态缓存失败，groupId={}", groupId, exception);
        }
    }

    private String statusKey(long groupId) {
        return STATUS_PREFIX + groupId;
    }
}
