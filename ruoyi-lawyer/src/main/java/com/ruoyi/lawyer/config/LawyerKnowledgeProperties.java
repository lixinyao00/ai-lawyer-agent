package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 法律知识库配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.knowledge")
public class LawyerKnowledgeProperties
{
    /** 是否启用知识库检索 */
    private boolean enabled = true;

    /** 默认召回条数 */
    private int topK = 4;

    /** 最低相关度分数 */
    private double minScore = 1.0;

    /** 引用片段最大字符数 */
    private int snippetMaxChars = 200;

    /** 注入提示词的知识库片段最大字符数 */
    private int promptMaxChars = 4000;

    /** 内存索引刷新间隔（分钟） */
    private long refreshMinutes = 10L;

    /** 是否启用内置语料自动导入 */
    private boolean seedEnabled = true;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public int getTopK()
    {
        return topK;
    }

    public void setTopK(int topK)
    {
        this.topK = topK;
    }

    public double getMinScore()
    {
        return minScore;
    }

    public void setMinScore(double minScore)
    {
        this.minScore = minScore;
    }

    public int getSnippetMaxChars()
    {
        return snippetMaxChars;
    }

    public void setSnippetMaxChars(int snippetMaxChars)
    {
        this.snippetMaxChars = snippetMaxChars;
    }

    public int getPromptMaxChars()
    {
        return promptMaxChars;
    }

    public void setPromptMaxChars(int promptMaxChars)
    {
        this.promptMaxChars = promptMaxChars;
    }

    public long getRefreshMinutes()
    {
        return refreshMinutes;
    }

    public void setRefreshMinutes(long refreshMinutes)
    {
        this.refreshMinutes = refreshMinutes;
    }

    public boolean isSeedEnabled()
    {
        return seedEnabled;
    }

    public void setSeedEnabled(boolean seedEnabled)
    {
        this.seedEnabled = seedEnabled;
    }
}
