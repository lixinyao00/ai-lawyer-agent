package com.ruoyi.lawyer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.vo.AdminObservabilityVO;
import com.ruoyi.lawyer.vo.AdminObservabilityVO.ToolBreakdownVO;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台可观测性统计聚合、P95 计算、空数据集与 toolBreakdown 解析单元测试
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LawAdminSessionServiceImplTest
{
    @Mock
    private LawChatMessageMapper messageMapper;

    private LawAdminSessionServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new LawAdminSessionServiceImpl();
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    @Test
    void aggregatesTokenTtftRagRewriteAndToolMetrics()
    {
        List<LawChatMessage> messages = new ArrayList<>();
        messages.add(message("success", 1000, 400, 1400, 100L, 1000L,
                "[{\"chunkId\":1}]", "补偿标准", "[{\"toolName\":\"calc_legal_amount\",\"status\":\"success\",\"durationMs\":8}]"));
        messages.add(message("success", 1000, 400, 1400, 200L, 2000L,
                "[]", null, "[{\"toolName\":\"calc_legal_amount\",\"status\":\"success\",\"durationMs\":12},"
                        + "{\"toolName\":\"search_law_articles\",\"status\":\"success\",\"durationMs\":20}]"));
        messages.add(message("success", 1000, 400, 1400, 300L, 3000L,
                "[{\"chunkId\":2}]", "房屋租赁", "[{\"toolName\":\"create_document_draft\",\"status\":\"error\",\"durationMs\":100}]"));
        messages.add(message("success", 1000, 400, 1400, 400L, 4000L,
                null, null, null));
        messages.add(message("failed", 1000, 400, 1400, 500L, 5000L,
                "[{\"chunkId\":3}]", null, null));
        when(messageMapper.selectAssistantMessagesForObservability(isNull(), isNull())).thenReturn(messages);

        AdminObservabilityVO vo = service.getObservability(null, null);

        assertEquals(5L, vo.getTotalRequests());
        assertEquals(4L, vo.getSuccessCount());
        assertEquals(1L, vo.getFailedCount());
        assertEquals(80.0D, vo.getSuccessRate());
        assertEquals(5000L, vo.getTotalPromptTokens());
        assertEquals(2000L, vo.getTotalCompletionTokens());
        assertEquals(7000L, vo.getTotalTokens());
        assertEquals(300.0D, vo.getAvgTtftMs());
        // n = 5, index = ceil(0.95*5)-1 = 5-1 = 4，升序第 5 个 = 500
        assertEquals(500L, vo.getP95TtftMs());
        assertEquals(3000.0D, vo.getAvgLatencyMs());
        // citations_json 非空且不为 []：3/5 = 60%
        assertEquals(60.0D, vo.getRagHitRate());
        assertEquals(2L, vo.getQueryRewriteCount());
        assertEquals(4L, vo.getToolCallTotal());
        // 4 次工具调用中 3 次成功
        assertEquals(75.0D, vo.getToolCallSuccessRate());

        List<ToolBreakdownVO> breakdown = vo.getToolBreakdown();
        assertEquals(3, breakdown.size());
        assertEquals("calc_legal_amount", breakdown.get(0).getToolName());
        assertEquals(2L, breakdown.get(0).getCount());
        assertEquals(2L, breakdown.get(0).getSuccessCount());
        assertEquals(10.0D, breakdown.get(0).getAvgDurationMs());
        assertEquals("create_document_draft", breakdown.get(1).getToolName());
        assertEquals(1L, breakdown.get(1).getCount());
        assertEquals(0L, breakdown.get(1).getSuccessCount());
        assertEquals(100.0D, breakdown.get(1).getAvgDurationMs());
        assertEquals("search_law_articles", breakdown.get(2).getToolName());
    }

    @Test
    void emptyDatasetReturnsZeroSafely()
    {
        when(messageMapper.selectAssistantMessagesForObservability(isNull(), isNull()))
                .thenReturn(new ArrayList<>());

        AdminObservabilityVO vo = service.getObservability(null, null);

        assertNotNull(vo.getToolBreakdown());
        assertTrue(vo.getToolBreakdown().isEmpty());
        assertEquals(0L, vo.getTotalRequests());
        assertEquals(0L, vo.getSuccessCount());
        assertEquals(0L, vo.getFailedCount());
        assertEquals(0.0D, vo.getSuccessRate());
        assertEquals(0L, vo.getTotalTokens());
        assertEquals(0.0D, vo.getAvgTtftMs());
        assertEquals(0L, vo.getP95TtftMs());
        assertEquals(0.0D, vo.getAvgLatencyMs());
        assertEquals(0.0D, vo.getRagHitRate());
        assertEquals(0L, vo.getToolCallTotal());
        assertEquals(0.0D, vo.getToolCallSuccessRate());
    }

    @Test
    void p95UsesCeilIndexRule()
    {
        List<LawChatMessage> messages = new ArrayList<>();
        long[] ttfts = { 120L, 240L, 360L, 480L, 600L, 720L, 840L, 960L, 1080L, 1200L };
        for (long ttft : ttfts)
        {
            messages.add(message("success", 100, 50, 150, ttft, ttft * 2, null, null, null));
        }
        when(messageMapper.selectAssistantMessagesForObservability(isNull(), isNull())).thenReturn(messages);

        AdminObservabilityVO vo = service.getObservability(null, null);

        // n = 10, index = ceil(9.5)-1 = 10-1 = 9 -> 最大值 1200
        assertEquals(1200L, vo.getP95TtftMs());
        assertEquals(660.0D, vo.getAvgTtftMs());
    }

    @Test
    void toleratesMalformedToolCallsJson()
    {
        List<LawChatMessage> messages = new ArrayList<>();
        messages.add(message("success", 100, 50, 150, 100L, 200L, null, null, "{not-json"));
        messages.add(message("success", 100, 50, 150, 200L, 300L, null, null,
                "[{\"toolName\":\"calc_legal_amount\",\"status\":\"success\",\"durationMs\":6}]"));
        when(messageMapper.selectAssistantMessagesForObservability(isNull(), isNull())).thenReturn(messages);

        AdminObservabilityVO vo = service.getObservability(null, null);

        assertEquals(1L, vo.getToolCallTotal());
        assertEquals(1, vo.getToolBreakdown().size());
        assertEquals("calc_legal_amount", vo.getToolBreakdown().get(0).getToolName());
    }

    private LawChatMessage message(String status, Integer promptTokens, Integer completionTokens, Integer totalTokens,
            Long ttftMs, Long totalLatencyMs, String citationsJson, String rewrittenQuery, String toolCallsJson)
    {
        LawChatMessage message = new LawChatMessage();
        message.setRole("assistant");
        message.setStatus(status);
        message.setPromptTokens(promptTokens);
        message.setCompletionTokens(completionTokens);
        message.setTotalTokens(totalTokens);
        message.setTtftMs(ttftMs);
        message.setTotalLatencyMs(totalLatencyMs);
        message.setCitationsJson(citationsJson);
        message.setRewrittenQuery(rewrittenQuery);
        message.setToolCallsJson(toolCallsJson);
        return message;
    }
}
