package com.atriplus.chat.service;

import com.atriplus.chat.mapper.NickNameAndQQNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/** 查询并缓存 QQ 号对应的业务昵称。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NickNameAndQQNumberService {

    private static final String CACHE_PREFIX = "atriplus:chat:qq-nickname:";
    private static final String NOT_FOUND = "__NOT_FOUND__";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration NOT_FOUND_TTL = Duration.ofMinutes(5);

    private final NickNameAndQQNumber nickNameAndQQNumberMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 根据群号和 QQ 号查询业务昵称。
     *
     * @param groupId 群号
     * @param qqNumber QQ 号文本
     * @return 对应昵称；不存在或查询失败时为空
     */
    public Optional<String> getNickName(long groupId, String qqNumber) {
        Long parsedQqNumber = parseQqNumber(qqNumber);
        if (groupId <= 0 || parsedQqNumber == null) {
            return Optional.empty();
        }

        String cacheKey = cacheKey(groupId, parsedQqNumber);
        Optional<String> cached = getCachedNickName(cacheKey, groupId, parsedQqNumber);
        if (cached != null) {
            return cached;
        }

        // 主要流程：Redis 未命中后查询 PostgreSQL，并将结果回填缓存。
        try {
            String result = nickNameAndQQNumberMapper.getNickName(groupId, parsedQqNumber);
            Optional<String> nickName = result == null || result.isBlank()
                    ? Optional.empty()
                    : Optional.of(result.strip());
            cacheNickName(cacheKey, groupId, parsedQqNumber, nickName);
            return nickName;
        } catch (DataAccessException exception) {
            log.warn("查询 QQ 昵称失败，groupId={}, qqNumber={}", groupId, parsedQqNumber, exception);
            return Optional.empty();
        }
    }

    /**
     * 记录群成员使用过的昵称；相同昵称再次出现时刷新其最近使用时间。
     *
     * @param groupId 群号
     * @param qqNumber QQ 号文本
     * @param nickName 当前 QQ 昵称
     */
    public void saveNickName(long groupId, String qqNumber, String nickName) {
        Long parsedQqNumber = parseQqNumber(qqNumber);
        String normalizedNickName = normalizeNickName(qqNumber, nickName);
        if (groupId <= 0 || parsedQqNumber == null || normalizedNickName == null) {
            return;
        }

        // 主要流程：保存昵称历史；相同昵称再次出现时由 Mapper 刷新 updated_at。
        try {
            nickNameAndQQNumberMapper.upsertNickName(groupId, parsedQqNumber, normalizedNickName);
            // 当前昵称应立即覆盖旧缓存，也能清除先前写入的“未找到”缓存。
            cacheNickName(
                    cacheKey(groupId, parsedQqNumber),
                    groupId,
                    parsedQqNumber,
                    Optional.of(normalizedNickName)
            );
        } catch (DataAccessException exception) {
            log.warn("保存 QQ 昵称失败，groupId={}, qqNumber={}", groupId, parsedQqNumber, exception);
        }
    }

    /**
     * 返回 null 表示缓存未命中，Optional.empty() 表示已缓存“没有昵称”。
     */
    private Optional<String> getCachedNickName(String cacheKey, long groupId, Long qqNumber) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            return NOT_FOUND.equals(cached) ? Optional.empty() : Optional.of(cached);
        } catch (DataAccessException exception) {
            log.warn("读取 QQ 昵称缓存失败，将回源 PostgreSQL，groupId={}, qqNumber={}",
                    groupId, qqNumber, exception);
            return null;
        }
    }

    private void cacheNickName(
            String cacheKey,
            long groupId,
            Long qqNumber,
            Optional<String> nickName
    ) {
        try {
            if (nickName.isPresent()) {
                redisTemplate.opsForValue().set(cacheKey, nickName.get(), CACHE_TTL);
            } else {
                redisTemplate.opsForValue().set(cacheKey, NOT_FOUND, NOT_FOUND_TTL);
            }
        } catch (DataAccessException exception) {
            log.warn("写入 QQ 昵称缓存失败，groupId={}, qqNumber={}", groupId, qqNumber, exception);
        }
    }

    private String cacheKey(long groupId, Long qqNumber) {
        return CACHE_PREFIX + groupId + ':' + qqNumber;
    }

    private Long parseQqNumber(String qqNumber) {
        if (qqNumber == null || !qqNumber.matches("\\d+")) {
            return null;
        }
        try {
            return Long.valueOf(qqNumber);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeNickName(String qqNumber, String nickName) {
        if (nickName == null || nickName.isBlank()) {
            return null;
        }
        String normalized = nickName.strip();
        // OneBot 无昵称时可能以 QQ 号回退，不能把号码本身当作昵称入库。
        return normalized.equals(qqNumber) ? null : normalized;
    }
}
