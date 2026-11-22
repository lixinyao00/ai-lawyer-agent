package com.ruoyi.lawyer.retrieval;

import java.util.List;

/**
 * 向量检索引擎抽象
 *
 * <p>上层 {@code KnowledgeRetriever} 仅依赖本接口，与具体实现零耦合。默认提供纯内存余弦实现
 * {@link InMemoryVectorSearchEngine}。未来平滑切换外部向量库时，只需新增配置
 * {@code lawyer.knowledge.vector-engine: memory | milvus | pgvector} 并提供对应实现：</p>
 * <ul>
 *   <li>{@code MilvusVectorSearchEngine}：{@link #rebuild(List)} 映射为 Collection upsert + flush，
 *       {@link #search(float[], int)} 映射为 HNSW COSINE metric Top-K；</li>
 *   <li>{@code PgVectorSearchEngine}：{@link #search(float[], int)} 映射为
 *       {@code ORDER BY embedding <=> :vec::vector LIMIT :topK}。</li>
 * </ul>
 * <p>{@code KnowledgeRetriever} 与全部上层 Service 无需修改一行代码。</p>
 *
 * @author ruoyi
 */
public interface VectorSearchEngine
{
    /**
     * 索引文档
     *
     * @param chunkId 知识库切片ID
     * @param docId 法规文档ID
     * @param category 法规分类
     * @param vector 已 L2 归一化向量
     */
    record VectorDocument(Long chunkId, Long docId, String category, float[] vector)
    {
    }

    /**
     * 检索命中
     *
     * @param chunkId 知识库切片ID
     * @param score 余弦相似度
     */
    record VectorHit(Long chunkId, double score)
    {
    }

    /**
     * 重建全量向量索引（与 KnowledgeRetriever 快照构建同步）
     *
     * @param documents 文档集合
     */
    void rebuild(List<VectorDocument> documents);

    /**
     * 余弦相似度 Top-K 检索（queryVector 与库向量均已 L2 归一化，点积即余弦）
     *
     * @param queryVector 查询向量
     * @param topK 返回条数
     * @return 按相似度倒序的命中集合
     */
    List<VectorHit> search(float[] queryVector, int topK);

    /**
     * 带分类过滤的 Top-K 检索
     *
     * <p>默认实现不做分类过滤，支持分类的引擎应覆写本方法。</p>
     *
     * @param queryVector 查询向量
     * @param topK 返回条数
     * @param category 法规分类，空值表示不过滤
     * @return 命中集合
     */
    default List<VectorHit> search(float[] queryVector, int topK, String category)
    {
        return search(queryVector, topK);
    }

    /**
     * 索引是否就绪（已加载有效向量数 > 0）
     *
     * @return 是否就绪
     */
    boolean isReady();

    /**
     * 已索引向量条数
     *
     * @return 条数
     */
    int size();

    /**
     * 向量维度（未就绪时为 0）
     *
     * @return 维度
     */
    int dimensions();

    /**
     * 引擎名称，如 in-memory-cosine / milvus / pgvector
     *
     * @return 引擎名称
     */
    String engineName();
}
