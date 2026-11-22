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

    /**
     * 核心配置是否齐全，缺少时不允许调用模型
     */
    public boolean isConfigured()
    {
        return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && model != null && !model.isBlank();
    }
}
