package com.ruoyi.lawyer.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 消息视图对象（前台消息列表 / 后台消息审计共用）
 *
 * @author ruoyi
 */
public class ChatMessageVO
{
    /** 主键 */
    private Long id;

    /** 所属会话ID */
    private Long sessionId;

    /** 本次发送的请求ID */
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

    /** 法条引用，空为 []，不输出 null */
    private List<CitationVO> citations = new ArrayList<>();

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    private Date updateTime;

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

    public List<CitationVO> getCitations()
    {
        return citations;
    }

    public void setCitations(List<CitationVO> citations)
    {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
