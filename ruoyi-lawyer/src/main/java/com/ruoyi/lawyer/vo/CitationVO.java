package com.ruoyi.lawyer.vo;

/**
 * 法条引用
 *
 * @author ruoyi
 */
public class CitationVO
{
    /** 知识库切片ID */
    private Long chunkId;

    /** 法规文档ID */
    private Long docId;

    /** 法规名称 */
    private String docTitle;

    /** 规范化中文条号 */
    private String articleNo;

    /** 条文片段（≤200 字） */
    private String snippet;

    /** 相关度分数 */
    private Double score;

    /** BM25 路排名（1-based，未进候选为 null） */
    private Integer bm25Rank;

    /** Vector 路排名（1-based，未进候选为 null） */
    private Integer vectorRank;

    /** BM25 原始分（保留 2 位小数） */
    private Double bm25Score;

    /** 余弦相似度（保留 4 位小数） */
    private Double vectorScore;

    /** RRF 融合分（保留 4 位小数） */
    private Double rrfScore;

    /** 实际生效检索模式：bm25 | vector | hybrid_rrf */
    private String retrievalMode;

    public Long getChunkId()
    {
        return chunkId;
    }

    public void setChunkId(Long chunkId)
    {
        this.chunkId = chunkId;
    }

    public Long getDocId()
    {
        return docId;
    }

    public void setDocId(Long docId)
    {
        this.docId = docId;
    }

    public String getDocTitle()
    {
        return docTitle;
    }

    public void setDocTitle(String docTitle)
    {
        this.docTitle = docTitle;
    }

    public String getArticleNo()
    {
        return articleNo;
    }

    public void setArticleNo(String articleNo)
    {
        this.articleNo = articleNo;
    }

    public String getSnippet()
    {
        return snippet;
    }

    public void setSnippet(String snippet)
    {
        this.snippet = snippet;
    }

    public Double getScore()
    {
        return score;
    }

    public void setScore(Double score)
    {
        this.score = score;
    }

    public Integer getBm25Rank()
    {
        return bm25Rank;
    }

    public void setBm25Rank(Integer bm25Rank)
    {
        this.bm25Rank = bm25Rank;
    }

    public Integer getVectorRank()
    {
        return vectorRank;
    }

    public void setVectorRank(Integer vectorRank)
    {
        this.vectorRank = vectorRank;
    }

    public Double getBm25Score()
    {
        return bm25Score;
    }

    public void setBm25Score(Double bm25Score)
    {
        this.bm25Score = bm25Score;
    }

    public Double getVectorScore()
    {
        return vectorScore;
    }

    public void setVectorScore(Double vectorScore)
    {
        this.vectorScore = vectorScore;
    }

    public Double getRrfScore()
    {
        return rrfScore;
    }

    public void setRrfScore(Double rrfScore)
    {
        this.rrfScore = rrfScore;
    }

    public String getRetrievalMode()
    {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode)
    {
        this.retrievalMode = retrievalMode;
    }
}
