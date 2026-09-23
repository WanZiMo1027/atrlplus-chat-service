package com.atriplus.chat.service;

import com.atriplus.chat.mapper.NickNameAndQQNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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
     * 根据 QQ 号查询业务昵称。
     *
     * @param qqNumber QQ 号文本
     * @return 对应昵称；不存在或查询失败时为空
     */
    public Optional<String> getNickName(String qqNumber) {
        Long parsedQqNumber = parseQqNumber(qqNumber);
        if (parsedQqNumber == null) {
            return Optional.empty();
        }

        String cacheKey = CACHE_PREFIX + parsedQqNumber;
        Optional<String> cached = getCachedNickName(cacheKey, parsedQqNumber);
        if (cached != null) {
            return cached;
        }

        // 主要流程：Redis 未命中后查询 PostgreSQL，并将结果回填缓存。
        try {
            List<String> nickNames = nickNameAndQQNumberMapper.getNickName(parsedQqNumber);
            Optional<String> nickName = nickNames == null
                    ? Optional.empty()
                    : nickNames.stream()
                            .filter(value -> value != null && !value.isBlank())
                            .map(String::strip)
                            .findFirst();
            cacheNickName(cacheKey, parsedQqNumber, nickName);
            return nickName;
        } catch (DataAccessException exception) {
            log.warn("查询 QQ 昵称失败，qqNumber={}", parsedQqNumber, exception);
            return Optional.empty();
        }
    }

    /**
     * 返回 null 表示缓存未命中，Optional.empty() 表示已缓存“没有昵称”。
     */
    private Optional<String> getCachedNickName(String cacheKey, Long qqNumber) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            return NOT_FOUND.equals(cached) ? Optional.empty() : Optional.of(cached);
        } catch (DataAccessException exception) {
            log.warn("读取 QQ 昵称缓存失败，将回源 PostgreSQL，qqNumber={}", qqNumber, exception);
            return null;
        }
    }

    private void cacheNickName(String cacheKey, Long qqNumber, Optional<String> nickName) {
        try {
            if (nickName.isPresent()) {
                redisTemplate.opsForValue().set(cacheKey, nickName.get(), CACHE_TTL);
            } else {
                redisTemplate.opsForValue().set(cacheKey, NOT_FOUND, NOT_FOUND_TTL);
            }
        } catch (DataAccessException exception) {
            log.warn("写入 QQ 昵称缓存失败，qqNumber={}", qqNumber, exception);
        }
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
}
