package com.ruoyi.lawyer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 发送消息请求
 *
 * @author ruoyi
 */
public class SendMessageRequest
{
    /** UUID 格式，作为一次发送的幂等标识 */
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    /** 问题正文 */
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 4000, message = "问题内容不能超过4000个字符")
    private String content;

    /** 请求ID，由前端为每次发送生成 */
    @NotBlank(message = "请求ID不能为空")
    @Pattern(regexp = UUID_PATTERN, message = "请求ID格式不正确")
    private String requestId;

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public void setRequestId(String requestId)
    {
        this.requestId = requestId;
    }
}
