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
}
