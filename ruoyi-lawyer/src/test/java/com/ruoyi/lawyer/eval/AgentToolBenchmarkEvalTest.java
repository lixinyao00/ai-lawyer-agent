package com.ruoyi.lawyer.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.agent.AgentExecutionResult;
import com.ruoyi.lawyer.agent.CalcLegalAmountTool;
import com.ruoyi.lawyer.agent.CreateDocumentDraftTool;
import com.ruoyi.lawyer.agent.LawAgentOrchestrator;
import com.ruoyi.lawyer.agent.LawAgentTool;
import com.ruoyi.lawyer.agent.LegalAmountCalculator;
import com.ruoyi.lawyer.agent.SearchLawArticlesTool;
import com.ruoyi.lawyer.agent.ToolExecutionContext;
import com.ruoyi.lawyer.agent.ToolExecutionResult;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.config.LawyerAgentProperties;
import com.ruoyi.lawyer.dto.CreateDraftRequest;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.service.ILawDocumentService;
import com.ruoyi.lawyer.service.impl.DocumentTemplateRegistry;
import com.ruoyi.lawyer.support.FormDataValidator;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.DocumentDraftVO;
import com.ruoyi.lawyer.vo.ToolCallTraceVO;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Agent 工具调用与确定性计算离线基准评测（15 题）
 *
 * <p>包含 9 道精确数值计算题（12 项金额/天数/日期断言）、3 道多步工具编排题、
 * 3 道异常自愈与 max-iterations 熔断题，要求全部 100% 通过，并自动生成
 * {@code doc/V2.0_BENCHMARK_REPORT.md} 的 Agent 区块。</p>
 *
 * @author ruoyi
 */
