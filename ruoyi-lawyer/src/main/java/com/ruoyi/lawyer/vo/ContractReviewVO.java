package com.ruoyi.lawyer.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 合同风险审查详情视图对象
 *
 * @author ruoyi
 */
public class ContractReviewVO
{
    /** 主键 */
    private Long id;

    /** 关联私有文件ID */
    private Long fileId;

    /** 原件文件名 */
    private String fileName;

    /** 标题 */
    private String title;

    /** 立场：party_a / party_b / neutral */
    private String stance;

    /** 状态：pending / success / failed */
    private String status;

    /** 风险等级：high / medium / low */
    private String riskLevel;

    /** 总体评价 */
    private String summary;

    /** 风险项列表，空值输出 [] */
    private List<RiskItem> risks = new ArrayList<>();

    /** 实际使用的模型名 */
    private String modelName;

    /** 失败原因 */
    private String errorMessage;

    /** 合同全文 */
    private String contractText;

    /** 合同全文字数 */
    private Integer contractLength;

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

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
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

    public List<RiskItem> getRisks()
    {
        return risks;
    }

    public void setRisks(List<RiskItem> risks)
    {
        this.risks = risks == null ? new ArrayList<>() : risks;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getContractText()
    {
        return contractText;
    }

    public void setContractText(String contractText)
    {
        this.contractText = contractText;
    }

    public Integer getContractLength()
    {
        return contractLength;
    }

    public void setContractLength(Integer contractLength)
    {
        this.contractLength = contractLength;
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
