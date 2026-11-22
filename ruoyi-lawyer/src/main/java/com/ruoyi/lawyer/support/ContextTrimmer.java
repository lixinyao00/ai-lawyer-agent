package com.ruoyi.lawyer.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.ruoyi.lawyer.client.AiChatClient;

/**
 * 上下文预算裁剪（纯函数，无外部依赖）
 *
 * <p>输入为按时间正序、成对的成功历史（user, assistant）。规则：
 * 先保留最近 maxRounds 对；再按两条正文长度之和计算总字符，超过 maxChars 时从最早的完整问答对开始整对丢弃，
 * 直到总字符数不超过预算；单对本身超限时也丢弃。</p>
 *
 * @author ruoyi
 */
public final class ContextTrimmer
{
    private ContextTrimmer()
    {
    }

    /**
     * 裁剪历史上下文
     *
     * @param pairsAsc 按时间正序的历史消息（期望成对，残留的单个消息会被丢弃）
     * @param maxRounds 最大保留轮数
     * @param maxChars 最大字符预算（仅统计 user + assistant 正文长度）
     * @return 裁剪后的消息，保持时间正序
     */
    public static List<AiChatClient.ChatMessage> trim(List<AiChatClient.ChatMessage> pairsAsc, int maxRounds,
            int maxChars)
    {
        if (pairsAsc == null || pairsAsc.isEmpty() || maxRounds <= 0 || maxChars <= 0)
        {
            return Collections.emptyList();
        }

        // 1. 组对，丢弃不成对的残片
        List<List<AiChatClient.ChatMessage>> pairs = new ArrayList<>();
        AiChatClient.ChatMessage pendingUser = null;
        for (AiChatClient.ChatMessage message : pairsAsc)
        {
            if (message == null || message.getRole() == null)
            {
                continue;
            }
            if ("user".equals(message.getRole()))
            {
                // 连续两条 user：前一条视为残片丢弃
                pendingUser = message;
            }
            else if ("assistant".equals(message.getRole()) && pendingUser != null)
            {
                List<AiChatClient.ChatMessage> pair = new ArrayList<>(2);
                pair.add(pendingUser);
                pair.add(message);
                pairs.add(pair);
                pendingUser = null;
            }
        }

        if (pairs.isEmpty())
        {
            return Collections.emptyList();
        }

        // 2. 只保留最近 maxRounds 对
        int from = Math.max(0, pairs.size() - maxRounds);
        List<List<AiChatClient.ChatMessage>> kept = new ArrayList<>(pairs.subList(from, pairs.size()));

        // 3. 超过字符预算时从最早的一对开始整对丢弃
        long total = 0L;
        for (List<AiChatClient.ChatMessage> pair : kept)
        {
            total += length(pair.get(0)) + length(pair.get(1));
        }
        while (!kept.isEmpty() && total > maxChars)
        {
            List<AiChatClient.ChatMessage> removed = kept.remove(0);
            total -= length(removed.get(0)) + length(removed.get(1));
        }

        // 4. 展开为时间正序列表
        List<AiChatClient.ChatMessage> result = new ArrayList<>(kept.size() * 2);
        for (List<AiChatClient.ChatMessage> pair : kept)
        {
            result.add(pair.get(0));
            result.add(pair.get(1));
        }
        return result;
    }

    private static int length(AiChatClient.ChatMessage message)
    {
        return message.getContent() == null ? 0 : message.getContent().length();
    }
}
