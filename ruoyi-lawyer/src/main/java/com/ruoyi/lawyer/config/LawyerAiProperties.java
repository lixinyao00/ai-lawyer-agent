package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 律师 AI 模型配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.ai")
public class LawyerAiProperties
{
    /** 模型服务基础地址，例如 https://api.deepseek.com */
    private String baseUrl;

    /** 访问密钥，通过环境变量 LAWYER_AI_API_KEY 注入 */
    private String apiKey;

    /** 模型名，例如 deepseek-chat */
    private String model;

    /** 建立连接的超时（毫秒） */
    private Long connectTimeout = 5000L;

    /** 一次模型请求的总超时（毫秒） */
    private Long requestTimeout = 60000L;

    /** 流式问答总时长上限（毫秒），必须小于会话锁 TTL */
    private Long streamTimeout = 100000L;

    /** 合同审查 / 文书生成等长任务超时（毫秒） */
    private Long longRequestTimeout = 120000L;

    /** 向量嵌入独立配置，默认初始化避免单测 NPE */
    private Embedding embedding = new Embedding();

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public Long getConnectTimeout()
    {
        return connectTimeout;
    }

    public void setConnectTimeout(Long connectTimeout)
    {
        this.connectTimeout = connectTimeout;
    }

    public Long getRequestTimeout()
    {
        return requestTimeout;
    }

    public void setRequestTimeout(Long requestTimeout)
    {
        this.requestTimeout = requestTimeout;
    }

    public Long getStreamTimeout()
    {
        return streamTimeout;
    }

    public void setStreamTimeout(Long streamTimeout)
    {
        this.streamTimeout = streamTimeout;
    }

    public Long getLongRequestTimeout()
    {
        return longRequestTimeout;
    }

    public void setLongRequestTimeout(Long longRequestTimeout)
    {
        this.longRequestTimeout = longRequestTimeout;
    }

    /**
     * 核心配置是否齐全，缺少时不允许调用模型
     */
    public boolean isConfigured()
    {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }

    public Embedding getEmbedding()
    {
        return embedding;
    }

    public void setEmbedding(Embedding embedding)
    {
        this.embedding = embedding == null ? new Embedding() : embedding;
    }

    /**
     * 解析实际使用的 Embedding 网关地址：独立配置为空时回退复用 chat base-url
     *
     * @return 已去除尾部斜杠的地址，可能为空串
     */
    public String resolveEmbeddingBaseUrl()
    {
        String value = embedding == null ? null : embedding.getBaseUrl();
        if (value == null || value.isBlank())
        {
            value = baseUrl;
        }
        if (value == null)
        {
            return "";
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/"))
        {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * 解析实际使用的 Embedding 密钥：独立配置为空时回退复用 chat api-key
     *
     * @return 密钥，可能为空串
     */
    public String resolveEmbeddingApiKey()
    {
        String value = embedding == null ? null : embedding.getApiKey();
        if (value == null || value.isBlank())
        {
            value = apiKey;
        }
        return value == null ? "" : value.trim();
    }

    /**
     * 向量嵌入配置
     */
    public static class Embedding
    {
        /** 是否启用向量嵌入（关闭或远端失败时自动降级本地编码） */
        private boolean enabled = true;

        /** 独立 Embedding 网关地址；留空回退 lawyer.ai.base-url */
        private String baseUrl;

        /** 独立 Embedding 密钥；留空回退 lawyer.ai.api-key */
        private String apiKey;

        /** Embedding 模型名 */
        private String model = "text-embedding-3-small";

        /** 向量维度（同时作为本地降级向量维度） */
        private int dimensions = 256;

        /** 单次 Embedding 请求超时（毫秒） */
        private long timeout = 15000L;

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public String getBaseUrl()
        {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl)
        {
            this.baseUrl = baseUrl;
        }

        public String getApiKey()
        {
            return apiKey;
        }

        public void setApiKey(String apiKey)
        {
            this.apiKey = apiKey;
        }

        public String getModel()
        {
            return model;
        }

        public void setModel(String model)
        {
            this.model = model;
        }

        public int getDimensions()
        {
            return dimensions;
        }

        public void setDimensions(int dimensions)
        {
            this.dimensions = dimensions;
        }

        public long getTimeout()
        {
            return timeout;
        }

        public void setTimeout(long timeout)
        {
            this.timeout = timeout;
        }
    }
}
