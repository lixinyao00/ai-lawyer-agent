package com.ruoyi.lawyer.vo;

/**
 * 合同审查风险项（risks_json 元素，字段固定 6 个）
 *
 * @author ruoyi
 */
public class RiskItem
{
    /** 条款标题 */
    private String clauseTitle;

    /** 原文摘录 */
    private String originalSnippet;

    /** 风险等级：high / medium / low */
    private String riskLevel;

    /** 风险原因 */
    private String riskReason;

    /** 修改建议 */
    private String suggestion;

    /** 法律依据 */
    private String legalBasis;

    public RiskItem()
    {
    }

    public RiskItem(String clauseTitle, String originalSnippet, String riskLevel, String riskReason,
            String suggestion, String legalBasis)
    {
        this.clauseTitle = clauseTitle;
        this.originalSnippet = originalSnippet;
        this.riskLevel = riskLevel;
        this.riskReason = riskReason;
        this.suggestion = suggestion;
        this.legalBasis = legalBasis;
    }

    public String getClauseTitle()
    {
        return clauseTitle;
    }

    public void setClauseTitle(String clauseTitle)
    {
        this.clauseTitle = clauseTitle;
    }

    public String getOriginalSnippet()
    {
        return originalSnippet;
    }

    public void setOriginalSnippet(String originalSnippet)
    {
        this.originalSnippet = originalSnippet;
    }

    public String getRiskLevel()
    {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel)
    {
        this.riskLevel = riskLevel;
    }

    public String getRiskReason()
    {
        return riskReason;
    }

    public void setRiskReason(String riskReason)
    {
        this.riskReason = riskReason;
    }

    public String getSuggestion()
    {
        return suggestion;
    }

    public void setSuggestion(String suggestion)
    {
        this.suggestion = suggestion;
    }

    public String getLegalBasis()
    {
        return legalBasis;
    }

    public void setLegalBasis(String legalBasis)
    {
        this.legalBasis = legalBasis;
    }
}
