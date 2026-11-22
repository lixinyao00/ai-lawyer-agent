package com.ruoyi.lawyer.vo;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 法律文书草稿详情视图对象
 *
 * @author ruoyi
 */
public class DocumentDraftVO
{
    /** 主键 */
    private Long id;

    /** 模板编码 */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 文书标题 */
    private String title;

    /** 动态表单要素，空值输出 {} */
    private Map<String, Object> formData = new LinkedHashMap<>();

    /** 文书正文纯文本 */
    private String content;

    /** 状态：success / failed */
    private String status;

    /** 实际使用的模型名 */
    private String modelName;

    /** 本次生成的失败原因（仅创建失败时下发，不落库） */
    private String errorMessage;

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

    public String getTemplateCode()
    {
        return templateCode;
    }

    public void setTemplateCode(String templateCode)
    {
        this.templateCode = templateCode;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Map<String, Object> getFormData()
    {
        return formData;
    }

    public void setFormData(Map<String, Object> formData)
    {
        this.formData = formData == null ? new LinkedHashMap<>() : formData;
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

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
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
