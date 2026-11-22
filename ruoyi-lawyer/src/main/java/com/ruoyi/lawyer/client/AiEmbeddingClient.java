package com.ruoyi.lawyer.client;

import java.util.List;

/**
 * 文本向量嵌入客户端
 *
 * <p>实现必须保证返回 L2 归一化向量；仅在完全禁用且无降级能力时允许返回 null。</p>
 *
 * @author ruoyi
 */
public interface AiEmbeddingClient
{
    /**
     * 单条文本向量化
     *
     * @param text 文本
     * @return L2 归一化向量；完全禁用时返回 null
     */
    float[] embed(String text);

    /**
     * 批量文本向量化，按输入顺序返回等长列表
     *
     * @param texts 文本集合
     * @return 向量集合（与输入等长，禁用时元素为 null）
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 当前生效模型标识（远端模型名或 local-legal-hash-{dim}）
     *
     * @return 模型标识
     */
    String currentModel();

    /**
     * 当前客户端运行状态快照
     *
     * @return 状态
     */
    EmbeddingStatus getStatus();
}
