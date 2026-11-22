package com.ruoyi.lawyer.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.lawyer.config.LawyerAiProperties;

/**
 * OpenAI 兼容协议的模型客户端实现
 *
 * @author ruoyi
 */
@Component
public class HttpAiChatClient implements AiChatClient
{
    private static final Logger log = LoggerFactory.getLogger(HttpAiChatClient.class);

    @Autowired
    private LawyerAiProperties properties;

    private final OpenAiStreamParser streamParser = new OpenAiStreamParser();

    private volatile HttpClient httpClient;

    private HttpClient client()
    {
        HttpClient local = httpClient;
        if (local == null)
        {
            synchronized (this)
            {
                local = httpClient;
                if (local == null)
                {
                    local = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.getConnectTimeout()))
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    @Override
    public String chat(List<ChatMessage> messages)
    {
        return chat(messages, Duration.ofMillis(properties.getRequestTimeout()));
    }

    @Override
    public String chat(List<ChatMessage> messages, Duration timeout)
    {
        if (!properties.isConfigured())
        {
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务未配置，请设置 LAWYER_AI_API_KEY 等配置");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages, false).toJSONString()))
                .build();

        HttpResponse<String> response;
        try
        {
            response = client().send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (HttpTimeoutException e)
        {
            log.warn("模型请求超时, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求超时，请稍后重试", e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求被中断，请稍后重试", e);
        }
        catch (Exception e)
        {
            log.warn("模型请求异常: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务连接失败，请稍后重试", e);
        }

        if (response.statusCode() == 401)
        {
            log.warn("模型服务鉴权失败, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.HTTP, 401, "模型服务鉴权失败，请检查 API Key");
        }
        if (response.statusCode() != 200)
        {
            // 不把服务商原始响应写进日志或抛给用户
            log.warn("模型服务返回异常状态, status={}", response.statusCode());
            throw new AiClientException(AiClientException.Type.HTTP, response.statusCode(),
                    "模型服务暂时不可用，请稍后重试");
        }

        return parseContent(response.body());
    }

    @Override
    public ChatResponse chatWithTools(ChatRequest request)
    {
        if (!properties.isConfigured())
        {
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务未配置，请设置 LAWYER_AI_API_KEY 等配置");
        }
        List<ChatMessage> messages = request == null || request.getMessages() == null
                ? new ArrayList<>() : request.getMessages();
        Duration timeout = request == null || request.getTimeout() == null
                ? Duration.ofMillis(properties.getRequestTimeout()) : request.getTimeout();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildBody(messages, false, tools(request), toolChoice(request)).toJSONString()))
                .build();

        HttpResponse<String> response;
        try
        {
            response = client().send(httpRequest, HttpResponse.BodyHandlers.ofString());
        }
        catch (HttpTimeoutException e)
        {
            log.warn("模型工具请求超时, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求超时，请稍后重试", e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求被中断，请稍后重试", e);
        }
        catch (Exception e)
        {
            log.warn("模型工具请求异常: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务连接失败，请稍后重试", e);
        }

        if (response.statusCode() == 401)
        {
            log.warn("模型服务鉴权失败, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.HTTP, 401, "模型服务鉴权失败，请检查 API Key");
        }
        if (response.statusCode() != 200)
        {
            log.warn("模型服务返回异常状态, status={}", response.statusCode());
            throw new AiClientException(AiClientException.Type.HTTP, response.statusCode(),
                    "模型服务暂时不可用，请稍后重试");
        }
        return parseChatResponse(response.body(), messages);
    }

    @Override
    public String streamChat(List<ChatMessage> messages, Consumer<String> onDelta, BooleanSupplier cancelled)
    {
        if (!properties.isConfigured())
        {
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务未配置，请设置 LAWYER_AI_API_KEY 等配置");
        }

        Duration timeout = Duration.ofMillis(properties.getStreamTimeout());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(buildBody(messages, true).toJSONString()))
                .build();

        HttpResponse<Stream<String>> response;
        try
        {
            response = client().send(request, HttpResponse.BodyHandlers.ofLines());
        }
        catch (HttpTimeoutException e)
        {
            log.warn("模型流式请求超时, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型响应超时，请稍后重试", e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求被中断，请稍后重试", e);
        }
        catch (Exception e)
        {
            log.warn("模型流式请求异常: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务连接失败，请稍后重试", e);
        }

        if (response.statusCode() != 200)
        {
            log.warn("模型流式服务返回异常状态, status={}", response.statusCode());
            closeQuietly(response.body());
            throw new AiClientException(AiClientException.Type.HTTP, response.statusCode(),
                    "模型服务暂时不可用，请稍后重试");
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        StringBuilder fullText = new StringBuilder();
        try (Stream<String> lines = response.body())
        {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext())
            {
                if (cancelled != null && cancelled.getAsBoolean())
                {
                    break;
                }
                if (System.nanoTime() > deadline)
                {
                    throw new AiClientException(AiClientException.Type.TIMEOUT, "模型响应超时，请稍后重试");
                }
                String line = iterator.next();
                if (streamParser.isDone(line))
                {
                    break;
                }
                String delta = streamParser.parseLine(line);
                if (delta != null && !delta.isEmpty())
                {
                    onDelta.accept(delta);
                    fullText.append(delta);
                }
            }
        }
        catch (AiClientException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            // 包括解析错误与服务端 SseEmitter 写出异常，交由上层分类处理
            throw e;
        }
        return fullText.toString();
    }

    @Override
    public ChatResponse streamChatWithTools(ChatRequest request, StreamListener listener, BooleanSupplier cancelled)
    {
        if (!properties.isConfigured())
        {
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务未配置，请设置 LAWYER_AI_API_KEY 等配置");
        }
        List<ChatMessage> messages = request == null || request.getMessages() == null
                ? new ArrayList<>() : request.getMessages();
        Duration timeout = request == null || request.getTimeout() == null
                ? Duration.ofMillis(properties.getStreamTimeout()) : request.getTimeout();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(
                        buildBody(messages, true, tools(request), toolChoice(request)).toJSONString()))
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<Stream<String>> response;
        try
        {
            response = client().send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        }
        catch (HttpTimeoutException e)
        {
            log.warn("模型流式工具请求超时, model={}", properties.getModel());
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型响应超时，请稍后重试", e);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AiClientException(AiClientException.Type.TIMEOUT, "模型请求被中断，请稍后重试", e);
        }
        catch (Exception e)
        {
            log.warn("模型流式工具请求异常: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.HTTP, "模型服务连接失败，请稍后重试", e);
        }

        if (response.statusCode() != 200)
        {
            log.warn("模型流式服务返回异常状态, status={}", response.statusCode());
            closeQuietly(response.body());
            throw new AiClientException(AiClientException.Type.HTTP, response.statusCode(),
                    "模型服务暂时不可用，请稍后重试");
        }

        long deadline = System.nanoTime() + timeout.toNanos();
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();
        String finishReason = null;
        TokenUsage usage = null;
        Long ttftMs = null;

        try (Stream<String> lines = response.body())
        {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext())
            {
                if (cancelled != null && cancelled.getAsBoolean())
                {
                    break;
                }
                if (System.nanoTime() > deadline)
                {
                    throw new AiClientException(AiClientException.Type.TIMEOUT, "模型响应超时，请稍后重试");
                }
                String line = iterator.next();
                if (streamParser.isDone(line))
                {
                    break;
                }
                OpenAiStreamParser.StreamChunk chunk = streamParser.parseChunk(line);
                if (chunk == null)
                {
                    continue;
                }
                String contentDelta = chunk.getContentDelta();
                if (contentDelta != null && !contentDelta.isEmpty())
                {
                    if (ttftMs == null)
                    {
                        ttftMs = Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
                    }
                    content.append(contentDelta);
                    if (listener != null)
                    {
                        listener.onContentDelta(contentDelta);
                    }
                }
                String reasoningDelta = chunk.getReasoningDelta();
                if (reasoningDelta != null && !reasoningDelta.isEmpty())
                {
                    reasoning.append(reasoningDelta);
                    if (listener != null)
                    {
                        listener.onReasoningDelta(reasoningDelta);
                    }
                }
                for (OpenAiStreamParser.ToolCallDelta delta : chunk.getToolCallDeltas())
                {
                    ToolCallAccumulator accumulator = toolCalls.computeIfAbsent(delta.getIndex(),
                            key -> new ToolCallAccumulator());
                    accumulator.append(delta);
                }
                if (chunk.getFinishReason() != null)
                {
                    finishReason = chunk.getFinishReason();
                }
                if (chunk.getUsage() != null)
                {
                    usage = chunk.getUsage();
                }
            }
        }
        catch (AiClientException e)
        {
            throw e;
        }
        catch (RuntimeException e)
        {
            throw e;
        }

        List<ToolCall> calls = new ArrayList<>();
        for (ToolCallAccumulator accumulator : toolCalls.values())
        {
            ToolCall call = accumulator.build();
            if (call != null)
            {
                calls.add(call);
            }
        }
        String outputText = content.toString();
        if (usage == null)
        {
            usage = TokenEstimator.estimate(messages, outputText);
        }
        return new ChatResponse(outputText, reasoning.toString(), calls, finishReason, usage, ttftMs);
    }

    /**
     * 组装请求体
     */
    private JSONObject buildBody(List<ChatMessage> messages, boolean stream)
    {
        return buildBody(messages, stream, null, null);
    }

    /**
     * 组装请求体，支持工具定义与流式用量统计
     */
    private JSONObject buildBody(List<ChatMessage> messages, boolean stream, List<ToolDefinition> tools,
            String toolChoice)
    {
        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : messages)
        {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty())
            {
                JSONArray toolCalls = new JSONArray();
                for (ToolCall call : message.getToolCalls())
                {
                    JSONObject callItem = new JSONObject();
                    callItem.put("id", call.getId());
                    callItem.put("type", "function");
                    JSONObject function = new JSONObject();
                    function.put("name", call.getName());
                    function.put("arguments", call.getArguments());
                    callItem.put("function", function);
                    toolCalls.add(callItem);
                }
                item.put("tool_calls", toolCalls);
            }
            if (message.getToolCallId() != null)
            {
                item.put("tool_call_id", message.getToolCallId());
            }
            if (message.getName() != null)
            {
                item.put("name", message.getName());
            }
            messageArray.add(item);
        }
        JSONObject body = new JSONObject();
        body.put("model", properties.getModel());
        body.put("messages", messageArray);
        body.put("stream", stream);
        if (stream)
        {
            JSONObject streamOptions = new JSONObject();
            streamOptions.put("include_usage", true);
            body.put("stream_options", streamOptions);
        }
        if (tools != null && !tools.isEmpty())
        {
            JSONArray toolArray = new JSONArray();
            for (ToolDefinition tool : tools)
            {
                JSONObject item = new JSONObject();
                item.put("type", "function");
                JSONObject function = new JSONObject();
                function.put("name", tool.getName());
                function.put("description", tool.getDescription());
                function.put("parameters", tool.getParameters());
                item.put("function", function);
                toolArray.add(item);
            }
            body.put("tools", toolArray);
            body.put("tool_choice", toolChoice == null || toolChoice.isBlank() ? "auto" : toolChoice);
        }
        return body;
    }

    private List<ToolDefinition> tools(ChatRequest request)
    {
        return request == null ? null : request.getTools();
    }

    private String toolChoice(ChatRequest request)
    {
        return request == null ? null : request.getToolChoice();
    }

    private void closeQuietly(Stream<String> stream)
    {
        if (stream == null)
        {
            return;
        }
        try
        {
            stream.close();
        }
        catch (Exception ignored)
        {
            // 忽略关闭异常
        }
    }

    /**
     * 解析 choices[0].message.content，结构不符合时抛出明确异常
     */
    private String parseContent(String responseBody)
    {
        try
        {
            JSONObject root = JSONObject.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                throw new AiClientException(AiClientException.Type.PARSE, "模型返回内容为空");
            }
            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
            if (content == null || content.isBlank())
            {
                throw new AiClientException(AiClientException.Type.PARSE, "模型返回内容为空");
            }
            return content;
        }
        catch (AiClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("模型响应结构解析失败: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.PARSE, "模型返回格式异常，请稍后重试", e);
        }
    }

    /**
     * 解析带工具调用与用量的非流式响应；message.content 允许为空，但 content 与 tool_calls 不得同时为空
     */
    private ChatResponse parseChatResponse(String responseBody, List<ChatMessage> messages)
    {
        try
        {
            JSONObject root = JSONObject.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                throw new AiClientException(AiClientException.Type.PARSE, "模型返回内容为空");
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice == null ? null : choice.getJSONObject("message");
            if (message == null)
            {
                throw new AiClientException(AiClientException.Type.PARSE, "模型返回内容为空");
            }
            String content = message.getString("content");
            String reasoning = message.getString("reasoning_content");
            List<ToolCall> toolCalls = new ArrayList<>();
            JSONArray callArray = message.getJSONArray("tool_calls");
            if (callArray != null)
            {
                for (int i = 0; i < callArray.size(); i++)
                {
                    JSONObject call = callArray.getJSONObject(i);
                    if (call == null)
                    {
                        continue;
                    }
                    JSONObject function = call.getJSONObject("function");
                    if (function == null)
                    {
                        continue;
                    }
                    toolCalls.add(new ToolCall(call.getString("id"), function.getString("name"),
                            function.getString("arguments")));
                }
            }
            if ((content == null || content.isBlank()) && toolCalls.isEmpty())
            {
                throw new AiClientException(AiClientException.Type.PARSE, "模型返回内容为空");
            }
            String finishReason = choice.getString("finish_reason");
            TokenUsage usage = parseUsage(root.getJSONObject("usage"));
            if (usage == null)
            {
                usage = TokenEstimator.estimate(messages, content);
            }
            return new ChatResponse(content == null ? "" : content, reasoning, toolCalls, finishReason, usage, null);
        }
        catch (AiClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("模型响应结构解析失败: {}", e.getMessage());
            throw new AiClientException(AiClientException.Type.PARSE, "模型返回格式异常，请稍后重试", e);
        }
    }

    private TokenUsage parseUsage(JSONObject usage)
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
        return new TokenUsage(promptValue, completionValue, totalValue, false);
    }

    /**
     * 单个工具调用跨分片聚合器
     */
    private static final class ToolCallAccumulator
    {
        private String id;

        private final StringBuilder name = new StringBuilder();

        private final StringBuilder arguments = new StringBuilder();

        private void append(OpenAiStreamParser.ToolCallDelta delta)
        {
            if (delta.getId() != null && !delta.getId().isEmpty())
            {
                if (id == null)
                {
                    id = delta.getId();
                }
                else if (!id.endsWith(delta.getId()))
                {
                    id = id + delta.getId();
                }
            }
            if (delta.getName() != null)
            {
                name.append(delta.getName());
            }
            if (delta.getArgumentsFragment() != null)
            {
                arguments.append(delta.getArgumentsFragment());
            }
        }

        private ToolCall build()
        {
            String nameValue = name.toString();
            String argumentsValue = arguments.toString();
            if (nameValue.isEmpty() && argumentsValue.isEmpty())
            {
                return null;
            }
            return new ToolCall(id, nameValue, argumentsValue);
        }
    }
}
