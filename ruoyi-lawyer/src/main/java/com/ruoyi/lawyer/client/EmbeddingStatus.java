package com.ruoyi.lawyer.client;

/**
 * 向量嵌入客户端运行状态快照
 *
 * <p>用于调试面板、检索调试接口与离线评测报告，如实标记当前生效模式与远端网关状态。</p>
 *
 * @author ruoyi
 */
public class EmbeddingStatus
{
    /** 生效模式：http / local_fallback / disabled */
    private String mode;

    /** 当前生效模型标识（远端模型名或 local-legal-hash-{dim}） */
    private String model;

    /** 向量维度 */
    private int dimensions;

    /** 是否配置了远端网关地址与密钥 */
    private boolean remoteConfigured;

    /** 远端网关当前是否可用 */
    private boolean remoteAvailable;

    /** 最近一次错误码，如 HTTP_403:embeddings_disabled；无错误为 null */
    private String lastErrorCode;

    /** 最近一次错误说明；无错误为 null */
    private String lastErrorMessage;

    public EmbeddingStatus()
    {
    }

    public EmbeddingStatus(String mode, String model, int dimensions, boolean remoteConfigured,
            boolean remoteAvailable, String lastErrorCode, String lastErrorMessage)
    {
        this.mode = mode;
        this.model = model;
        this.dimensions = dimensions;
        this.remoteConfigured = remoteConfigured;
        this.remoteAvailable = remoteAvailable;
        this.lastErrorCode = lastErrorCode;
        this.lastErrorMessage = lastErrorMessage;
    }

    /**
     * 完全禁用（不返回任何向量，检索退化为纯 BM25）
     */
    public static EmbeddingStatus disabled(int dimensions)
    {
        return new EmbeddingStatus("disabled", null, dimensions, false, false, null, null);
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
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

    public boolean isRemoteConfigured()
    {
        return remoteConfigured;
    }

    public void setRemoteConfigured(boolean remoteConfigured)
    {
        this.remoteConfigured = remoteConfigured;
    }

    public boolean isRemoteAvailable()
    {
        return remoteAvailable;
    }

    public void setRemoteAvailable(boolean remoteAvailable)
    {
        this.remoteAvailable = remoteAvailable;
    }

    public String getLastErrorCode()
    {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode)
    {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorMessage()
    {
        return lastErrorMessage;
    }

    public void setLastErrorMessage(String lastErrorMessage)
    {
        this.lastErrorMessage = lastErrorMessage;
    }
}
