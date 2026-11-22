package com.ruoyi.lawyer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库法规重新切分请求
 *
 * @author ruoyi
 */
public class KnowledgeRechunkRequest
{
    /** 法规全文 */
    @NotBlank(message = "法规内容不能为空")
    @Size(max = 500000, message = "法规内容不能超过500000个字符")
    private String content;

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
