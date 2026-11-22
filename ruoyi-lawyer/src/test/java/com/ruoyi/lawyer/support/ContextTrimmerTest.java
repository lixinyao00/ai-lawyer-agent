package com.ruoyi.lawyer.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.client.AiChatClient.ChatMessage;

/**
 * ContextTrimmer 单元测试
 *
 * @author ruoyi
 */
class ContextTrimmerTest
{
    @Test
    void keepsOnlyLatestTenRounds()
    {
        List<ChatMessage> input = new ArrayList<>();
        for (int i = 0; i < 12; i++)
        {
            input.add(new ChatMessage("user", "u" + i));
            input.add(new ChatMessage("assistant", "a" + i));
        }

        List<ChatMessage> result = ContextTrimmer.trim(input, 10, 12000);

        assertEquals(20, result.size());
        assertEquals("u2", result.get(0).getContent());
        assertEquals("a11", result.get(result.size() - 1).getContent());
    }

    @Test
    void trimsWholePairsToCharacterBudget()
    {
        List<ChatMessage> input = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            input.add(new ChatMessage("user", repeat('u', 100)));
            input.add(new ChatMessage("assistant", repeat('a', 100)));
        }

        // 每对 200 字，预算 450 只能保留最近 2 对（400 字）
        List<ChatMessage> result = ContextTrimmer.trim(input, 10, 450);

        assertEquals(4, result.size());
        assertEquals(repeat('u', 100), result.get(0).getContent());
        assertEquals("user", result.get(0).getRole());
        assertEquals("assistant", result.get(3).getRole());
    }

    @Test
    void dropsSingleOversizedPair()
    {
        List<ChatMessage> input = new ArrayList<>();
        input.add(new ChatMessage("user", repeat('u', 7000)));
        input.add(new ChatMessage("assistant", repeat('a', 7000)));

        assertTrue(ContextTrimmer.trim(input, 10, 12000).isEmpty());
    }

    @Test
    void keepsPairExactlyAtBudget()
    {
        List<ChatMessage> input = new ArrayList<>();
        input.add(new ChatMessage("user", repeat('u', 6000)));
        input.add(new ChatMessage("assistant", repeat('a', 6000)));

        assertEquals(2, ContextTrimmer.trim(input, 10, 12000).size());
    }

    @Test
    void emptyAndNullInputReturnEmpty()
    {
        assertTrue(ContextTrimmer.trim(null, 10, 12000).isEmpty());
        assertTrue(ContextTrimmer.trim(Collections.emptyList(), 10, 12000).isEmpty());
        assertTrue(ContextTrimmer.trim(List.of(new ChatMessage("user", "q")), 0, 12000).isEmpty());
        assertTrue(ContextTrimmer.trim(List.of(new ChatMessage("user", "q")), 10, 0).isEmpty());
    }

    @Test
    void dropsUnpairedFragments()
    {
        List<ChatMessage> input = new ArrayList<>();
        input.add(new ChatMessage("assistant", "orphan"));
        input.add(new ChatMessage("user", "u1"));
        input.add(new ChatMessage("assistant", "a1"));
        input.add(new ChatMessage("user", "u2"));

        List<ChatMessage> result = ContextTrimmer.trim(input, 10, 12000);

        assertEquals(2, result.size());
        assertEquals("u1", result.get(0).getContent());
        assertEquals("a1", result.get(1).getContent());
    }

    private String repeat(char c, int count)
    {
        return String.valueOf(c).repeat(count);
    }
}