class AgentToolBenchmarkEvalTest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String TYPE_CALC = "calc";

    private static final String TYPE_ORCHESTRATION = "orchestration";

    private static final String TYPE_SELF_HEALING = "self_healing";

    private static final Long USER_ID = 1L;

    private static final Long SESSION_ID = 1L;

    private static List<AgentEvalCase> cases;

    private static LegalAmountCalculator calculator;

    private static CalcLegalAmountTool calcTool;

    @BeforeAll
    static void setUp() throws Exception
    {
        cases = loadDataset();
        calculator = new LegalAmountCalculator();
        calcTool = new CalcLegalAmountTool(calculator, OBJECT_MAPPER);
    }

    @Test
    void agentBenchmarkPassesAllQuestions()
    {
        assertEquals(15, cases.size(), "Agent 评测集必须包含 15 道题");
        assertEquals(9, countType(TYPE_CALC));
        assertEquals(3, countType(TYPE_ORCHESTRATION));
        assertEquals(3, countType(TYPE_SELF_HEALING));

        List<BenchmarkReportWriter.AgentCase> reportCases = new ArrayList<>();
        int passedQuestions = 0;
        int calcAssertions = 0;
        int calcPassedAssertions = 0;

        for (AgentEvalCase evalCase : cases)
        {
            long startNanos = System.nanoTime();
            CaseOutcome outcome;
            if (TYPE_CALC.equals(evalCase.type))
            {
                outcome = runCalcCase(evalCase);
                calcAssertions += outcome.assertionCount;
                calcPassedAssertions += outcome.passedAssertions;
            }
            else if (TYPE_ORCHESTRATION.equals(evalCase.type))
            {
                outcome = runOrchestrationCase(evalCase);
            }
            else
            {
                outcome = runSelfHealingCase(evalCase);
            }
            long durationMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
            if (outcome.passed)
            {
                passedQuestions++;
            }
            reportCases.add(new BenchmarkReportWriter.AgentCase(evalCase.id, evalCase.type, evalCase.title,
                    outcome.passed, outcome.expected, outcome.actual, durationMs));
        }

        assertEquals(12, calcAssertions, "9 道计算题必须覆盖 12 项确定性数值断言");
        assertEquals(calcAssertions, calcPassedAssertions, "全部金额/天数/日期断言必须零误差通过");
        assertEquals(cases.size(), passedQuestions, "15 道 Agent 基准题必须 100% 通过");

        BenchmarkReportWriter.AgentReport report = new BenchmarkReportWriter.AgentReport();
        report.generatedAt = java.time.LocalDateTime.now().toString();
        report.cases = reportCases;
        report.totalQuestions = cases.size();
        report.passedQuestions = passedQuestions;
        report.calcAssertions = calcAssertions;
        report.calcPassedAssertions = calcPassedAssertions;
        report.maxIterationsNote = "max-iterations=4（连续 4 轮无效工具调用后移除 tools 完成兜底回答）";
        BenchmarkReportWriter.writeAgent(report);
    }

    // ------------------------------------------------------------------
    // 9 道精确数值计算题
    // ------------------------------------------------------------------

    private CaseOutcome runCalcCase(AgentEvalCase evalCase)
    {
        CaseOutcome outcome = new CaseOutcome();
        outcome.assertionCount = evalCase.assertions.size();
        StringBuilder expectedText = new StringBuilder();
        StringBuilder actualText = new StringBuilder();
        ToolExecutionContext context = new ToolExecutionContext(USER_ID, SESSION_ID, evalCase.id,
                new ArrayList<>());

        for (Assertion assertion : evalCase.assertions)
        {
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("calcType", assertion.calcType);
            arguments.put("params", assertion.params == null ? Map.of() : assertion.params);
            ToolExecutionResult result = calcTool.execute(context, arguments);
            if (!expectedText.isEmpty())
            {
                expectedText.append("；");
                actualText.append("；");
            }
            expectedText.append(assertion.expected);
            if (!result.isSuccess())
            {
                actualText.append("error=").append(result.getSummary());
                outcome.actual = actualText.toString();
                outcome.expected = expectedText.toString();
                return outcome;
            }
            actualText.append(assertion.calcType).append("=").append(result.getDetail());
            boolean assertionPassed = true;
            for (Map.Entry<String, Object> entry : assertion.expected.entrySet())
            {
                Object actualValue = result.getDetail().get(entry.getKey());
                if (!valueMatches(entry.getValue(), actualValue))
                {
                    assertionPassed = false;
                    actualText.append(" [").append(entry.getKey()).append(" 期望 ")
                            .append(entry.getValue()).append(" 实际 ").append(actualValue).append("]");
                }
            }
            if (assertionPassed)
            {
                outcome.passedAssertions++;
            }
        }
        outcome.passed = outcome.passedAssertions == outcome.assertionCount;
        outcome.expected = expectedText.toString();
        outcome.actual = outcome.assertionCount == 1
                ? "金额/天数/日期与期望值逐字段一致"
                : outcome.assertionCount + " 项断言全部一致";
        if (!outcome.passed)
        {
            outcome.actual = actualText.toString();
        }
        return outcome;
    }

    private static boolean valueMatches(Object expected, Object actual)
    {
        if (expected == null)
        {
            return actual == null;
        }
        if (expected instanceof Boolean expectedBoolean)
        {
            return expectedBoolean.equals(actual);
        }
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber)
        {
            return new BigDecimal(expectedNumber.toString())
                    .compareTo(new BigDecimal(actualNumber.toString())) == 0;
        }
        if (expected instanceof String expectedText && actual instanceof Number actualNumber)
        {
            try
            {
                return new BigDecimal(expectedText).compareTo(new BigDecimal(actualNumber.toString())) == 0;
            }
            catch (NumberFormatException e)
            {
                return expectedText.equals(actualNumber.toString());
            }
        }
        return expected.toString().equals(actual == null ? null : actual.toString());
    }

    // ------------------------------------------------------------------
    // 3 道多步编排题
    // ------------------------------------------------------------------

    private CaseOutcome runOrchestrationCase(AgentEvalCase evalCase)
    {
        CaseOutcome outcome = new CaseOutcome();
        Scenario scenario = new Scenario(evalCase.scenario);
        AgentExecutionResult result = scenario.run();
        List<String> actualTools = new ArrayList<>();
        for (ToolCallTraceVO trace : result.getToolCalls())
        {
            actualTools.add(trace.getToolName());
        }
        expectedExpected(outcome, evalCase);

        boolean passed = true;
        List<String> expectedTools = stringList(evalCase.expected.get("tools"));
        if (!expectedTools.equals(actualTools))
        {
            passed = false;
        }
        for (ToolCallTraceVO trace : result.getToolCalls())
        {
            if (!"success".equals(trace.getStatus()))
            {
                passed = false;
            }
        }
        Object finalContains = evalCase.expected.get("finalContains");
        if (finalContains != null && (result.getFinalContent() == null
                || !result.getFinalContent().contains(finalContains.toString())))
        {
            passed = false;
        }
        Object minCitations = evalCase.expected.get("minCitations");
        if (minCitations instanceof Number number && result.getCitations().size() < number.intValue())
        {
            passed = false;
        }
        Object draftId = evalCase.expected.get("draftId");
        if (draftId != null)
        {
            boolean draftMatched = false;
            for (ToolCallTraceVO trace : result.getToolCalls())
            {
                if (trace.getDraftId() != null && trace.getDraftId().toString().equals(draftId.toString()))
                {
                    draftMatched = true;
                }
            }
            if (!draftMatched)
            {
                passed = false;
            }
        }
        outcome.passed = passed;
        outcome.actual = "tools=" + actualTools + "，draftId=" + firstDraftId(result)
                + "，citations=" + result.getCitations().size();
        return outcome;
    }

    private static Long firstDraftId(AgentExecutionResult result)
    {
        for (ToolCallTraceVO trace : result.getToolCalls())
        {
            if (trace.getDraftId() != null)
            {
                return trace.getDraftId();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 3 道异常自愈 / 熔断题
    // ------------------------------------------------------------------

    private CaseOutcome runSelfHealingCase(AgentEvalCase evalCase)
    {
        CaseOutcome outcome = new CaseOutcome();
        Scenario scenario = new Scenario(evalCase.scenario);
        AgentExecutionResult result = scenario.run();
        expectedExpected(outcome, evalCase);
        List<ToolCallTraceVO> traces = result.getToolCalls();
        boolean passed = true;

        if ("circuit_breaker".equals(evalCase.scenario))
        {
            int expectedErrors = number(evalCase.expected.get("errorCount"), 4);
            if (traces.size() != expectedErrors)
            {
                passed = false;
            }
            if (traces.stream().anyMatch(trace -> !"error".equals(trace.getStatus())))
            {
                passed = false;
            }
            if (result.getFinalContent() == null
                    || !result.getFinalContent().contains(String.valueOf(evalCase.expected.get("finalContains"))))
            {
                passed = false;
            }
            int maxIterations = number(evalCase.expected.get("maxIterations"), 4);
            if (scenario.client.requests.size() != maxIterations + 1)
            {
                passed = false;
            }
            AiChatClient.ChatRequest lastRequest = scenario.client.requests.get(scenario.client.requests.size() - 1);
            if (lastRequest.getTools() != null)
            {
                passed = false;
            }
        }
        else
        {
            if (traces.size() < 2)
            {
                passed = false;
            }
            else
            {
                if (!String.valueOf(evalCase.expected.get("firstStatus")).equals(traces.get(0).getStatus()))
                {
                    passed = false;
                }
                if (!String.valueOf(evalCase.expected.get("secondStatus")).equals(traces.get(1).getStatus()))
                {
                    passed = false;
                }
            }
            String errorContains = String.valueOf(evalCase.expected.get("errorContains"));
            boolean hintFound = false;
            for (AiChatClient.ChatRequest request : scenario.client.requests)
            {
                for (AiChatClient.ChatMessage message : request.getMessages())
                {
                    if ("tool".equals(message.getRole()) && message.getContent() != null
                            && message.getContent().contains(errorContains))
                    {
                        hintFound = true;
                    }
                }
            }
            if (!hintFound)
            {
                passed = false;
            }
            if (result.getFinalContent() == null || result.getFinalContent().isBlank())
            {
                passed = false;
            }
        }
        outcome.passed = passed;
        outcome.actual = "traces=" + traces.stream()
                .map(trace -> trace.getToolName() + ":" + trace.getStatus()).toList()
                + "，final=" + result.getFinalContent();
        return outcome;
    }

    private static void expectedExpected(CaseOutcome outcome, AgentEvalCase evalCase)
    {
        if (evalCase.expected.containsKey("tools"))
        {
            outcome.expected = "tools=" + evalCase.expected.get("tools")
                    + "，finalContains=" + evalCase.expected.get("finalContains");
        }
        else
        {
            outcome.expected = evalCase.expected.toString();
        }
    }

    private static int number(Object value, int defaultValue)
    {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private static List<String> stringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (value instanceof List<?> list)
        {
            for (Object item : list)
            {
                items.add(String.valueOf(item));
            }
        }
        return items;
    }

    private static int countType(String type)
    {
        int count = 0;
        for (AgentEvalCase evalCase : cases)
        {
            if (type.equals(evalCase.type))
            {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 场景执行器
    // ------------------------------------------------------------------

    /**
     * 单个 Agent 评测场景：构造脚本化模型、真实工具与编排器
     */
    private static final class Scenario
    {
        private final String name;

        private ScriptedClient client;

        private Scenario(String name)
        {
            this.name = name;
        }

        private AgentExecutionResult run()
        {
            KnowledgeRetriever retriever = mock(KnowledgeRetriever.class);
            when(retriever.search(anyString(), anyInt(), nullable(String.class), nullable(String.class)))
                    .thenReturn(citations());
            ILawDocumentService documentService = mock(ILawDocumentService.class);
            long draftId = "search_calc_draft".equals(name) ? 9002L : 9001L;
            when(documentService.createDraft(anyLong(), any(CreateDraftRequest.class)))
                    .thenReturn(documentDraft(draftId));

            List<LawAgentTool> tools = List.of(
                    new SearchLawArticlesTool(retriever, OBJECT_MAPPER),
                    new CalcLegalAmountTool(calculator, OBJECT_MAPPER),
                    new CreateDocumentDraftTool(new DocumentTemplateRegistry(OBJECT_MAPPER),
                            new FormDataValidator(), documentService, OBJECT_MAPPER));
            LawyerAgentProperties properties = new LawyerAgentProperties();
            properties.setEnabled(true);
            properties.setMaxIterations(4);
            client = new ScriptedClient(responsesFor(name));
            LawAgentOrchestrator orchestrator = new LawAgentOrchestrator(client, properties, tools, OBJECT_MAPPER);
            List<AiChatClient.ChatMessage> messages = new ArrayList<>(List.of(
                    new AiChatClient.ChatMessage("system", "你是严谨的中国法律助手，涉及金额必须调用工具计算。"),
                    new AiChatClient.ChatMessage("user", queryFor(name))));
            return orchestrator.runStream(USER_ID, SESSION_ID, "req-" + name, messages, new ArrayList<>(),
                    new NoopSink(), () -> false);
        }

        private static String queryFor(String name)
        {
            return switch (name)
            {
                case "search_calc" -> "工作 3.5 年月薪 1 万被违法辞退，赔偿金怎么算？请引用法条。";
                case "search_draft" -> "公司拖欠工资，我要申请劳动仲裁，请检索法条并生成申请书。";
                case "search_calc_draft" -> "工作 3 年月薪 1.2 万被违法辞退，请检索法条、计算赔偿并生成仲裁申请书。";
                case "malformed_json" -> "月薪 1 万工作 3.5 年违法辞退，2N 是多少？";
                case "invalid_params" -> "请计算我的违法解除赔偿金。";
                default -> "任意问题";
            };
        }

        private static List<AiChatClient.ChatResponse> responsesFor(String name)
        {
            return switch (name)
            {
                case "search_calc" -> List.of(
                        toolResponse(toolCall("c1", "search_law_articles",
                                "{\"query\":\"违法解除劳动合同 赔偿金\",\"category\":\"labor\",\"topK\":4}")),
                        toolResponse(toolCall("c2", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                                        + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}")),
                        finalResponse("根据《中华人民共和国劳动合同法》第八十七条，违法解除赔偿金 2N 为 ¥80,000.00。"));
                case "search_draft" -> List.of(
                        toolResponse(toolCall("c1", "search_law_articles",
                                "{\"query\":\"劳动仲裁 拖欠工资\",\"category\":\"labor\",\"topK\":4}")),
                        toolResponse(toolCall("c2", "create_document_draft",
                                "{\"templateCode\":\"labor_arbitration\",\"title\":\"劳动仲裁申请书\","
                                        + "\"formData\":" + laborArbitrationFormData() + "}")),
                        finalResponse("已生成《劳动仲裁申请书》草稿，draftId=9001，可在工作台继续编辑或导出 DOCX。"));
                case "search_calc_draft" -> List.of(
                        toolResponse(toolCall("c1", "search_law_articles",
                                "{\"query\":\"违法解除劳动合同 经济补偿\",\"category\":\"labor\",\"topK\":4}")),
                        toolResponse(toolCall("c2", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":12000,"
                                        + "\"terminationType\":\"2n\",\"yearsOfService\":3}}")),
                        toolResponse(toolCall("c3", "create_document_draft",
                                "{\"templateCode\":\"labor_arbitration\",\"title\":\"劳动仲裁申请书\","
                                        + "\"formData\":" + laborArbitrationFormData() + "}")),
                        finalResponse("已检索法条、完成 2N 计算（¥72,000.00），并生成仲裁申请书草稿 draftId=9002。"));
                case "malformed_json" -> List.of(
                        toolResponse(toolCall("c1", "calc_legal_amount", "{not-json")),
                        toolResponse(toolCall("c2", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                                        + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}")),
                        finalResponse("已修正工具参数，2N 赔偿金为 ¥80,000.00。"));
                case "invalid_params" -> List.of(
                        toolResponse(toolCall("c1", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"terminationType\":\"2n\","
                                        + "\"yearsOfService\":3.5}}")),
                        toolResponse(toolCall("c2", "calc_legal_amount",
                                "{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                                        + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}")),
                        finalResponse("已补齐 monthlySalary 字段，2N 赔偿金为 ¥80,000.00。"));
                case "circuit_breaker" -> List.of(
                        toolResponse(toolCall("c1", "unknown_tool", "{}")),
                        toolResponse(toolCall("c2", "unknown_tool", "{}")),
                        toolResponse(toolCall("c3", "unknown_tool", "{}")),
                        toolResponse(toolCall("c4", "unknown_tool", "{}")),
                        finalResponse("兜底回答"));
                default -> List.of(finalResponse("兜底回答"));
            };
        }

        private static String laborArbitrationFormData()
        {
            return "{\"applicantName\":\"张三\",\"applicantGender\":\"男\",\"applicantIdCard\":\"110101199001011234\","
                    + "\"applicantPhone\":\"13800138000\",\"applicantAddress\":\"北京市朝阳区示例路 1 号\","
                    + "\"employerName\":\"示例科技有限公司\",\"employerAddress\":\"北京市海淀区示例大厦 8 层\","
                    + "\"entryDate\":\"2021-01-01\",\"monthlySalary\":12000,"
                    + "\"arbitrationCommission\":\"北京市朝阳区劳动人事争议仲裁委员会\","
                    + "\"claims\":\"请求支付违法解除劳动合同赔偿金 36000 元。\","
                    + "\"factsAndReasons\":\"申请人于 2021 年 1 月入职，2024 年 1 月被违法解除劳动合同。\"}";
        }
    }

    private static AiChatClient.ChatResponse toolResponse(AiChatClient.ToolCall call)
    {
        return new AiChatClient.ChatResponse(null, null, List.of(call), "tool_calls", usage(100, 40), null);
    }

    private static AiChatClient.ChatResponse finalResponse(String content)
    {
        return new AiChatClient.ChatResponse(content, null, List.of(), "stop", usage(200, 80), null);
    }

    private static AiChatClient.ToolCall toolCall(String id, String name, String arguments)
    {
        return new AiChatClient.ToolCall(id, name, arguments);
    }

    private static AiChatClient.TokenUsage usage(int prompt, int completion)
    {
        return new AiChatClient.TokenUsage(prompt, completion, prompt + completion, false);
    }

    private static List<CitationVO> citations()
    {
        CitationVO first = new CitationVO();
        first.setChunkId(47L);
        first.setDocId(1L);
        first.setDocTitle("中华人民共和国劳动合同法");
        first.setArticleNo("第四十七条");
        first.setSnippet("经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。");
        first.setScore(8.1);
        CitationVO second = new CitationVO();
        second.setChunkId(87L);
        second.setDocId(1L);
        second.setDocTitle("中华人民共和国劳动合同法");
        second.setArticleNo("第八十七条");
        second.setSnippet("用人单位违反本法规定解除或者终止劳动合同的，应当依照经济补偿标准的二倍支付赔偿金。");
        second.setScore(7.6);
        return List.of(first, second);
    }

    private static DocumentDraftVO documentDraft(long draftId)
    {
        DocumentDraftVO draft = new DocumentDraftVO();
        draft.setId(draftId);
        draft.setTemplateCode("labor_arbitration");
        draft.setTemplateName("劳动仲裁申请书");
        draft.setTitle("劳动仲裁申请书");
        draft.setStatus("draft");
        return draft;
    }

    // ------------------------------------------------------------------
    // 评测数据集加载
    // ------------------------------------------------------------------

    private static List<AgentEvalCase> loadDataset() throws Exception
    {
        try (InputStream input = AgentToolBenchmarkEvalTest.class.getClassLoader()
                .getResourceAsStream("eval/agent_eval_dataset.json"))
        {
            if (input == null)
            {
                throw new IllegalStateException("缺少 Agent 评测集: eval/agent_eval_dataset.json");
            }
            AgentEvalDataset dataset = OBJECT_MAPPER.readValue(input, new TypeReference<AgentEvalDataset>()
            {
            });
            return dataset == null || dataset.cases == null ? new ArrayList<>() : dataset.cases;
        }
    }

    // ------------------------------------------------------------------
    // 结果模型
    // ------------------------------------------------------------------

    private static final class CaseOutcome
    {
        private boolean passed;

        private String expected = "";

        private String actual = "";

        private int assertionCount;

        private int passedAssertions;
    }

    /**
     * 评测集根节点
     */
    public static class AgentEvalDataset
    {
        private String version;

        private String description;

        private List<AgentEvalCase> cases = new ArrayList<>();

        public String getVersion()
        {
            return version;
        }

        public void setVersion(String version)
        {
            this.version = version;
        }

        public String getDescription()
        {
            return description;
        }

        public void setDescription(String description)
        {
            this.description = description;
        }

        public List<AgentEvalCase> getCases()
        {
            return cases;
        }

        public void setCases(List<AgentEvalCase> cases)
        {
            this.cases = cases == null ? new ArrayList<>() : cases;
        }
    }

    /**
     * 单道 Agent 评测题
     */
    public static class AgentEvalCase
    {
        private String id;

        private String type;

        private String scenario;

        private String title;

        private List<Assertion> assertions = new ArrayList<>();

        private Map<String, Object> expected = new LinkedHashMap<>();

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getType()
        {
            return type;
        }

        public void setType(String type)
        {
            this.type = type;
        }

        public String getScenario()
        {
            return scenario;
        }

        public void setScenario(String scenario)
        {
            this.scenario = scenario;
        }

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public List<Assertion> getAssertions()
        {
            return assertions;
        }

        public void setAssertions(List<Assertion> assertions)
        {
            this.assertions = assertions == null ? new ArrayList<>() : assertions;
        }

        public Map<String, Object> getExpected()
        {
            return expected;
        }

        public void setExpected(Map<String, Object> expected)
        {
            this.expected = expected == null ? new LinkedHashMap<>() : expected;
        }
    }

    /**
     * 单项确定性计算断言
     */
    public static class Assertion
    {
        private String calcType;

        private Map<String, Object> params = new LinkedHashMap<>();

        private Map<String, Object> expected = new LinkedHashMap<>();

        public String getCalcType()
        {
            return calcType;
        }

        public void setCalcType(String calcType)
        {
            this.calcType = calcType;
        }

        public Map<String, Object> getParams()
        {
            return params;
        }

        public void setParams(Map<String, Object> params)
        {
            this.params = params == null ? new LinkedHashMap<>() : params;
        }

        public Map<String, Object> getExpected()
        {
            return expected;
        }

        public void setExpected(Map<String, Object> expected)
        {
            this.expected = expected == null ? new LinkedHashMap<>() : expected;
        }
    }

    // ------------------------------------------------------------------
    // 测试桩
    // ------------------------------------------------------------------

    /**
     * 按脚本返回响应的模型客户端
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
            throw new UnsupportedOperationException("评测仅使用工具调用");
        }

        @Override
        public String chat(List<ChatMessage> messages, Duration timeout)
        {
            throw new UnsupportedOperationException("评测仅使用工具调用");
        }

        @Override
        public String streamChat(List<ChatMessage> messages, Consumer<String> onDelta, BooleanSupplier cancelled)
        {
            throw new UnsupportedOperationException("评测仅使用工具调用");
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
            return new ChatResponse("兜底回答", null, List.of(), "stop", usage(1, 1), null);
        }
    }

    /**
     * 无副作用事件汇
     */
    private static final class NoopSink implements LawAgentOrchestrator.AgentEventSink
    {
        @Override
        public void onCitation(List<CitationVO> citations)
        {
        }

        @Override
        public void onToolCall(Map<String, Object> event)
        {
        }

        @Override
        public void onToolResult(Map<String, Object> event)
        {
        }

        @Override
        public void onDelta(String delta)
        {
        }
    }
}
