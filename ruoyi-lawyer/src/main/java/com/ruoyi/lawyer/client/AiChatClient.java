package com.ruoyi.lawyer.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 模型问答客户端
 *
 * @author ruoyi
 */
public interface AiChatClient
{
    /**
     * 一次非流式问答调用（使用 request-timeout）
     *
     * @param messages 按顺序排列的角色和消息，第一条通常是 system 提示词
     * @return 模型回答正文
     */
    String chat(List<ChatMessage> messages);

    /**
     * 一次非流式问答调用，自定义总超时
     *
     * @param messages 按顺序排列的角色和消息
     * @param timeout 本次调用总超时
     * @return 模型回答正文
     */
    String chat(List<ChatMessage> messages, Duration timeout);

    /**
     * 流式问答调用
     *
     * @param messages 按顺序排列的角色和消息
     * @param onDelta 增量内容回调
     * @param cancelled 取消判断，返回 true 时停止读取
     * @return 已接收的完整回答正文
     */
    String streamChat(List<ChatMessage> messages, Consumer<String> onDelta, BooleanSupplier cancelled);

    /**
     * 带工具定义的非流式调用（V2.0 默认实现不支持 Tool Calling，自动退化为普通问答）
     *
     * @param request 请求
     * @return 模型响应
     */
    default ChatResponse chatWithTools(ChatRequest request)
    {
        List<ChatMessage> messages = request == null ? new ArrayList<>() : request.getMessages();
        String content = request == null || request.getTimeout() == null
                ? chat(messages) : chat(messages, request.getTimeout());
        return new ChatResponse(content, null, new ArrayList<>(), "stop",
                TokenEstimator.estimate(messages, content), null);
    }

    /**
     * 带工具定义的流式调用（V2.0 默认实现不支持 Tool Calling，自动退化为普通流式问答）
     *
     * @param request 请求
     * @param listener 增量回调，可为 null
     * @param cancelled 取消判断，可为 null
     * @return 模型响应
     */
    default ChatResponse streamChatWithTools(ChatRequest request, StreamListener listener, BooleanSupplier cancelled)
    {
        List<ChatMessage> messages = request == null ? new ArrayList<>() : request.getMessages();
        Consumer<String> onDelta = listener == null ? delta -> { } : listener::onContentDelta;
        String content = streamChat(messages, onDelta, cancelled);
        return new ChatResponse(content, null, new ArrayList<>(), "stop",
                TokenEstimator.estimate(messages, content), null);
    }

    /**
     * 聊天消息
     */
    class ChatMessage
    {
        private final String role;

        private final String content;

        /** assistant 发起工具调用时携带 */
        private List<ToolCall> toolCalls;

        /** role=tool 回传工具结果时携带 */
        private String toolCallId;

        /** role=tool 回传工具结果时携带的工具名 */
        private String name;

        public ChatMessage(String role, String content)
        {
            this.role = role;
            this.content = content;
        }

        public ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId, String name)
        {
            this.role = role;
            this.content = content;
            this.toolCalls = toolCalls;
            this.toolCallId = toolCallId;
            this.name = name;
        }

        /**
         * assistant 发起工具调用的消息
         *
         * @param content 可空的说明正文
         * @param toolCalls 工具调用列表
         * @return 消息
         */
        public static ChatMessage assistantToolCalls(String content, List<ToolCall> toolCalls)
        {
            return new ChatMessage("assistant", content, toolCalls, null, null);
        }

        /**
         * role=tool 回传工具执行结果
         *
         * @param toolCallId 对应的工具调用ID
         * @param name 工具名
         * @param content 结构化结果正文
         * @return 消息
         */
        public static ChatMessage toolResult(String toolCallId, String name, String content)
        {
            return new ChatMessage("tool", content, null, toolCallId, name);
        }

        public String getRole()
        {
            return role;
        }

        public String getContent()
        {
            return content;
        }

        public List<ToolCall> getToolCalls()
        {
            return toolCalls;
        }

        public void setToolCalls(List<ToolCall> toolCalls)
        {
            this.toolCalls = toolCalls;
        }

        public String getToolCallId()
        {
            return toolCallId;
        }

        public void setToolCallId(String toolCallId)
        {
            this.toolCallId = toolCallId;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }
    }

    /**
     * 工具定义（OpenAI function calling）
     */
    class ToolDefinition
    {
        private final String name;

        private final String description;

        private final Map<String, Object> parameters;

        public ToolDefinition(String name, String description, Map<String, Object> parameters)
        {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName()
        {
            return name;
        }

        public String getDescription()
        {
            return description;
        }

        public Map<String, Object> getParameters()
        {
            return parameters;
        }
    }

    /**
     * 模型发起的工具调用，arguments 为原始 JSON 字符串
     */
    class ToolCall
    {
        private final String id;

        private final String name;

        private final String arguments;

        public ToolCall(String id, String name, String arguments)
        {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }

        public String getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public String getArguments()
        {
            return arguments;
        }
    }

    /**
     * Token 用量
     */
    class TokenUsage
    {
        private final Integer promptTokens;

        private final Integer completionTokens;

        private final Integer totalTokens;

        private final boolean estimated;

        public TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens, boolean estimated)
        {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.estimated = estimated;
        }

        public Integer getPromptTokens()
        {
            return promptTokens;
        }

        public Integer getCompletionTokens()
        {
            return completionTokens;
        }

        public Integer getTotalTokens()
        {
            return totalTokens;
        }

        public boolean isEstimated()
        {
            return estimated;
        }
    }

    /**
     * 带工具的请求
     */
    class ChatRequest
    {
        private final List<ChatMessage> messages;

        private final List<ToolDefinition> tools;

        private final String toolChoice;

        private final Duration timeout;

        public ChatRequest(List<ChatMessage> messages, List<ToolDefinition> tools, String toolChoice, Duration timeout)
        {
            this.messages = messages;
            this.tools = tools;
            this.toolChoice = toolChoice;
            this.timeout = timeout;
        }

        public List<ChatMessage> getMessages()
        {
            return messages;
        }

        public List<ToolDefinition> getTools()
        {
            return tools;
        }

        public String getToolChoice()
        {
            return toolChoice;
        }

        public Duration getTimeout()
        {
            return timeout;
        }
    }

    /**
     * 模型响应
     */
    class ChatResponse
    {
        private final String content;

        private final String reasoningContent;

        private final List<ToolCall> toolCalls;

        private final String finishReason;

        private final TokenUsage usage;

        private final Long ttftMs;

        public ChatResponse(String content, String reasoningContent, List<ToolCall> toolCalls, String finishReason,
                TokenUsage usage, Long ttftMs)
        {
            this.content = content;
            this.reasoningContent = reasoningContent;
            this.toolCalls = toolCalls == null ? new ArrayList<>() : toolCalls;
            this.finishReason = finishReason;
            this.usage = usage;
            this.ttftMs = ttftMs;
        }

        public String getContent()
        {
            return content;
        }

        public String getReasoningContent()
        {
            return reasoningContent;
        }

        public List<ToolCall> getToolCalls()
        {
            return toolCalls;
        }

        public String getFinishReason()
        {
            return finishReason;
        }

        public TokenUsage getUsage()
        {
            return usage;
        }

        public Long getTtftMs()
        {
            return ttftMs;
        }
    }

    /**
     * 流式增量回调
     */
    interface StreamListener
    {
        default void onContentDelta(String delta)
        {
        }

        default void onReasoningDelta(String delta)
        {
        }
    }
}
