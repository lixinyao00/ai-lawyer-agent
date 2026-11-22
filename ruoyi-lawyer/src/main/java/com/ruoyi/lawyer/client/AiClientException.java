package com.ruoyi.lawyer.client;

/**
 * 模型调用异常，区分超时、HTTP 错误与响应解析错误
 *
 * @author ruoyi
 */
public class AiClientException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * 异常类型
     */
    public enum Type
    {
        /** 请求超时 */
        TIMEOUT,
        /** HTTP 状态码异常 */
        HTTP,
        /** 响应结构解析失败 */
        PARSE
    }

    private final Type type;

    private final Integer statusCode;

    public AiClientException(Type type, String message)
    {
        this(type, null, message, null);
    }

    public AiClientException(Type type, String message, Throwable cause)
    {
        this(type, null, message, cause);
    }

    public AiClientException(Type type, Integer statusCode, String message)
    {
        this(type, statusCode, message, null);
    }

    public AiClientException(Type type, Integer statusCode, String message, Throwable cause)
    {
        super(message, cause);
        this.type = type;
        this.statusCode = statusCode;
    }

    public Type getType()
    {
        return type;
    }

    public Integer getStatusCode()
    {
        return statusCode;
    }
}
