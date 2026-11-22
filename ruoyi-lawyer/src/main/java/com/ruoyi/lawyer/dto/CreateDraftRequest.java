package com.ruoyi.lawyer.dto;

import java.util.Map;

/**
 * 创建文书草稿请求
 *
 * @author ruoyi
 */
public class CreateDraftRequest
{
    /** 模板编码 */
    private String templateCode;

    /** 标题，不传时按模板与主体自动生成 */
    private String title;

    /** 动态表单要素 */
    private Map<String, Object> formData;

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

    public Map<String, Object> getFormData()
    {
        return formData;
    }

    public void setFormData(Map<String, Object> formData)
    {
        this.formData = formData;
    }
}
