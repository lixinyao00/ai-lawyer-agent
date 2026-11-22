package com.ruoyi.lawyer.support;

/**
 * 会话分布式锁
 *
 * @author ruoyi
 */
public interface LawSessionLockService
{
    /**
     * 尝试加锁，不等待、不重试
     *
     * @param sessionId 会话ID
     * @return 成功返回锁 token，失败返回 null
     */
    String tryLock(Long sessionId);

    /**
     * 释放锁，仅当 token 匹配当前持有者时才删除（Lua 原子校验）
     *
     * @param sessionId 会话ID
     * @param token 加锁时返回的 token
     * @return 是否成功释放
     */
    boolean unlock(Long sessionId, String token);

    /**
     * 会话是否正在被锁定
     *
     * @param sessionId 会话ID
     * @return 是否锁定
     */
    boolean isLocked(Long sessionId);
}
