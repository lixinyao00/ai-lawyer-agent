package com.ruoyi.lawyer.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.config.LawyerAgentProperties;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.vo.CitationVO;
import tools.jackson.databind.ObjectMapper;

/**
 * LawAgentOrchestrator ReAct 编排、异常自愈与迭代熔断单元测试
 *
 * @author ruoyi
 */
class LawAgentOrchestratorTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();

    private LawyerAgentProperties properties;

    private LegalAmountCalculator calculator;

    private KnowledgeRetriever knowledgeRetriever;

    @BeforeEach
    void setUp()
    {
        properties = new LawyerAgentProperties();
        properties.setEnabled(true);
        properties.setMaxIterations(4);
        calculator = new LegalAmountCalculator();
        knowledgeRetriever = mock(KnowledgeRetriever.class);
    }

    @Test
    void runsMultiStepToolsAndAggregatesMetrics()
    {
        CitationVO citation = citation(5L, "中华人民共和国劳动合同法", "第四十七条");
        when(knowledgeRetriever.search(anyString(), anyInt(), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(citation));

        ScriptedClient client = new ScriptedClient(List.of(
                new AiChatClient.ChatResponse(null, null, List.of(
                        new AiChatClient.ToolCall("call_search", "search_law_articles",
                                "{\"query\":\"违法解除劳动合同\",\"topK\":4}"),
                        new AiChatClient.ToolCall("call_calc", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                                        + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}")),
                        "tool_calls", usage(100, 50), null),
                new AiChatClient.ChatResponse("公司违法解除应支付 2N 赔偿金 ¥80,000.00。", null, List.of(), "stop",
                        usage(200, 80), null)));
        RecordingSink sink = new RecordingSink();
        LawAgentOrchestrator orchestrator = orchestrator(client);

        AgentExecutionResult result = orchestrator.runStream(7L, 1L, "req-1", messages(), new ArrayList<>(), sink,
                () -> false);

        assertEquals("公司违法解除应支付 2N 赔偿金 ¥80,000.00。", result.getFinalContent());
        assertEquals(2, result.getToolCalls().size());
        assertEquals("success", result.getToolCalls().get(0).getStatus());
        assertEquals("success", result.getToolCalls().get(1).getStatus());
        assertTrue(result.getToolCalls().get(1).getResultSummary().contains("80,000.00"));
        assertEquals(1, result.getCitations().size());
        assertEquals(5L, result.getCitations().get(0).getChunkId());
        assertEquals(300, result.getPromptTokens());
        assertEquals(130, result.getCompletionTokens());
        assertEquals(430, result.getTotalTokens());
        assertNotNull(result.getTtftMs());
        assertNotNull(result.getTotalLatencyMs());
        assertEquals(2, sink.toolCalls.size());
        assertEquals(2, sink.toolResults.size());
        assertEquals(1, sink.citationEvents.size());
        assertTrue(sink.deltas.toString().contains("80,000.00"));
        assertEquals(2, client.requests.size());
        assertNotNull(client.requests.get(0).getTools());
        assertEquals(2, client.requests.get(0).getTools().size());
    }

    @Test
    void healsMalformedJsonAndMissingRequiredParamThenSucceeds()
    {
        CitationVO citation = citation(8L, "中华人民共和国劳动合同法", "第八十七条");
        when(knowledgeRetriever.search(anyString(), anyInt(), nullable(String.class), nullable(String.class)))
                .thenReturn(List.of(citation));

        AiChatClient.ToolCall malformed = new AiChatClient.ToolCall("call_bad_json", "calc_legal_amount",
                "{not-json");
        AiChatClient.ToolCall missing = new AiChatClient.ToolCall("call_missing", "calc_legal_amount",
                "{\"calcType\":\"labor_compensation\",\"params\":{\"terminationType\":\"2n\","
                        + "\"yearsOfService\":3.5}}");
        AiChatClient.ToolCall fixed = new AiChatClient.ToolCall("call_fixed", "calc_legal_amount",
                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                        + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}");

        ScriptedClient client = new ScriptedClient(List.of(
                new AiChatClient.ChatResponse(null, null, List.of(malformed), "tool_calls", usage(10, 5), null),
                new AiChatClient.ChatResponse(null, null, List.of(missing), "tool_calls", usage(20, 10), null),
                new AiChatClient.ChatResponse(null, null, List.of(fixed), "tool_calls", usage(30, 15), null),
                new AiChatClient.ChatResponse("经修正参数，2N 赔偿金为 ¥80,000.00。", null, List.of(), "stop",
                        usage(40, 20), null)));
        RecordingSink sink = new RecordingSink();
        LawAgentOrchestrator orchestrator = orchestrator(client);

        AgentExecutionResult result = orchestrator.runStream(7L, 1L, "req-2", messages(), new ArrayList<>(), sink,
                () -> false);

        assertEquals(3, result.getToolCalls().size());
        assertEquals("error", result.getToolCalls().get(0).getStatus());
        assertTrue(result.getToolCalls().get(0).getResultSummary().contains("JSON"));
        assertEquals("error", result.getToolCalls().get(1).getStatus());
        assertTrue(result.getToolCalls().get(1).getResultSummary().contains("校验失败"));
        assertEquals("success", result.getToolCalls().get(2).getStatus());
        assertTrue(result.getToolCalls().get(2).getResultSummary().contains("80,000.00"));
        assertEquals("经修正参数，2N 赔偿金为 ¥80,000.00。", result.getFinalContent());
        assertEquals(4, client.requests.size());
        assertEquals(3, sink.toolResults.size());

        // 每次工具错误都要以 role=tool 消息回传模型，且包含可修正的字段级提示
        List<AiChatClient.ChatMessage> thirdRound = client.requests.get(2).getMessages();
        boolean hasFieldHint = thirdRound.stream().anyMatch(message -> "tool".equals(message.getRole())
                && message.getContent() != null && message.getContent().contains("monthlySalary"));
        assertTrue(hasFieldHint);
    }

    @Test
    void breaksCircuitAtMaxIterationsAndRemovesToolsForFinalAnswer()
    {
        List<AiChatClient.ChatResponse> responses = new ArrayList<>();
        for (int i = 0; i < 4; i++)
        {
            responses.add(new AiChatClient.ChatResponse(null, null,
                    List.of(new AiChatClient.ToolCall("call_unknown_" + i, "unknown_tool", "{}")),
                    "tool_calls", usage(5, 2), null));
        }
        ScriptedClient client = new ScriptedClient(responses);
        RecordingSink sink = new RecordingSink();
        LawAgentOrchestrator orchestrator = orchestrator(client);

        AgentExecutionResult result = orchestrator.runStream(7L, 1L, "req-3", messages(), new ArrayList<>(), sink,
                () -> false);

        assertEquals(5, client.requests.size());
        assertNotNull(client.requests.get(3).getTools());
        assertNull(client.requests.get(4).getTools());
        List<AiChatClient.ChatMessage> finalMessages = client.requests.get(4).getMessages();
        boolean hasFinalizePrompt = finalMessages.stream().anyMatch(message -> "system".equals(message.getRole())
                && message.getContent() != null && message.getContent().contains("迭代上限"));
        assertTrue(hasFinalizePrompt);
        assertEquals("兜底回答", result.getFinalContent());
        assertEquals(4, result.getToolCalls().size());
        assertTrue(result.getToolCalls().stream().allMatch(trace -> "error".equals(trace.getStatus())));
        assertTrue(result.getToolCalls().stream()
                .allMatch(trace -> trace.getResultSummary().contains("工具不存在")));
        assertEquals(4, sink.toolResults.size());
        // 4 轮工具调用各 7 token，外加收尾兜底回答 2 token = 30
        assertEquals(30, result.getTotalTokens());
        assertFalse(sink.deltas.toString().contains("unknown_tool"));
    }

    private LawAgentOrchestrator orchestrator(AiChatClient client)
    {
        List<LawAgentTool> tools = List.of(
                new SearchLawArticlesTool(knowledgeRetriever, objectMapper),
                new CalcLegalAmountTool(calculator, objectMapper));
        return new LawAgentOrchestrator(client, properties, tools, objectMapper);
    }

    private List<AiChatClient.ChatMessage> messages()
    {
        return new ArrayList<>(List.of(new AiChatClient.ChatMessage("system", "你是法律助手"),
                new AiChatClient.ChatMessage("user", "工作 3.5 年月薪 1 万被违法辞退，2N 怎么算？")));
    }

    private AiChatClient.TokenUsage usage(int prompt, int completion)
    {
        return new AiChatClient.TokenUsage(prompt, completion, prompt + completion, false);
    }

    private CitationVO citation(Long chunkId, String title, String articleNo)
    {
        CitationVO citation = new CitationVO();
        citation.setChunkId(chunkId);
        citation.setDocId(1L);
        citation.setDocTitle(title);
        citation.setArticleNo(articleNo);
        citation.setSnippet("违法解除劳动合同的，应当依照经济补偿标准的二倍支付赔偿金。");
        citation.setScore(7.2);
        return citation;
    }

    /**
     * 按脚本返回响应的测试客户端
     */
    private static final class ScriptedClient implements AiChatClient
    {
        private final List<ChatResponse> responses;

        private final List<ChatRequest> requests = new ArrayList<>();

        private int index;

        private ScriptedClient(List<ChatResponse> responses)
        {
            this.responses = new ArrayList<>(responses);
        }

        @Override
        public String chat(List<ChatMessage> messages)
        {
            throw new UnsupportedOperationException("测试仅使用工具调用");
        }

        @Override
        public String chat(List<ChatMessage> messages, Duration timeout)
        {
            throw new UnsupportedOperationException("测试仅使用工具调用");
        }

        @Override
        public String streamChat(List<ChatMessage> messages, Consumer<String> onDelta, BooleanSupplier cancelled)
        {
            throw new UnsupportedOperationException("测试仅使用工具调用");
        }

        @Override
        public ChatResponse chatWithTools(ChatRequest request)
        {
            requests.add(request);
            return next();
        }

        @Override
        public ChatResponse streamChatWithTools(ChatRequest request, StreamListener listener,
                BooleanSupplier cancelled)
        {
            requests.add(request);
            ChatResponse response = next();
            if (listener != null && response.getContent() != null && !response.getContent().isEmpty())
            {
                listener.onContentDelta(response.getContent());
            }
            return response;
        }

        private ChatResponse next()
        {
            if (index < responses.size())
            {
                return responses.get(index++);
            }
            return new ChatResponse("兜底回答", null, List.of(), "stop",
                    new TokenUsage(1, 1, 2, false), null);
        }
    }

    /**
     * 记录编排事件
     */
    private static final class RecordingSink implements LawAgentOrchestrator.AgentEventSink
    {
        private final List<List<CitationVO>> citationEvents = new ArrayList<>();

        private final List<Map<String, Object>> toolCalls = new ArrayList<>();

        private final List<Map<String, Object>> toolResults = new ArrayList<>();

        private final StringBuilder deltas = new StringBuilder();

        @Override
        public void onCitation(List<CitationVO> citations)
        {
            citationEvents.add(new ArrayList<>(citations));
        }

        @Override
        public void onToolCall(Map<String, Object> event)
        {
            toolCalls.add(event);
        }

        @Override
        public void onToolResult(Map<String, Object> event)
        {
            toolResults.add(event);
        }

        @Override
        public void onDelta(String delta)
        {
            deltas.append(delta);
        }
    }
}
