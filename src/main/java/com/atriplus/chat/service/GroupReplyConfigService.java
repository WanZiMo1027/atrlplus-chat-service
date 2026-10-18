package com.atriplus.chat.service;

import com.atriplus.chat.dto.GroupReplyConfig;
import com.atriplus.chat.mapper.GroupReplyConfigMapper;
import com.atriplus.chat.service.ai.RedisMessageBufferStore;
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
    private static final String RECEIVE_PREFIX = "atriplus:chat:group-reply:receive-enabled:";

    private final GroupReplyConfigMapper mapper;
    private final StringRedisTemplate redis;
    private final RedisMessageBufferStore bufferStore;

    /** 首次见群建档，名称变化时更新名称；两个开关均保留管理员已有设置。 */
    public boolean registerAndIsReceiveEnabled(long groupId, String groupName) {
        String name = normalizeName(groupName);
        String knownName = cachedName(groupId);
        if (knownName == null || (name != null && !name.equals(knownName))) {
            // 主要流程：仅首次见群或真实群名变化时写库，冲突更新只改群名。
            mapper.upsertName(groupId, name);
            GroupReplyConfig config = requiredConfig(groupId);
            cacheName(groupId, config.groupName());
            cacheConfigStatus(config);
            Boolean latest = cachedStatus(RECEIVE_PREFIX + groupId, groupId);
            return latest == null ? config.receiveEnabled() : latest;
        }
        return isReceiveEnabled(groupId);
    }

    /** 入队前检查；尚未建档的新群默认接收，后台取得群名后再建档。 */
    public boolean isReceiveEnabled(long groupId) {
        Boolean cached = cachedStatus(RECEIVE_PREFIX + groupId, groupId);
        if (cached != null) return cached;
        GroupReplyConfig config = mapper.findByGroupId(groupId);
        if (config == null) return true;
        cacheConfigStatus(config);
        Boolean latest = cachedStatus(RECEIVE_PREFIX + groupId, groupId);
        return latest == null ? config.receiveEnabled() : latest;
    }

    /** 回复开关只控制 Agent 是否生成回复以及 QQ 发送，不阻止聊天记录入库。 */
    public boolean isReplyEnabled(long groupId) {
        Boolean cached = cachedStatus(STATUS_PREFIX + groupId, groupId);
        if (cached != null) return cached;
        GroupReplyConfig config = mapper.findByGroupId(groupId);
        if (config == null) return false;
        cacheConfigStatus(config);
        Boolean latest = cachedStatus(STATUS_PREFIX + groupId, groupId);
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
        cacheValue(STATUS_PREFIX + groupId, Boolean.toString(config.enabled()), groupId);
        return config;
    }

    public GroupReplyConfig updateReceiveEnabled(long groupId, boolean enabled) {
        if (mapper.updateReceiveEnabled(groupId, enabled) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "群配置不存在");
        }
        GroupReplyConfig config = requiredConfig(groupId);
        cacheValue(RECEIVE_PREFIX + groupId, Boolean.toString(config.receiveEnabled()), groupId);
        if (!enabled) {
            // 主要流程：关闭接收时丢弃积压并撤销刷新租约，重新开启后不补发旧消息。
            bufferStore.discardGroup(groupId);
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

    private Boolean cachedStatus(String key, long groupId) {
        try {
            String value = redis.opsForValue().get(key);
            if ("true".equals(value)) return true;
            if ("false".equals(value)) return false;
            return null;
        } catch (DataAccessException exception) {
            log.warn("读取群开关缓存失败，回查数据库，groupId={}", groupId, exception);
            return null;
        }
    }

    private void cacheName(long groupId, String name) {
        cacheValue(NAME_PREFIX + groupId, name, groupId);
    }

    private void cacheValue(String key, String value, long groupId) {
        try {
            redis.opsForValue().set(key, value, CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn("群配置缓存同步失败，groupId={}", groupId, exception);
        }
    }

    private void cacheConfigStatus(GroupReplyConfig config) {
        cacheStatusIfAbsent(STATUS_PREFIX + config.groupId(), config.enabled(), config.groupId());
        cacheStatusIfAbsent(RECEIVE_PREFIX + config.groupId(), config.receiveEnabled(), config.groupId());
    }

    private void cacheStatusIfAbsent(String key, boolean enabled, long groupId) {
        try {
            redis.opsForValue().setIfAbsent(key, Boolean.toString(enabled), CACHE_TTL);
        } catch (DataAccessException exception) {
            log.warn("写入群回复状态缓存失败，groupId={}", groupId, exception);
        }
    }

}
