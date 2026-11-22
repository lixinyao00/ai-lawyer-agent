package com.ruoyi.lawyer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库切片修改请求
 *
 * @author ruoyi
 */
public class KnowledgeChunkRequest
{
    /** 规范化中文条号，可为空 */
    @Size(max = 32, message = "条号不能超过32个字符")
    private String articleNo;

    /** 条文内容 */
    @NotBlank(message = "条文内容不能为空")
    @Size(max = 4000, message = "条文内容不能超过4000个字符")
    private String content;

    /** 章节标题 + 关键词 */
    @Size(max = 500, message = "关键词不能超过500个字符")
    private String keywords;

    public String getArticleNo()
    {
        return articleNo;
    }

    public void setArticleNo(String articleNo)
    {
        this.articleNo = articleNo;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }
}
