package com.atriplus.chat.service.ai;

import com.atriplus.chat.config.MessageBufferProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** 使用 Lua 原子管理会话队列、处理资格与下一轮时间，支持多个网关实例。 */
@Component
public class RedisMessageBufferStore {
    private final StringRedisTemplate redis;
    private final MessageBufferProperties properties;
    private final String prefix;

    private static final DefaultRedisScript<Long> ADD = new DefaultRedisScript<>("""
            -- 忙碌时只追加；已有续批或冷却时间时不能被新的五秒防抖延长。
            local size = redis.call('RPUSH', KEYS[1], ARGV[2])
            local now = tonumber(ARGV[3])
            if redis.call('EXISTS', KEYS[3]) == 0 then
                local nextTime = redis.call('GET', KEYS[4])
                local due = now + tonumber(ARGV[4])
                if nextTime and (tonumber(nextTime) > now or size > 1) then
                    due = math.max(now, tonumber(nextTime))
                    -- 冷却期间有消息入队后转换为续批，标记不能在工作线程启动前过期。
                    redis.call('PERSIST', KEYS[4])
                elseif nextTime then
                    redis.call('DEL', KEYS[4])
                end
                redis.call('ZADD', KEYS[2], due, ARGV[1])
            end
            return size
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            -- 先在 Redis 抢占，后提交线程池；同一会话不能积累重复刷新任务。
            local now = tonumber(ARGV[3])
            local due = redis.call('ZSCORE', KEYS[2], ARGV[1])
            if not due or tonumber(due) > now then return 0 end
            if redis.call('LLEN', KEYS[1]) == 0 then
                redis.call('ZREM', KEYS[2], ARGV[1])
                return 0
            end
            local ttl = redis.call('PTTL', KEYS[3])
            if ttl > 0 then
                redis.call('ZADD', KEYS[2], now + ttl, ARGV[1])
                return 0
            end
            local nextTime = tonumber(redis.call('GET', KEYS[4]) or '0')
            if nextTime > now then
                redis.call('ZADD', KEYS[2], nextTime, ARGV[1])
                return 0
            end
            local lease = tonumber(ARGV[4])
            if not redis.call('SET', KEYS[3], ARGV[2], 'NX', 'PX', lease) then return 0 end
            -- 索引保留到租期结束，进程退出后未领取的队列仍能被重新扫描。
            redis.call('ZADD', KEYS[2], now + lease, ARGV[1])
            return 1
            """, Long.class);

    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> BEGIN = new DefaultRedisScript<>("""
            -- 八秒从工作线程实际开始时计时；读取快照暂不删除队列。
            if redis.call('GET', KEYS[3]) ~= ARGV[2] then return {} end
            local messages = redis.call('LRANGE', KEYS[1], 0, -1)
            if #messages > 0 then
                redis.call('SET', KEYS[4], tonumber(ARGV[3]) + tonumber(ARGV[4]))
            end
            return messages
            """, List.class);

    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            -- 只消费选中的连续前缀，预算外消息和快照之后的新消息都留在队列。
            if redis.call('GET', KEYS[3]) ~= ARGV[2] then return 0 end
            local count = tonumber(ARGV[3])
            if redis.call('LLEN', KEYS[1]) < count then return 0 end
            if count > 0 then redis.call('LTRIM', KEYS[1], count, -1) end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RENEW = new DefaultRedisScript<>("""
            -- 只有原持有者可以续租；排队和外部 HTTP 调用期间持续保留处理资格。
            if redis.call('GET', KEYS[3]) ~= ARGV[2] then return 0 end
            redis.call('PEXPIRE', KEYS[3], ARGV[4])
            redis.call('ZADD', KEYS[2], tonumber(ARGV[3]) + tonumber(ARGV[4]), ARGV[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> OWNS = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[3]) == ARGV[2] then return 1 end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
            -- 释放与安排下一轮必须原子衔接，旧任务不能删除新持有者的锁。
            if redis.call('GET', KEYS[3]) ~= ARGV[2] then return -1 end
            redis.call('DEL', KEYS[3])
            local now = tonumber(ARGV[3])
            local nextTime = tonumber(redis.call('GET', KEYS[4]) or '0')
            if redis.call('LLEN', KEYS[1]) > 0 then
                local due = math.max(now, nextTime)
                redis.call('ZADD', KEYS[2], due, ARGV[1])
                -- 有积压时保留续批标记，后来入队的消息不能重新触发五秒防抖。
                if nextTime == 0 then redis.call('SET', KEYS[4], due)
                else redis.call('PERSIST', KEYS[4]) end
                return due
            end
            redis.call('ZREM', KEYS[2], ARGV[1])
            if nextTime > now then
                -- 空队列仍保留剩余冷却；冷却过后自然恢复空闲防抖。
                redis.call('PEXPIRE', KEYS[4], nextTime - now)
            else
                redis.call('DEL', KEYS[4])
            end
            return 0
            """, Long.class);

