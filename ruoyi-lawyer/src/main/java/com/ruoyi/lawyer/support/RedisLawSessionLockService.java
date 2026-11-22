package com.ruoyi.lawyer.support;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import com.ruoyi.lawyer.config.LawyerChatProperties;

/**
 * 基于 Redis 的会话锁实现
 *
 * <p>Key：{@code lawyer:session:lock:{sessionId}}，Value：UUID，TTL 默认 120 秒。
 * 释放使用 Lua 脚本校验 token 归属，避免误删他人锁。</p>
 *
 * @author ruoyi
 */
@Component
public class RedisLawSessionLockService implements LawSessionLockService
{
    private static final Logger log = LoggerFactory.getLogger(RedisLawSessionLockService.class);

    /** 锁 key 前缀 */
    public static final String KEY_PREFIX = "lawyer:session:lock:";

    /** 释放锁的 Lua 脚本：仅当值等于 token 时删除 */
    public static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> UNLOCK_REDIS_SCRIPT =
            new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final LawyerChatProperties chatProperties;

    public RedisLawSessionLockService(StringRedisTemplate stringRedisTemplate, LawyerChatProperties chatProperties)
    {
        this.stringRedisTemplate = stringRedisTemplate;
        this.chatProperties = chatProperties;
    }

    /**
     * 构造会话锁 key
     *
     * @param sessionId 会话ID
     * @return Redis key
     */
    public static String lockKey(Long sessionId)
    {
        return KEY_PREFIX + sessionId;
    }

    @Override
    public String tryLock(Long sessionId)
    {
        String token = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey(sessionId), token,
                Duration.ofSeconds(chatProperties.getLockTtlSeconds()));
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    @Override
    public boolean unlock(Long sessionId, String token)
    {
        if (token == null)
        {
            return false;
        }
        try
        {
            Long result = stringRedisTemplate.execute(UNLOCK_REDIS_SCRIPT,
                    Collections.singletonList(lockKey(sessionId)), token);
            return result != null && result > 0;
        }
        catch (Exception e)
        {
            // 释放失败不影响业务结果，锁会随 TTL 自动过期
            log.warn("释放会话锁失败, sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isLocked(Long sessionId)
    {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(sessionId)));
    }
}
