package com.ruoyi.lawyer.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 知识库切片视图对象
 *
 * @author ruoyi
 */
public class KnowledgeChunkVO
{
    /** 主键 */
    private Long id;

    /** 所属法规ID */
    private Long docId;

    /** 规范化中文条号 */
    private String articleNo;

    /** 条文原文 */
    private String content;

    /** 章节标题 + 关键词 */
    private String keywords;

    /** token 估算值 */
    private Integer tokenCount;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

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
}
