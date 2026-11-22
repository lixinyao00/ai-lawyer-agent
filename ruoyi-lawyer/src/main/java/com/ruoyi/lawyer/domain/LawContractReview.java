package com.ruoyi.lawyer.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 合同风险审查对象 law_contract_review
 *
 * @author ruoyi
 */
public class LawContractReview implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所有者用户ID */
    private Long userId;

    /** 关联私有文件ID；粘贴文本审查时为 NULL */
    private Long fileId;

    /** 标题 */
    private String title;

    /** 立场：party_a / party_b / neutral */
    private String stance;

    /** 抽取后合同全文（≤50000 字） */
    private String contractText;

    /** 状态：pending / success / failed */
    private String status;

    /** 风险等级：high / medium / low */
    private String riskLevel;

    /** 总体评价 */
    private String summary;

    /** 风险项 JSON 数组字符串，对外不直接暴露 */
    @JsonIgnore
    private String risksJson;

    /** 实际使用的模型名 */
    private String modelName;

    /** 面向用户的失败原因 */
    private String errorMsg;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getStance()
    {
        return stance;
    }

    public void setStance(String stance)
    {
        this.stance = stance;
    }

    public String getContractText()
    {
        return contractText;
    }

    public void setContractText(String contractText)
    {
        this.contractText = contractText;
    }

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

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getRisksJson()
    {
        return risksJson;
    }

    public void setRisksJson(String risksJson)
    {
        this.risksJson = risksJson;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getErrorMsg()
    {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg)
    {
        this.errorMsg = errorMsg;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Date getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime)
    {
        this.updateTime = updateTime;
    }
}
