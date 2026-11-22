package com.ruoyi.lawyer.client;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.lawyer.config.LawyerAiProperties;
import com.ruoyi.lawyer.retrieval.LocalSemanticEmbeddingEncoder;

/**
 * OpenAI 兼容协议的向量嵌入客户端
 *
 * <p>优先请求 {@code POST {embeddingBaseUrl}/embeddings}；当未启用、未配置、上游返回
 * 403 embeddings_disabled / 4xx / 5xx / 超时或网络异常时，如实记录错误码并无缝降级到
 * {@link LocalSemanticEmbeddingEncoder} 本地确定性语义向量编码器，绝不阻断入库与在线检索。</p>
 *
 * @author ruoyi
 */
@Component
public class HttpAiEmbeddingClient implements AiEmbeddingClient
{
    private static final Logger log = LoggerFactory.getLogger(HttpAiEmbeddingClient.class);

    /** 单次 HTTP 请求最多文本条数，避免大法规导入超网关 payload 限制 */
    private static final int MAX_BATCH_SIZE = 32;

    /** 403 / 404 等确定性失败后的冷却时间（毫秒），避免每次查询重复碰壁 */
    private static final long COOLDOWN_MILLIS = 5 * 60 * 1000L;

    /** 未配置远端网关时的错误码 */
    private static final String ERROR_NOT_CONFIGURED = "REMOTE_NOT_CONFIGURED";

    @Autowired
    private LawyerAiProperties properties;

    private volatile HttpClient httpClient;

    /** 冷却截止时间戳（毫秒），0 表示未冷却 */
    private volatile long cooldownUntil;

    private volatile String lastErrorCode;

    private volatile String lastErrorMessage;

    private volatile boolean remoteAvailable;

    private volatile String currentMode = "http";

    private volatile LocalSemanticEmbeddingEncoder localEncoder;