    @Autowired
    public RedisMessageBufferStore(StringRedisTemplate redis, MessageBufferProperties properties) {
        this(redis, properties, "atriplus:chat:buffer:");
    }

    /** 真实 Redis 测试使用独立前缀，不读取或清理生产会话。 */
    RedisMessageBufferStore(StringRedisTemplate redis, MessageBufferProperties properties, String prefix) {
        this.redis = redis;
        this.properties = properties;
        this.prefix = prefix;
    }

    private List<String> keys(String conversationId) {
        return List.of(prefix + conversationId, prefix + "due",
                prefix + "lock:" + conversationId, prefix + "next:" + conversationId);
    }

    /** 新消息仅追加；刷新资格统一由调度器取得。 */
    public long add(String id, String json, long now) {
        Long result = redis.execute(ADD, keys(id), id, json, String.valueOf(now),
                String.valueOf(properties.debounceMillis()));
        if (result == null) throw new IllegalStateException("Redis 未返回消息入队结果");
        return result;
    }

    /** 沿用每秒最多扫描一百个到期会话。 */
    public Set<String> due(long now) {
        Set<String> result = redis.opsForZSet().rangeByScore(prefix + "due", 0, now, 0, 100);
        return result == null ? Set.of() : result;
    }

    /** 校验到期时间并取得唯一持有者资格。 */
    public boolean acquire(String id, String token, long now) {
        return success(redis.execute(ACQUIRE, keys(id), id, token, String.valueOf(now),
                String.valueOf(properties.lockLeaseMillis())));
    }

    /** 开始本轮并读取消息快照，预算选择后才消费选中的前缀。 */
    @SuppressWarnings("unchecked")
    public List<String> begin(String id, String token, long now) {
        List<String> result = redis.execute(BEGIN, keys(id), id, token, String.valueOf(now),
                String.valueOf(properties.minIntervalMillis()));
        return result == null ? List.of() : result;
    }

    /** 移除预算内前缀；描述失败时也使用此操作丢弃快照而非清空队列。 */
    public boolean consume(String id, String token, int count) {
        return success(redis.execute(CONSUME, keys(id), id, token, String.valueOf(count)));
    }

    /** 唯一持有者续租并推后到期扫描，防止忙碌会话占满扫描结果。 */
    public boolean renew(String id, String token, long now) {
        return success(redis.execute(RENEW, keys(id), id, token, String.valueOf(now),
                String.valueOf(properties.lockLeaseMillis())));
    }

    /** 外部调用前确认原任务仍有处理资格。 */
    public boolean owns(String id, String token) {
        return success(redis.execute(OWNS, keys(id), id, token));
    }

    /** 返回下一轮时间，零表示无积压，负数表示本轮已失去持有权。 */
    public long finish(String id, String token, long now) {
        Long result = redis.execute(FINISH, keys(id), id, token, String.valueOf(now));
        return result == null ? -1 : result;
    }

    private boolean success(Long result) {
        return Long.valueOf(1).equals(result);
    }
}
