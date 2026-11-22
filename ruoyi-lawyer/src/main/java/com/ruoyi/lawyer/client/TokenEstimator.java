package com.ruoyi.lawyer.client;

import java.util.List;

/**
 * Token 用量估算器
 *
 * <p>当上游网关未返回 {@code usage}（例如代理不支持 {@code stream_options.include_usage}）时，
 * 按 CJK 字符与非 CJK 字符分别估算输入/输出 Token，保证可观测指标永不为 {@code null} 或 {@code 0}。
 * 估算规则：{@code ceil(cjkChars * 1.5 + otherChars / 4.0)}。</p>
 *
 * @author ruoyi
 */
public final class TokenEstimator
{
    /** CJK 单字权重 */
    private static final double CJK_WEIGHT = 1.5;

    /** 非 CJK 字符平均每 Token 字符数 */
    private static final double OTHER_CHARS_PER_TOKEN = 4.0;

    private TokenEstimator()
    {
    }

    /**
     * 估算整轮对话的 Token 用量
     *
     * @param messages 输入消息
     * @param outputText 模型输出正文
     * @return 估算用量，estimated=true，且各值最小为 1
     */
    public static AiChatClient.TokenUsage estimate(List<AiChatClient.ChatMessage> messages, String outputText)
    {
        int prompt = estimatePrompt(messages);
        int completion = Math.max(1, estimateText(outputText));
        return new AiChatClient.TokenUsage(prompt, completion, prompt + completion, true);
    }

    /**
     * 估算输入消息 Token 数
     *
     * @param messages 输入消息
     * @return Token 数，最小为 1
     */
    public static int estimatePrompt(List<AiChatClient.ChatMessage> messages)
    {
        if (messages == null || messages.isEmpty())
        {
            return 1;
        }
        StringBuilder builder = new StringBuilder();
        for (AiChatClient.ChatMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }
            builder.append(message.getRole()).append('\n');
            if (message.getContent() != null)
            {
                builder.append(message.getContent()).append('\n');
            }
            if (message.getToolCalls() != null)
            {
                for (AiChatClient.ToolCall call : message.getToolCalls())
                {
                    if (call != null)
                    {
                        builder.append(call.getName()).append(' ').append(call.getArguments()).append('\n');
                    }
                }
            }
        }
        return Math.max(1, estimateText(builder.toString()));
    }

    /**
     * 估算文本 Token 数
     *
     * @param text 文本
     * @return Token 数，空文本返回 0
     */
    public static int estimateText(String text)
    {
        if (text == null || text.isEmpty())
        {
            return 0;
        }
        long cjk = 0;
        long other = 0;
        for (int i = 0; i < text.length(); i++)
        {
            if (isCjk(text.charAt(i)))
            {
                cjk++;
            }
            else
            {
                other++;
            }
        }
        return (int) Math.ceil(cjk * CJK_WEIGHT + other / OTHER_CHARS_PER_TOKEN);
    }

    private static boolean isCjk(char value)
    {
        Character.UnicodeScript script = Character.UnicodeScript.of(value);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}
