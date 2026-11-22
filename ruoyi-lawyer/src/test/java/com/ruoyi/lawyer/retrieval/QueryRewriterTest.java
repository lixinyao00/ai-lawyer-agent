package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiChatClient.ChatMessage;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;

/**
 * QueryRewriter 单元测试
 *
 * @author ruoyi
 */
class QueryRewriterTest
{
    private LawyerKnowledgeProperties properties;

    @BeforeEach
    void setUp()
    {
        properties = new LawyerKnowledgeProperties();
    }

    @Test
    void noHistoryPassesThrough()
    {
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        QueryRewriteResult result = rewriter.rewrite("那押金不退怎么办", List.of());

        assertFalse(result.isRewritten());
        assertEquals("passthrough", result.getStrategy());
        assertEquals("那押金不退怎么办", result.getRewrittenQuery());
    }

    @Test
    void rewriteDisabledPassesThrough()
    {
        properties.setRewriteEnabled(false);
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        QueryRewriteResult result = rewriter.rewrite("那押金不退怎么办", List.of(user("房东不退押金")));

        assertFalse(result.isRewritten());
        assertEquals("passthrough", result.getStrategy());
    }

    @Test
    void longIndependentQueryDoesNotNeedRewrite()
    {
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        boolean needed = rewriter.needsRewrite("中华人民共和国劳动合同法关于经济补偿金的计算标准是什么", true);

        assertFalse(needed);
    }

    @Test
    void shortFollowUpNeedsRewrite()
    {
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        assertTrue(rewriter.needsRewrite("那赔多少", true));
        assertTrue(rewriter.needsRewrite("如果房东扣着押金不退呢", true));
        assertFalse(rewriter.needsRewrite("那赔多少", false));
    }

    @Test
    void ruleFallbackPrependsLaborEntities()
    {
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        QueryRewriteResult result = rewriter.rewrite("那赔偿标准具体按几个月算？",
                List.of(user("公司违法辞退我，工作三年，月薪一万")));

        assertTrue(result.isRewritten());
        assertEquals("rule_fallback", result.getStrategy());
        assertTrue(result.getRewrittenQuery().contains("解除劳动合同"));
        assertTrue(result.getRewrittenQuery().contains("经济补偿"));
        assertTrue(result.getRewrittenQuery().endsWith("那赔偿标准具体按几个月算？"));
    }

    @Test
    void ruleFallbackWorksWithHistoryContext()
    {
        QueryRewriter rewriter = new QueryRewriter(null, properties);

        QueryRewriteResult result = rewriter.rewriteWithContext("那押金还能要回吗",
                "我租的房子到期搬走了，房东一直押着押金不退");

        assertTrue(result.isRewritten());
        assertEquals("rule_fallback", result.getStrategy());
        assertTrue(result.getRewrittenQuery().contains("押金"));
        assertTrue(result.getRewrittenQuery().contains("房屋租赁"));
    }

    @Test
    void llmResultIsUsedWhenValid()
    {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.chat(anyList(), any(Duration.class)))
                .thenReturn("「房屋租赁合同到期后房东拒不退还押金怎么办」");
        QueryRewriter rewriter = new QueryRewriter(aiChatClient, properties);

        QueryRewriteResult result = rewriter.rewrite("那他不退押金怎么办？",
                List.of(user("我租的房子到期搬走了")));

        assertTrue(result.isRewritten());
        assertEquals("llm", result.getStrategy());
        assertEquals("房屋租赁合同到期后房东拒不退还押金怎么办", result.getRewrittenQuery());
    }

    @Test
    void llmUnchangedAnswerIsNotMarkedRewritten()
    {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.chat(anyList(), any(Duration.class))).thenReturn("那他不退押金怎么办？");
        QueryRewriter rewriter = new QueryRewriter(aiChatClient, properties);

        QueryRewriteResult result = rewriter.rewrite("那他不退押金怎么办？",
                List.of(user("我租的房子到期搬走了")));

        assertFalse(result.isRewritten());
        assertEquals("llm", result.getStrategy());
    }

    @Test
    void illegalLlmOutputFallsBackToRule()
    {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.chat(anyList(), any(Duration.class)))
                .thenReturn("抱歉，我无法回答这个问题。\n请补充更多信息");
        QueryRewriter rewriter = new QueryRewriter(aiChatClient, properties);

        QueryRewriteResult result = rewriter.rewrite("那赔偿标准具体按几个月算？",
                List.of(user("公司违法辞退我，工作三年")));

        assertEquals("rule_fallback", result.getStrategy());
        assertTrue(result.isRewritten());
    }

    @Test
    void llmExceptionFallsBackToRule()
    {
        AiChatClient aiChatClient = mock(AiChatClient.class);
        when(aiChatClient.chat(anyList(), any(Duration.class))).thenThrow(new RuntimeException("timeout"));
        QueryRewriter rewriter = new QueryRewriter(aiChatClient, properties);

        QueryRewriteResult result = rewriter.rewrite("那押金不退怎么办",
                List.of(user("房东押着押金不退")));

        assertEquals("rule_fallback", result.getStrategy());
        assertTrue(result.isRewritten());
    }

    private ChatMessage user(String content)
    {
        return new ChatMessage("user", content);
    }
}
