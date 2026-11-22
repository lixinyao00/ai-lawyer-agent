package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 咨询聊天配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.chat")
public class LawyerChatProperties
{
    /** Redis 会话锁 TTL（秒） */
    private long lockTtlSeconds = 120L;

    /** 遗留 pending 消息收敛阈值（秒） */
    private long pendingTimeoutSeconds = 120L;

    /** 带入模型的历史轮数 */
    private int historyRounds = 10;

    /** 历史上下文最大字符预算 */
    private int historyMaxChars = 12000;

    /** SseEmitter 超时（毫秒） */
    private long sseTimeout = 110000L;

    /** SSE 心跳间隔（秒） */
    private long heartbeatSeconds = 15L;

    public long getLockTtlSeconds()
    {
        return lockTtlSeconds;
    }

    public void setLockTtlSeconds(long lockTtlSeconds)
    {
        this.lockTtlSeconds = lockTtlSeconds;
    }

    public long getPendingTimeoutSeconds()
    {
        return pendingTimeoutSeconds;
    }

    public void setPendingTimeoutSeconds(long pendingTimeoutSeconds)
    {
        this.pendingTimeoutSeconds = pendingTimeoutSeconds;
    }

    public int getHistoryRounds()
    {
        return historyRounds;
    }

    public void setHistoryRounds(int historyRounds)
    {
        this.historyRounds = historyRounds;
    }

    public int getHistoryMaxChars()
    {
        return historyMaxChars;
    }

    public void setHistoryMaxChars(int historyMaxChars)
    {
        this.historyMaxChars = historyMaxChars;
    }

    public long getSseTimeout()
    {
        return sseTimeout;
    }

    public void setSseTimeout(long sseTimeout)
    {
        this.sseTimeout = sseTimeout;
    }

    public long getHeartbeatSeconds()
    {
        return heartbeatSeconds;
    }

    public void setHeartbeatSeconds(long heartbeatSeconds)
    {
        this.heartbeatSeconds = heartbeatSeconds;
    }
}
