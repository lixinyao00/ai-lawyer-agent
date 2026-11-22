package com.ruoyi.lawyer.domain;

import java.io.Serializable;
import java.util.Date;

/**
 * 法律知识库切片对象 law_knowledge_chunk
 *
 * @author ruoyi
 */
public class LawKnowledgeChunk implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属法规ID */
    private Long docId;

    /** 规范化中文条号，序言/无条号段为 null */
    private String articleNo;

    /** 条文原文（不含章节标题） */
    private String content;

    /** 章节标题 + 人工关键词，空格分隔 */
    private String keywords;

    /** token 估算值（V1 用 content.length()） */
    private Integer tokenCount;

    /** 创建时间 */
    private Date createTime;

    /** 所属法规名称（检索索引使用，非表字段） */
    private String docTitle;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getDocId()
    {
        return docId;
    }

    public void setDocId(Long docId)
    {
        this.docId = docId;
    }

    public String getArticleNo()
    {
        return articleNo;
    }

    public void setArticleNo(String articleNo)
    {
        this.articleNo = articleNo;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

    public String getKeywords()
    {
        return keywords;
    }

    public void setKeywords(String keywords)
    {
        this.keywords = keywords;
    }

    public Integer getTokenCount()
    {
        return tokenCount;
    }

    public void setTokenCount(Integer tokenCount)
    {
        this.tokenCount = tokenCount;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public String getDocTitle()
    {
        return docTitle;
    }

    public void setDocTitle(String docTitle)
    {
        this.docTitle = docTitle;
    }
}
