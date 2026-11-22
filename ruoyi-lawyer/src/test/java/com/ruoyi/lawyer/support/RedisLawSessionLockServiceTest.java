package com.ruoyi.lawyer.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import com.ruoyi.lawyer.config.LawyerChatProperties;

/**
 * RedisLawSessionLockService 单元测试
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class RedisLawSessionLockServiceTest
{
    private static final Long SESSION_ID = 42L;

    private static final String KEY = "lawyer:session:lock:42";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisLawSessionLockService service;

    @BeforeEach
    void setUp()
    {
        service = new RedisLawSessionLockService(redisTemplate, new LawyerChatProperties());
    }

    @Test
    void lockKeyFormatIsStable()
    {
        assertEquals(KEY, RedisLawSessionLockService.lockKey(SESSION_ID));
        assertEquals(RedisLawSessionLockService.KEY_PREFIX + "1", RedisLawSessionLockService.lockKey(1L));
    }

    @Test
    void tryLockReturnsTokenWhenAcquired()
    {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(Duration.ofSeconds(120)))).thenReturn(true);

        String token = service.tryLock(SESSION_ID);

        assertNotNull(token);
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), tokenCaptor.capture(), eq(Duration.ofSeconds(120)));
        assertEquals(token, tokenCaptor.getValue());
    }

    @Test
    void tryLockReturnsNullWhenBusy()
    {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(KEY), anyString(), any(Duration.class))).thenReturn(false);

        assertNull(service.tryLock(SESSION_ID));
    }

    @Test
    void unlockUsesLuaWithToken()
    {
        when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList(KEY)), eq("token-1")))
                .thenReturn(1L);

        assertTrue(service.unlock(SESSION_ID, "token-1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(Collections.singletonList(KEY)), eq("token-1"));
        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("redis.call('get', KEYS[1]) == ARGV[1]"));
        assertTrue(script.contains("redis.call('del', KEYS[1])"));
    }

    @Test
    void unlockReturnsFalseWhenTokenMismatch()
    {
        when(redisTemplate.execute(any(RedisScript.class), eq(Collections.singletonList(KEY)), eq("stale")))
                .thenReturn(0L);

        assertFalse(service.unlock(SESSION_ID, "stale"));
    }

    @Test
    void unlockReturnsFalseForNullToken()
    {
        assertFalse(service.unlock(SESSION_ID, null));
    }

    @Test
    void isLockedReadsRedisKey()
    {
        when(redisTemplate.hasKey(KEY)).thenReturn(true);
        assertTrue(service.isLocked(SESSION_ID));

        when(redisTemplate.hasKey(KEY)).thenReturn(false);
        assertFalse(service.isLocked(SESSION_ID));
    }
}
