package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 法律 Agent 编排配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.agent")
public class LawyerAgentProperties
{
    /** 是否启用 ReAct Tool Calling 法律 Agent */
    private boolean enabled = true;

    /** 最大 Tool Calling 迭代轮数（达到上限后移除 tools 做最终收尾回答） */
    private int maxIterations = 4;

    /** 单个工具执行超时上限（秒） */
    private int toolTimeoutSeconds = 30;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public int getMaxIterations()
    {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations)
    {
        this.maxIterations = maxIterations;
    }

    public int getToolTimeoutSeconds()
    {
        return toolTimeoutSeconds;
    }

    public void setToolTimeoutSeconds(int toolTimeoutSeconds)
    {
        this.toolTimeoutSeconds = toolTimeoutSeconds;
    }
}
