package com.ruoyi.lawyer.agent;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.ToolCallTraceVO;

/**
 * Agent 一次完整执行的结果
 *
 * @author ruoyi
 */
public class AgentExecutionResult
{
    /** 最终自然语言回答 */
    private String finalContent = "";

    /** 全量去重后的法条引用 */
    private List<CitationVO> citations = new ArrayList<>();

    /** 工具调用轨迹 */
    private List<ToolCallTraceVO> toolCalls = new ArrayList<>();

    /** 累计输入 Token */
    private Integer promptTokens = 0;

    /** 累计输出 Token */
    private Integer completionTokens = 0;

    /** 累计总 Token */
    private Integer totalTokens = 0;

    /** 首个可见正文 token 延迟（毫秒），无正文时为 null */
    private Long ttftMs;

    /** 端到端总耗时（毫秒） */
    private Long totalLatencyMs;

    public String getFinalContent()
    {
        return finalContent;
    }

    public void setFinalContent(String finalContent)
    {
        this.finalContent = finalContent == null ? "" : finalContent;
    }

    public List<CitationVO> getCitations()
    {
        return citations;
    }

    public void setCitations(List<CitationVO> citations)
    {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public List<ToolCallTraceVO> getToolCalls()
    {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCallTraceVO> toolCalls)
    {
        this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
    }

    public Integer getPromptTokens()
    {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens)
    {
        this.promptTokens = promptTokens == null ? 0 : promptTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens)
    {
        this.completionTokens = completionTokens == null ? 0 : completionTokens;
    }

    public Integer getTotalTokens()
    {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens)
    {
        this.totalTokens = totalTokens == null ? 0 : totalTokens;
    }

    public Long getTtftMs()
    {
        return ttftMs;
    }

    public void setTtftMs(Long ttftMs)
    {
        this.ttftMs = ttftMs;
    }

    public Long getTotalLatencyMs()
    {
        return totalLatencyMs;
    }

    public void setTotalLatencyMs(Long totalLatencyMs)
    {
        this.totalLatencyMs = totalLatencyMs;
    }
}
