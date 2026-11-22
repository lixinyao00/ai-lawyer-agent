package com.ruoyi.lawyer.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiChatClient.ChatMessage;
import com.ruoyi.lawyer.client.AiChatClient.ChatRequest;
import com.ruoyi.lawyer.client.AiChatClient.ChatResponse;
import com.ruoyi.lawyer.client.AiChatClient.StreamListener;
import com.ruoyi.lawyer.client.AiChatClient.TokenUsage;
import com.ruoyi.lawyer.client.AiChatClient.ToolCall;
import com.ruoyi.lawyer.client.AiChatClient.ToolDefinition;
import com.ruoyi.lawyer.client.TokenEstimator;
import com.ruoyi.lawyer.config.LawyerAgentProperties;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.ToolCallTraceVO;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * ReAct Tool Calling 法律 Agent 编排器
 *
 * <p>铁律三：未知工具、arguments JSON 非法、参数校验失败或工具执行异常，一律捕获并封装为
 * {@code status="error"} 的 {@link ToolExecutionResult}，通过 SSE 推送 {@code tool_result}
 * 并以 {@code role="tool"} 消息回传模型，驱动下一轮自愈，绝不中断 SSE。</p>
 *
 * @author ruoyi
 */
@Component
public class LawAgentOrchestrator
{
    private static final Logger log = LoggerFactory.getLogger(LawAgentOrchestrator.class);

    /** 达到迭代上限后追加的收尾提示 */
    private static final String FINALIZE_PROMPT = "已达到本轮工具调用迭代上限。请立即停止调用工具，"
            + "基于以上全部对话、工具执行结果与已检索法条，直接给出完整、专业、可执行的中文最终回答；"
            + "若仍有要素缺失或工具报错，请如实说明并一次性向用户追问所需信息。";

    /** 最终兜底文案 */
    private static final String FALLBACK_ANSWER = "抱歉，本次回答生成未能完整完成。请您补充关键事实（如时间、金额、"
            + "合同约定等）后重新提问，我会据此给出更准确的法律分析与建议。";

    private final AiChatClient aiChatClient;

    private final LawyerAgentProperties properties;

    private final Map<String, LawAgentTool> tools = new LinkedHashMap<>();

    private final ObjectMapper objectMapper;

    @Autowired
    public LawAgentOrchestrator(AiChatClient aiChatClient, LawyerAgentProperties properties,
            List<LawAgentTool> toolList, ObjectMapper objectMapper)
    {
        this.aiChatClient = aiChatClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (toolList != null)
        {
            for (LawAgentTool tool : toolList)
            {
                if (tool != null && tool.name() != null)
                {
                    tools.put(tool.name(), tool);
                }
            }
        }
    }

    /**
     * 流式 ReAct 编排
     *
     * @param userId 当前用户ID（严禁在线程内调用 SecurityUtils）
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @param modelMessages 初始消息（system → history → user）
     * @param initialCitations 首轮检索法条
     * @param sink SSE 事件回调，可为 null
     * @param cancelled 取消判断，可为 null
     * @return 执行结果
     */
    public AgentExecutionResult runStream(Long userId, Long sessionId, String requestId,
            List<ChatMessage> modelMessages, List<CitationVO> initialCitations, AgentEventSink sink,
            BooleanSupplier cancelled)
    {
        return execute(userId, sessionId, requestId, modelMessages, initialCitations,
                sink == null ? AgentEventSink.NOOP : sink, cancelled == null ? () -> false : cancelled, true);
    }

    /**
     * 同步 ReAct 编排
     *
     * @param userId 当前用户ID
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @param modelMessages 初始消息
     * @param initialCitations 首轮检索法条
     * @return 执行结果
     */
    public AgentExecutionResult runSync(Long userId, Long sessionId, String requestId,
            List<ChatMessage> modelMessages, List<CitationVO> initialCitations)
    {
        return execute(userId, sessionId, requestId, modelMessages, initialCitations, AgentEventSink.NOOP,
                () -> false, false);
    }

