package com.ruoyi.lawyer.client;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * OpenAI 兼容 SSE 流式响应解析器
 *
 * <p>负责把单行 SSE 文本解析成增量内容；非 data 行、空 delta、[DONE] 与异常 JSON 均返回 null。</p>
 *
 * @author ruoyi
 */
public class OpenAiStreamParser
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiStreamParser.class);

    /** 流结束标记 */
    public static final String DONE_MARKER = "[DONE]";

    private static final String DATA_PREFIX = "data:";

    /**
     * 是否为 data 行
     *
     * @param line 原始行
     * @return 是否 data 行
     */
    public boolean isDataLine(String line)
    {
        return line != null && line.trim().startsWith(DATA_PREFIX);
    }

    /**
     * 是否为结束标记行
     *
     * @param line 原始行
     * @return 是否 [DONE]
     */
    public boolean isDone(String line)
    {
        if (!isDataLine(line))
        {
            return false;
        }
        return DONE_MARKER.equals(payload(line));
    }

    /**
     * 解析单行 SSE，返回增量文本
     *
     * @param line 原始行
     * @return 增量文本；非 data 行、空 delta、[DONE]、异常 JSON 返回 null
     */
    public String parseLine(String line)
    {
        if (!isDataLine(line))
        {
            return null;
        }
        String payload = payload(line);
        if (payload.isEmpty() || DONE_MARKER.equals(payload))
        {
            return null;
        }
        try
        {
            JSONObject root = JSONObject.parseObject(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            if (choice == null)
            {
                return null;
            }
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null)
            {
                return null;
            }
            String content = delta.getString("content");
            return content == null || content.isEmpty() ? null : content;
        }
        catch (Exception e)
        {
            // 单行异常不应中断整个流，记录后跳过
            log.warn("SSE 数据行解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String payload(String line)
    {
        return line.trim().substring(DATA_PREFIX.length()).trim();
    }

    /**
     * 解析 V2.0 流式分片：正文、推理内容、工具调用增量、结束原因与用量尾包
     *
     * @param line 原始 SSE 行
     * @return 解析结果；非 data 行、[DONE]、异常 JSON 或空分片返回 null
     */
    public StreamChunk parseChunk(String line)
    {
        if (!isDataLine(line))
        {
            return null;
        }
        String payload = payload(line);
        if (payload.isEmpty() || DONE_MARKER.equals(payload))
        {
            return null;
        }
        try
        {
            JSONObject root = JSONObject.parseObject(payload);
            AiChatClient.TokenUsage usage = parseUsage(root.getJSONObject("usage"));
            String contentDelta = null;
            String reasoningDelta = null;
            String finishReason = null;
            List<ToolCallDelta> toolCallDeltas = new ArrayList<>();

            JSONArray choices = root.getJSONArray("choices");
            if (choices != null && !choices.isEmpty())
            {
                JSONObject choice = choices.getJSONObject(0);
                if (choice != null)
                {
                    finishReason = emptyToNull(choice.getString("finish_reason"));
                    JSONObject delta = choice.getJSONObject("delta");
                    if (delta != null)
                    {
                        contentDelta = emptyToNull(delta.getString("content"));
                        reasoningDelta = emptyToNull(delta.getString("reasoning_content"));
                        JSONArray toolCalls = delta.getJSONArray("tool_calls");
                        if (toolCalls != null)
                        {
                            for (int i = 0; i < toolCalls.size(); i++)
                            {
                                JSONObject item = toolCalls.getJSONObject(i);
                                if (item == null)
                                {
                                    continue;
                                }
                                Integer index = item.getInteger("index");
                                JSONObject function = item.getJSONObject("function");
                                toolCallDeltas.add(new ToolCallDelta(index == null ? i : index,
                                        emptyToNull(item.getString("id")),
                                        function == null ? null : emptyToNull(function.getString("name")),
                                        function == null ? null : emptyToNull(function.getString("arguments"))));
                            }
                        }
                    }
                }
            }

            if (contentDelta == null && reasoningDelta == null && toolCallDeltas.isEmpty()
                    && finishReason == null && usage == null)
            {
                return null;
            }
            return new StreamChunk(contentDelta, reasoningDelta, toolCallDeltas, finishReason, usage);
        }
        catch (Exception e)
        {
            // 单行异常不应中断整个流，记录后跳过
            log.warn("SSE V2 数据行解析失败: {}", e.getMessage());
            return null;
        }
    }

    private AiChatClient.TokenUsage parseUsage(JSONObject usage)
    {
        if (usage == null)
        {
            return null;
        }
        Integer prompt = usage.getInteger("prompt_tokens");
        Integer completion = usage.getInteger("completion_tokens");
        Integer total = usage.getInteger("total_tokens");
        if (prompt == null && completion == null && total == null)
        {
            return null;
        }
        int promptValue = prompt == null ? 0 : prompt;
        int completionValue = completion == null ? 0 : completion;
        int totalValue = total == null ? promptValue + completionValue : total;
        return new AiChatClient.TokenUsage(promptValue, completionValue, totalValue, false);
    }

    private String emptyToNull(String value)
    {
        return value == null || value.isEmpty() ? null : value;
    }

    /**
     * 单个 SSE 分片
     */
    public static final class StreamChunk
    {
        private final String contentDelta;

        private final String reasoningDelta;

        private final List<ToolCallDelta> toolCallDeltas;

        private final String finishReason;

        private final AiChatClient.TokenUsage usage;

        public StreamChunk(String contentDelta, String reasoningDelta, List<ToolCallDelta> toolCallDeltas,
                String finishReason, AiChatClient.TokenUsage usage)
        {
            this.contentDelta = contentDelta;
            this.reasoningDelta = reasoningDelta;
            this.toolCallDeltas = toolCallDeltas == null ? new ArrayList<>() : toolCallDeltas;
            this.finishReason = finishReason;
            this.usage = usage;
        }

        public String getContentDelta()
        {
            return contentDelta;
        }

        public String getReasoningDelta()
        {
            return reasoningDelta;
        }

        public List<ToolCallDelta> getToolCallDeltas()
        {
            return toolCallDeltas;
        }

        public String getFinishReason()
        {
            return finishReason;
        }

        public AiChatClient.TokenUsage getUsage()
        {
            return usage;
        }
    }

    /**
     * 工具调用增量分片
     */
    public static final class ToolCallDelta
    {
        private final int index;

        private final String id;

        private final String name;

        private final String argumentsFragment;

        public ToolCallDelta(int index, String id, String name, String argumentsFragment)
        {
            this.index = index;
            this.id = id;
            this.name = name;
            this.argumentsFragment = argumentsFragment;
        }

        public int getIndex()
        {
            return index;
        }

        public String getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String getArgumentsFragment()
        {
            return argumentsFragment;
        }
    }
}
