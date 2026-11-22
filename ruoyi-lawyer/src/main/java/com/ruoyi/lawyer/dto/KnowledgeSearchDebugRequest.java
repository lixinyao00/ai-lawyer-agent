package com.ruoyi.lawyer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 知识库检索调试请求（三路对比 + 多轮指代改写）
 *
 * @author ruoyi
 */
public class KnowledgeSearchDebugRequest
{
    /** 查询文本 */
    @NotBlank(message = "查询内容不能为空")
    @Size(max = 500, message = "查询内容不能超过500个字符")
    private String query;

    /** 返回条数，默认 4，上限 10 */
    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 10, message = "topK 最大为 10")
    private Integer topK = 4;

    /** 检索模式：bm25 | vector | hybrid_rrf */
    @Size(max = 32, message = "检索模式不合法")
    private String mode = "hybrid_rrf";

    /** 多轮追问前序上下文（可选字符串，用于测试 QueryRewriter） */
    @Size(max = 2000, message = "历史上下文不能超过2000个字符")
    private String historyContext;

    public String getQuery()
    {
        return query;
    }

    public void setQuery(String query)
    {
        this.query = query;
    }

    public Integer getTopK()
    {
        return topK;
    }

    public void setTopK(Integer topK)
    {
        this.topK = topK;
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getHistoryContext()
    {
        return historyContext;
    }

    public void setHistoryContext(String historyContext)
    {
        this.historyContext = historyContext;
    }
}
