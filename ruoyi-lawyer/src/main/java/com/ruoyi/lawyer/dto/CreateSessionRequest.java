package com.ruoyi.lawyer.dto;

import jakarta.validation.constraints.Size;

/**
 * 新建会话请求
 *
 * @author ruoyi
 */
public class CreateSessionRequest
{
    /** 会话标题，允许为空，为空时由后端使用默认标题 */
    @Size(max = 100, message = "会话标题不能超过100个字符")
    private String title;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }
}
