package com.ruoyi.lawyer.dto;

/**
 * 合同审查列表查询条件
 *
 * @author ruoyi
 */
public class ContractReviewQuery
{
    /** 状态：pending / success / failed */
    private String status;

    /** 风险等级：high / medium / low */
    private String riskLevel;

    /** 标题模糊匹配 */
    private String title;

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRiskLevel()
    {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel)
    {
        this.riskLevel = riskLevel;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }
}
