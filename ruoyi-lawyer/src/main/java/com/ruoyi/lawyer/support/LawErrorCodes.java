package com.ruoyi.lawyer.support;

/**
 * 律师业务错误码常量
 *
 * <p>前半部分为落库的 error_code 列取值，后半部分为 SSE error 事件 code 取值。</p>
 *
 * @author ruoyi
 */
public final class LawErrorCodes
{
    /** 未分类内部错误 */
    public static final String INTERNAL_ERROR = "internal_error";

    /** 模型响应超时 */
    public static final String AI_TIMEOUT = "ai_timeout";

    /** 模型服务或响应解析错误 */
    public static final String AI_ERROR = "ai_error";

    /** 遗留 pending 自动收敛 */
    public static final String TIMEOUT_RECOVERED = "timeout_recovered";

    /** 客户端主动中断 */
    public static final String ABORTED = "aborted";

    /** SSE error 事件：模型超时 */
    public static final String SSE_AI_TIMEOUT = "AI_TIMEOUT";

    /** SSE error 事件：模型错误 */
    public static final String SSE_AI_ERROR = "AI_ERROR";

    /** SSE error 事件：内部错误 */
    public static final String SSE_INTERNAL = "INTERNAL";

    /** SSE error 事件：客户端中断（仅服务端日志使用） */
    public static final String SSE_ABORTED = "ABORTED";

    private LawErrorCodes()
    {
    }
}
