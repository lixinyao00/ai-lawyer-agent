package com.ruoyi.lawyer.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 咨询消息对象 law_chat_message
 *
 * @author ruoyi
 */
public class LawChatMessage extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属会话ID */
    private Long sessionId;

    /** 本次发送的请求ID，同一轮问答的用户消息和助手消息共用 */
    private String requestId;

    /** 角色：user / assistant */
    private String role;

    /** 消息正文 */
    private String content;

    /** 状态：pending / success / failed */
    private String status;

    /** 应用定义的错误代码 */
    private String errorCode;

    /** 实际使用的模型名 */
    private String modelName;

    /** 法条引用 JSON 数组（仅 assistant 消息写入），对外只暴露解析后的 citations */
    @JsonIgnore
    private String citationsJson;

    /** 多轮指代改写后的实际检索词（仅 assistant 消息写入） */
    private String rewrittenQuery;

    /** 实际生效检索模式：bm25 / vector / hybrid_rrf */
    private String retrievalMode;

    /** Agent 工具调用轨迹 JSON 数组（仅 assistant 写入），对外只暴露解析后的 toolCalls */
    @JsonIgnore
    private String toolCallsJson;

    /** 本轮累计输入 Token 数（多轮 Agent 迭代求和） */
    private Integer promptTokens;

    /** 本轮累计输出 Token 数（多轮 Agent 迭代求和） */
    private Integer completionTokens;

    /** 本轮总 Token 数 */
    private Integer totalTokens;

    /** 首字延迟（毫秒） */
    private Long ttftMs;

    /** 端到端总耗时（毫秒） */
    private Long totalLatencyMs;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getCitationsJson()
    {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson)
    {
        this.citationsJson = citationsJson;
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

    public String getToolCallsJson()
    {
        return toolCallsJson;
    }

    public void setToolCallsJson(String toolCallsJson)
    {
        this.toolCallsJson = toolCallsJson;
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