    @Override
    public float[] embed(String text)
    {
        if (text == null || text.isBlank())
        {
            return null;
        }
        List<float[]> vectors = embedBatch(List.of(text));
        return vectors.isEmpty() ? null : vectors.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts)
    {
        List<float[]> result = new ArrayList<>();
        if (texts == null || texts.isEmpty())
        {
            return result;
        }
        // 完全禁用：不返回向量，检索自动退化为纯 BM25
        if (!properties.getEmbedding().isEnabled())
        {
            currentMode = "disabled";
            for (int i = 0; i < texts.size(); i++)
            {
                result.add(null);
            }
            return result;
        }

        int dimensions = resolveDimensions();
        boolean remoteConfigured = isRemoteConfigured();
        if (!remoteConfigured)
        {
            lastErrorCode = ERROR_NOT_CONFIGURED;
            lastErrorMessage = "Embedding 网关地址或密钥未配置";
            currentMode = "local_fallback";
            for (String text : texts)
            {
                result.add(localEncoder(dimensions).encode(text));
            }
            return result;
        }

        boolean fallback = false;
        for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE)
        {
            List<String> chunk = texts.subList(start, Math.min(start + MAX_BATCH_SIZE, texts.size()));
            float[][] vectors = tryRemote(chunk, dimensions);
            if (vectors == null)
            {
                fallback = true;
                for (String text : chunk)
                {
                    result.add(localEncoder(dimensions).encode(text));
                }
            }
            else
            {
                for (float[] vector : vectors)
                {
                    result.add(vector);
                }
            }
        }
        if (fallback)
        {
            currentMode = "local_fallback";
            remoteAvailable = false;
        }
        else
        {
            currentMode = "http";
            remoteAvailable = true;
            lastErrorCode = null;
            lastErrorMessage = null;
        }
        return result;
    }

    @Override
    public String currentModel()
    {
        if ("local_fallback".equals(currentMode))
        {
            return localEncoder(resolveDimensions()).modelName();
        }
        return properties.getEmbedding().getModel();
    }

    @Override
    public EmbeddingStatus getStatus()
    {
        int dimensions = resolveDimensions();
        String mode = currentMode;
        String model = switch (mode)
        {
            case "local_fallback" -> localEncoder(dimensions).modelName();
            case "disabled" -> null;
            default -> properties.getEmbedding().getModel();
        };
        return new EmbeddingStatus(mode, model, dimensions, isRemoteConfigured(), remoteAvailable,
                lastErrorCode, lastErrorMessage);
    }

    /**
     * 清除 403/404 冷却并重置错误状态，供 rebuild-embeddings 主动重新探测远端网关
     */
    public void resetCooldown()
    {
        cooldownUntil = 0L;
        lastErrorCode = null;
        lastErrorMessage = null;
        log.info("Embedding 远端网关冷却已清除，下次调用将重新探测");
    }

    /**
     * 当前是否处于冷却期（调试与单测可读）
     *
     * @return true 表示冷却中，直接走本地降级
     */
    public boolean isCoolingDown()
    {
        return System.currentTimeMillis() < cooldownUntil;
    }

    /**
     * 调用远端 Embedding；失败返回 null 并记录 lastErrorCode
     */
    private float[][] tryRemote(List<String> texts, int dimensions)
    {
        if (isCoolingDown())
        {
            return null;
        }
        try
        {
            JSONObject payload = buildPayload(texts, dimensions);
            JSONObject response = post(payload);
            if (response != null)
            {
                return parseResponse(response, texts.size());
            }
            // 400 且可能不支持 dimensions：去掉 dimensions 重试一次
            if (lastErrorCode != null && lastErrorCode.startsWith("HTTP_400"))
            {
                JSONObject retryPayload = buildPayload(texts, 0);
                JSONObject retryResponse = post(retryPayload);
                if (retryResponse != null)
                {
                    return parseResponse(retryResponse, texts.size());
                }
            }
            return null;
        }
        catch (HttpTimeoutException e)
        {
            recordFailure("TIMEOUT", "Embedding 请求超时: " + e.getMessage(), false);
            return null;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            recordFailure("INTERRUPTED", "Embedding 请求被中断", false);
            return null;
        }
        catch (ConnectException e)
        {
            recordFailure("CONNECT_ERROR", "Embedding 网关连接失败: " + e.getMessage(), false);
            return null;
        }
        catch (Exception e)
        {
            recordFailure("CLIENT_ERROR", "Embedding 调用异常: " + e.getMessage(), false);
            return null;
        }
    }

    private JSONObject buildPayload(List<String> texts, int dimensions)
    {
        JSONObject payload = new JSONObject();
        payload.put("model", properties.getEmbedding().getModel());
        payload.put("input", texts);
        if (dimensions > 0)
        {
            payload.put("dimensions", dimensions);
        }
        return payload;
    }

    /**
     * 发起一次 HTTP 请求；成功返回响应 JSON，失败返回 null 并写入错误码
     */
    private JSONObject post(JSONObject payload) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.resolveEmbeddingBaseUrl() + "/embeddings"))
                .timeout(Duration.ofMillis(resolveTimeout()))
                .header("Authorization", "Bearer " + properties.resolveEmbeddingApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        String body = response.body();
        if (status >= 200 && status < 300)
        {
            JSONObject json = JSONObject.parseObject(body);
            if (json == null)
            {
                recordFailure("PARSE_ERROR", "Embedding 响应不是合法 JSON", false);
                return null;
            }
            return json;
        }

        String code = "HTTP_" + status;
        String errorBody = body == null ? "" : body;
        if (status == 403 && errorBody.contains("embeddings_disabled"))
        {
            code = "HTTP_403:embeddings_disabled";
        }
        // 403 / 404 / 401 属于确定性拒绝，冷却 5 分钟避免重复碰壁
        boolean cooldown = status == 403 || status == 404 || status == 401;
        recordFailure(code, truncate(errorBody), cooldown);
        return null;
    }

    private float[][] parseResponse(JSONObject json, int expectedSize)
    {
        JSONArray data = json.getJSONArray("data");
        if (data == null || data.isEmpty())
        {
            recordFailure("EMPTY_RESPONSE", "Embedding 响应缺少 data 数组", false);
            return null;
        }
        List<IndexedVector> vectors = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++)
        {
            JSONObject item = data.getJSONObject(i);
            if (item == null)
            {
                continue;
            }
            JSONArray embedding = item.getJSONArray("embedding");
            if (embedding == null || embedding.isEmpty())
            {
                continue;
            }
            float[] vector = new float[embedding.size()];
            for (int j = 0; j < embedding.size(); j++)
            {
                vector[j] = embedding.getFloatValue(j);
            }
            normalize(vector);
            int index = item.getIntValue("index", vectors.size());
            vectors.add(new IndexedVector(index, vector));
        }
        if (vectors.isEmpty())
        {
            recordFailure("EMPTY_RESPONSE", "Embedding 响应无有效向量", false);
            return null;
        }
        // 按 data[].index 排序，保证与输入顺序一致
        vectors.sort(Comparator.comparingInt(item -> item.index));
        float[][] result = new float[Math.min(vectors.size(), expectedSize)][];
        for (int i = 0; i < result.length; i++)
        {
            result[i] = vectors.get(i).vector;
        }
        return result;
    }

    private void recordFailure(String code, String message, boolean cooldown)
    {
        lastErrorCode = code;
        lastErrorMessage = truncate(message);
        currentMode = "local_fallback";
        remoteAvailable = false;
        if (cooldown)
        {
            cooldownUntil = System.currentTimeMillis() + COOLDOWN_MILLIS;
        }
        log.warn("Embedding 远端调用失败，自动降级本地语义编码, code={}, message={}", code, lastErrorMessage);
    }

    private boolean isRemoteConfigured()
    {
        return !properties.resolveEmbeddingBaseUrl().isBlank()
                && !properties.resolveEmbeddingApiKey().isBlank()
                && properties.getEmbedding().getModel() != null
                && !properties.getEmbedding().getModel().isBlank();
    }

    private int resolveDimensions()
    {
        int dimensions = properties.getEmbedding().getDimensions();
        return dimensions > 0 ? dimensions : LocalSemanticEmbeddingEncoder.DEFAULT_DIMENSIONS;
    }

    private long resolveTimeout()
    {
        long timeout = properties.getEmbedding().getTimeout();
        return timeout > 0 ? timeout : 15000L;
    }

    private LocalSemanticEmbeddingEncoder localEncoder(int dimensions)
    {
        LocalSemanticEmbeddingEncoder encoder = localEncoder;
        if (encoder == null || encoder.getDimensions() != dimensions)
        {
            synchronized (this)
            {
                encoder = localEncoder;
                if (encoder == null || encoder.getDimensions() != dimensions)
                {
                    encoder = new LocalSemanticEmbeddingEncoder(dimensions);
                    localEncoder = encoder;
                }
            }
        }
        return encoder;
    }

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
                    long connectTimeout = properties.getConnectTimeout() == null ? 5000L
                            : properties.getConnectTimeout();
                    local = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(connectTimeout))
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private void normalize(float[] vector)
    {
        double sum = 0.0;
        for (float value : vector)
        {
            sum += (double) value * value;
        }
        if (sum <= 0.0)
        {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++)
        {
            vector[i] = (float) (vector[i] / norm);
        }
    }

    private String truncate(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    private static final class IndexedVector
    {
        private final int index;

        private final float[] vector;

        private IndexedVector(int index, float[] vector)
        {
            this.index = index;
            this.vector = vector;
        }
    }
}
