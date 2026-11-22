package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 合同风险审查配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.contract")
public class LawyerContractProperties
{
    /** 送模型的合同最大字数 */
    private int maxChars = 20000;

    /** 审查中状态的收敛阈值（秒） */
    private long pendingTimeoutSeconds = 300L;

    public int getMaxChars()
    {
        return maxChars;
    }

    public void setMaxChars(int maxChars)
    {
        this.maxChars = maxChars;
    }

    public long getPendingTimeoutSeconds()
    {
        return pendingTimeoutSeconds;
    }

    public void setPendingTimeoutSeconds(long pendingTimeoutSeconds)
    {
        this.pendingTimeoutSeconds = pendingTimeoutSeconds;
    }
}
