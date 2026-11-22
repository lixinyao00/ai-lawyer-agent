package com.ruoyi.lawyer.retrieval;

/**
 * 多轮指代 Query 改写结果
 *
 * @author ruoyi
 */
public class QueryRewriteResult
{
    /** 原始问题 */
    private String originalQuery;

    /** 改写后用于检索的查询句 */
    private String rewrittenQuery;

    /** 是否发生实质改写 */
    private boolean rewritten;

    /** 改写策略：passthrough / llm / rule_fallback */
    private String strategy;

    /** 改写耗时（毫秒） */
    private long latencyMs;

    public QueryRewriteResult()
    {
    }

    public QueryRewriteResult(String originalQuery, String rewrittenQuery, boolean rewritten, String strategy,
            long latencyMs)
    {
        this.originalQuery = originalQuery;
        this.rewrittenQuery = rewrittenQuery;
        this.rewritten = rewritten;
        this.strategy = strategy;
        this.latencyMs = latencyMs;
    }

    /**
     * 原样透传
     *
     * @param query 原始问题
     * @param latencyMs 耗时
     * @return 结果
     */
    public static QueryRewriteResult passthrough(String query, long latencyMs)
    {
        return new QueryRewriteResult(query, query, false, "passthrough", latencyMs);
    }

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

    public boolean isRewritten()
    {
        return rewritten;
    }

    public void setRewritten(boolean rewritten)
    {
        this.rewritten = rewritten;
    }

    public String getStrategy()
    {
        return strategy;
    }

    public void setStrategy(String strategy)
    {
        this.strategy = strategy;
    }

    public long getLatencyMs()
    {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs)
    {
        this.latencyMs = latencyMs;
    }
}