    private AgentExecutionResult execute(Long userId, Long sessionId, String requestId,
            List<ChatMessage> modelMessages, List<CitationVO> initialCitations, AgentEventSink sink,
            BooleanSupplier cancelled, boolean streaming)
    {
        long startNanos = System.nanoTime();
        long[] firstTokenNanos = {-1L};
        List<ChatMessage> messages = new ArrayList<>(modelMessages == null ? List.of() : modelMessages);
        List<CitationVO> citations = new ArrayList<>();
        if (initialCitations != null)
        {
            citations.addAll(initialCitations);
        }
        ToolExecutionContext context = new ToolExecutionContext(userId, sessionId, requestId, citations);
        List<ToolCallTraceVO> traces = new ArrayList<>();
        List<ToolDefinition> toolDefinitions = buildToolDefinitions();
        int maxIterations = properties == null ? 4 : Math.max(1, properties.getMaxIterations());
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        String finalContent = null;

        for (int step = 1; step <= maxIterations; step++)
        {
            if (cancelled.getAsBoolean())
            {
                break;
            }
            ChatResponse response = callModel(messages, toolDefinitions, streaming, sink, firstTokenNanos, cancelled);
            TokenUsage usage = response.getUsage();
            if (usage == null)
            {
                usage = TokenEstimator.estimate(messages, response.getContent());
            }
            promptTokens += nullSafe(usage.getPromptTokens());
            completionTokens += nullSafe(usage.getCompletionTokens());
            totalTokens += usage.getTotalTokens() == null
                    ? nullSafe(usage.getPromptTokens()) + nullSafe(usage.getCompletionTokens())
                    : nullSafe(usage.getTotalTokens());

            List<ToolCall> toolCalls = response.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty())
            {
                finalContent = response.getContent() == null ? "" : response.getContent();
                break;
            }
            toolCalls = normalizeToolCalls(toolCalls, step);
            if (toolCalls.isEmpty())
            {
                finalContent = response.getContent() == null ? "" : response.getContent();
                break;
            }

            messages.add(ChatMessage.assistantToolCalls(response.getContent(), toolCalls));
            for (ToolCall call : toolCalls)
            {
                if (cancelled.getAsBoolean())
                {
                    break;
                }
                ChatMessage toolMessage = executeToolSafely(call, step, context, traces, sink);
                if (toolMessage != null)
                {
                    messages.add(toolMessage);
                }
            }
        }

        if (finalContent == null && !cancelled.getAsBoolean())
        {
            // 达到迭代上限仍未产生自然语言回答：移除 tools，追加收尾提示强制生成
            List<ChatMessage> finalMessages = new ArrayList<>(messages);
            finalMessages.add(new ChatMessage("system", FINALIZE_PROMPT));
            ChatResponse response = callModel(finalMessages, null, streaming, sink, firstTokenNanos, cancelled);
            TokenUsage usage = response.getUsage();
            if (usage == null)
            {
                usage = TokenEstimator.estimate(finalMessages, response.getContent());
            }
            promptTokens += nullSafe(usage.getPromptTokens());
            completionTokens += nullSafe(usage.getCompletionTokens());
            totalTokens += usage.getTotalTokens() == null
                    ? nullSafe(usage.getPromptTokens()) + nullSafe(usage.getCompletionTokens())
                    : nullSafe(usage.getTotalTokens());
            finalContent = response.getContent();
        }

        if (finalContent == null || finalContent.isBlank())
        {
            finalContent = cancelled.getAsBoolean() ? "" : FALLBACK_ANSWER;
        }

