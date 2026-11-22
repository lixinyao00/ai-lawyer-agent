package com.ruoyi.lawyer.vo;

import java.util.ArrayList;
import java.util.List;
import com.ruoyi.lawyer.client.EmbeddingStatus;

/**
 * 知识库检索调试视图：一键三路对比 + 改写词透出
 *
 * @author ruoyi
 */
public class KnowledgeSearchDebugVO
{
    /** 原始问题 */
    private String originalQuery;

    /** 多轮指代改写后的检索词 */
    private String rewrittenQuery;

    /** 是否发生实质改写 */
    private Boolean rewritten;

    /** 改写策略：passthrough / llm / rule_fallback */
    private String rewriteStrategy;

    /** 改写耗时（毫秒） */
    private Long rewriteLatencyMs;

    /** 本次实际使用的检索模式 */
    private String mode;

    /** Embedding 客户端状态 */
    private EmbeddingStatus embeddingStatus;

    /** 按请求 mode 选定的主结果 */
    private List<CitationVO> citations = new ArrayList<>();

    /** 纯 BM25 Top-K */
    private List<CitationVO> bm25Results = new ArrayList<>();

    /** 纯 Vector Top-K */
    private List<CitationVO> vectorResults = new ArrayList<>();

    /** BM25 + Vector RRF Top-K */
    private List<CitationVO> hybridResults = new ArrayList<>();

    public String getOriginalQuery()
    {
        return originalQuery;
    }

    public void setOriginalQuery(String originalQuery)
    {
        this.originalQuery = originalQuery;
    }

    public String getRewrittenQuery()
    {
        return rewrittenQuery;
    }

    public void setRewrittenQuery(String rewrittenQuery)
    {
        this.rewrittenQuery = rewrittenQuery;
    }

    public Boolean getRewritten()
    {
        return rewritten;
    }

    public void setRewritten(Boolean rewritten)
    {
        this.rewritten = rewritten;
    }

    public String getRewriteStrategy()
    {
        return rewriteStrategy;
    }

    public void setRewriteStrategy(String rewriteStrategy)
    {
        this.rewriteStrategy = rewriteStrategy;
    }

    public Long getRewriteLatencyMs()
    {
        return rewriteLatencyMs;
    }

    public void setRewriteLatencyMs(Long rewriteLatencyMs)
    {
        this.rewriteLatencyMs = rewriteLatencyMs;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public EmbeddingStatus getEmbeddingStatus()
    {
        return embeddingStatus;
    }

    public void setEmbeddingStatus(EmbeddingStatus embeddingStatus)
    {
        this.embeddingStatus = embeddingStatus;
    }

    public List<CitationVO> getCitations()
    {
        return citations;
    }

    public void setCitations(List<CitationVO> citations)
    {
        this.citations = citations == null ? new ArrayList<>() : citations;
    }

    public List<CitationVO> getBm25Results()
    {
        return bm25Results;
    }

    public void setBm25Results(List<CitationVO> bm25Results)
    {
        this.bm25Results = bm25Results == null ? new ArrayList<>() : bm25Results;
    }

    public List<CitationVO> getVectorResults()
    {
        return vectorResults;
    }

    public void setVectorResults(List<CitationVO> vectorResults)
    {
        this.vectorResults = vectorResults == null ? new ArrayList<>() : vectorResults;
    }

    public List<CitationVO> getHybridResults()
    {
        return hybridResults;
    }

    public void setHybridResults(List<CitationVO> hybridResults)
    {
        this.hybridResults = hybridResults == null ? new ArrayList<>() : hybridResults;
    }
}
