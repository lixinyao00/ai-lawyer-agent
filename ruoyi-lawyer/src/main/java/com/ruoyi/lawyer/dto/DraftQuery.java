package com.ruoyi.lawyer.dto;

/**
 * 文书草稿列表查询条件
 *
 * @author ruoyi
 */
public class DraftQuery
{
    /** 模板编码 */
    private String templateCode;

    /** 标题模糊匹配 */
    private String title;

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
}