        AgentExecutionResult result = new AgentExecutionResult();
        result.setFinalContent(finalContent);
        result.setCitations(new ArrayList<>(context.getCollectedCitations()));
        result.setToolCalls(traces);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        result.setTotalTokens(totalTokens == 0 ? promptTokens + completionTokens : totalTokens);
        result.setTtftMs(firstTokenNanos[0] < 0 ? null
                : Math.max(0L, (firstTokenNanos[0] - startNanos) / 1_000_000L));
        result.setTotalLatencyMs(Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L));
        return result;
    }

    private ChatResponse callModel(List<ChatMessage> messages, List<ToolDefinition> toolDefinitions, boolean streaming,
            AgentEventSink sink, long[] firstTokenNanos, BooleanSupplier cancelled)
    {
        if (!streaming)
        {
            return aiChatClient.chatWithTools(new ChatRequest(messages, toolDefinitions,
                    toolDefinitions == null || toolDefinitions.isEmpty() ? null : "auto", (Duration) null));
        }
        StreamListener listener = new StreamListener()
        {
            @Override
            public void onContentDelta(String delta)
            {
                if (delta == null || delta.isEmpty())
                {
                    return;
                }
                if (firstTokenNanos[0] < 0)
                {
                    firstTokenNanos[0] = System.nanoTime();
                }
                sink.onDelta(delta);
            }
        };
        return aiChatClient.streamChatWithTools(new ChatRequest(messages, toolDefinitions,
                toolDefinitions == null || toolDefinitions.isEmpty() ? null : "auto", (Duration) null), listener,
                cancelled);
    }

    private ChatMessage executeToolSafely(ToolCall call, int step, ToolExecutionContext context,
            List<ToolCallTraceVO> traces, AgentEventSink sink)
    {
        long toolStartNanos = System.nanoTime();
        String callId = call == null || call.getId() == null ? "call_" + step + "_" + (traces.size() + 1)
                : call.getId();
        String toolName = call == null || call.getName() == null ? "" : call.getName();
        Map<String, Object> arguments = new LinkedHashMap<>();
        boolean parseFailed = false;
        String parseError = null;

        String rawArguments = call == null ? null : call.getArguments();
        if (rawArguments != null && !rawArguments.isBlank())
        {
            try
            {
                Map<String, Object> parsed = objectMapper.readValue(rawArguments,
                        new TypeReference<Map<String, Object>>()
                        {
                        });
                if (parsed != null)
                {
                    arguments.putAll(parsed);
                }
            }
            catch (Exception e)
            {
                parseFailed = true;
                parseError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                arguments.put("_raw", rawArguments);
            }
        }

        LawAgentTool tool = tools.get(toolName);
        String displayTitle = tool == null ? "正在执行工具：" + toolName
                : tool.displayTitle(arguments);
        Map<String, Object> callEvent = new LinkedHashMap<>();
        callEvent.put("callId", callId);
        callEvent.put("step", step);
        callEvent.put("toolName", toolName);
        callEvent.put("displayTitle", displayTitle);
        callEvent.put("arguments", arguments);
        sink.onToolCall(callEvent);

        ToolExecutionResult result;
        if (parseFailed)
        {
            result = ToolExecutionResult.error("工具参数 JSON 解析失败",
                    "工具 " + toolName + " 的 arguments 不是合法 JSON 对象（" + parseError
                            + "）。请输出严格合法的 JSON 对象，例如 {\"query\":\"劳动合同法第四十七条\"}，然后重新调用。");
        }
        else if (tool == null)
        {
            result = ToolExecutionResult.error("工具不存在",
                    "不存在名为 " + toolName + " 的工具。当前可用工具：" + String.join("、", tools.keySet())
                            + "。请选择正确工具后重试，或直接基于已有信息回答用户。");
        }
        else
        {
            try
            {
                result = tool.execute(context, arguments);
            }
            catch (Exception e)
            {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                log.warn("工具执行异常已自愈封装, tool={}, message={}", toolName, message);
                result = ToolExecutionResult.error("工具执行异常",
                        "工具 " + toolName + " 执行异常（" + message + "）。请检查参数后重试，或改用其他工具/直接回答。");
            }
        }
        if (result == null)
        {
            result = ToolExecutionResult.error("工具无返回结果", "工具 " + toolName + " 未返回结果，请重试或直接回答。");
        }
        long durationMs = Math.max(0L, (System.nanoTime() - toolStartNanos) / 1_000_000L);

        List<CitationVO> newCitations = result.getNewCitations();
        if (newCitations != null && !newCitations.isEmpty())
        {
            context.mergeCitations(newCitations);
            sink.onCitation(new ArrayList<>(context.getCollectedCitations()));
        }

        ToolCallTraceVO trace = new ToolCallTraceVO();
        trace.setCallId(callId);
        trace.setStep(step);
        trace.setToolName(toolName);
        trace.setDisplayTitle(displayTitle);
        trace.setArguments(arguments);
        trace.setStatus(result.getStatus());
        trace.setResultSummary(result.getSummary());
        trace.setDurationMs(durationMs);
        trace.setDraftId(result.getDraftId());
        traces.add(trace);

        Map<String, Object> resultEvent = new LinkedHashMap<>();
        resultEvent.put("callId", callId);
        resultEvent.put("step", step);
        resultEvent.put("toolName", toolName);
        resultEvent.put("status", result.getStatus());
        resultEvent.put("summary", result.getSummary());
        resultEvent.put("detail", result.getDetail());
        resultEvent.put("durationMs", durationMs);
        resultEvent.put("draftId", result.getDraftId());
        sink.onToolResult(resultEvent);

        String modelContent = result.getModelContent();
        if (modelContent == null || modelContent.isBlank())
        {
            modelContent = result.getSummary();
        }
        return ChatMessage.toolResult(callId, toolName, modelContent);
    }

    private List<ToolDefinition> buildToolDefinitions()
    {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (LawAgentTool tool : tools.values())
        {
            definitions.add(new ToolDefinition(tool.name(), tool.description(), tool.parametersSchema()));
        }
        return definitions;
    }

    /**
     * 补齐模型缺失的工具调用ID，确保 assistant.tool_calls 与 role=tool 的 tool_call_id 严格一一对应
     */
    private List<ToolCall> normalizeToolCalls(List<ToolCall> toolCalls, int step)
    {
        List<ToolCall> normalized = new ArrayList<>(toolCalls.size());
        for (int i = 0; i < toolCalls.size(); i++)
        {
            ToolCall call = toolCalls.get(i);
            if (call == null)
            {
                continue;
            }
            String id = call.getId();
            if (id == null || id.isBlank())
            {
                id = "call_" + step + "_" + (i + 1);
            }
            normalized.add(new ToolCall(id, call.getName() == null ? "" : call.getName(), call.getArguments()));
        }
        return normalized;
    }

    private int nullSafe(Integer value)
    {
        return value == null ? 0 : value;
    }

    /**
     * 编排事件回调：由聊天服务转成对应 SSE 事件
     */
    public interface AgentEventSink
    {
        AgentEventSink NOOP = new AgentEventSink()
        {
        };

        default void onCitation(List<CitationVO> citations)
        {
        }

        default void onToolCall(Map<String, Object> event)
        {
        }

        default void onToolResult(Map<String, Object> event)
        {
        }

        default void onDelta(String delta)
        {
        }
    }
}
