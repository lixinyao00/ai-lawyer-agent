package com.ruoyi.lawyer.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 发送消息返回结果
 *
 * @author ruoyi
 */
public class ChatReplyVO
{
    /** 本次请求ID */
    private String requestId;

    /** 用户消息ID */
    private Long userMessageId;

    /** 助手消息ID */
    private Long assistantMessageId;

    /** 回答状态：pending / success / failed */
    private String status;

    /** 回答正文 */
    private String content;

    /** 面向用户的错误提示，成功时为空 */
    private String errorMessage;

    /** 应用定义的错误代码 */
    private String errorCode;

    /** 法条引用，空为 []，不输出 null */
    private List<CitationVO> citations = new ArrayList<>();

    /** 多轮指代改写后的实际检索词 */
    private String rewrittenQuery;

    /** 实际生效检索模式：bm25 / vector / hybrid_rrf */
    private String retrievalMode;

    /** Agent 工具调用轨迹，空为 []，不输出 null */
    private List<ToolCallTraceVO> toolCalls = new ArrayList<>();

    /** 累计输入 Token */
    private Integer promptTokens;

    /** 累计输出 Token */
    private Integer completionTokens;

    /** 累计总 Token */
    private Integer totalTokens;

    /** 首字延迟（毫秒） */
    private Long ttftMs;

    /** 端到端总耗时（毫秒） */
    private Long totalLatencyMs;

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public Long getUserMessageId()
    {
        return userMessageId;
    }

    public void setUserMessageId(Long userMessageId)
    {
        this.userMessageId = userMessageId;
    }

    public Long getAssistantMessageId()
    {
        return assistantMessageId;
    }

    public void setAssistantMessageId(Long assistantMessageId)
    {
        this.assistantMessageId = assistantMessageId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public List<CitationVO> getCitations()
    {
        return citations;
    }

    public void setCitations(List<CitationVO> citations)
    {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public String getRewrittenQuery()
    {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery)
    {
        this.rewrittenQuery = rewrittenQuery;
    }

    public String getRetrievalMode()
    {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode)
    {
        this.retrievalMode = retrievalMode;
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
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens()
    {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens)
    {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens()
    {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens)
    {
        this.totalTokens = totalTokens;
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
