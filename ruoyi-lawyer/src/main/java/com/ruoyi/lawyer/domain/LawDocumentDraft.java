package com.ruoyi.lawyer.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 法律文书草稿对象 law_document_draft
 *
 * @author ruoyi
 */
public class LawDocumentDraft implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所有者用户ID */
    private Long userId;

    /** 文书模板编码 */
    private String templateCode;

    /** 文书标题 */
    private String title;

    /** 要素对象 JSON：{fieldKey: value}，对外不直接暴露 */
    @JsonIgnore
    private String formDataJson;

    /** 文书正文纯文本；失败时为空串 */
    private String content;

    /** 状态：success / failed */
    private String status;

    /** 实际使用的模型名 */
    private String modelName;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getFormDataJson()
    {
        return formDataJson;
    }

    public void setFormDataJson(String formDataJson)
    {
        this.formDataJson = formDataJson;
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

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
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
