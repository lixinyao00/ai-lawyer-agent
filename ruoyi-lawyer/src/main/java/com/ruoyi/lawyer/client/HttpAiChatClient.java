package com.ruoyi.lawyer.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
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

    /**
     * 组装请求体
     */
    private JSONObject buildBody(List<ChatMessage> messages, boolean stream)
    {
        JSONArray messageArray = new JSONArray();
        for (ChatMessage message : messages)
        {
            JSONObject item = new JSONObject();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messageArray.add(item);
        }
        JSONObject body = new JSONObject();
        body.put("model", properties.getModel());
        body.put("messages", messageArray);
        body.put("stream", stream);
        return body;
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
}
