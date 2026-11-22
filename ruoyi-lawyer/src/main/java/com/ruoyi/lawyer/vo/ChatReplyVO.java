package com.ruoyi.lawyer.vo;

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
}
