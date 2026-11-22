package com.ruoyi.lawyer.dto;

/**
 * 更新文书草稿请求
 *
 * @author ruoyi
 */
public class UpdateDraftRequest
{
    /** 标题 */
    private String title;

    /** 文书正文纯文本 */
    private String content;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
