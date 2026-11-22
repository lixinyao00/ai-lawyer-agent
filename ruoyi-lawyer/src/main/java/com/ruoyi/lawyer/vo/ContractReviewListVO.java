package com.ruoyi.lawyer.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 合同风险审查列表视图对象
 *
 * @author ruoyi
 */
public class ContractReviewListVO
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

    /** 风险项数量（JSON_LENGTH，NULL 记 0） */
    private Integer riskCount;

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

    public Integer getRiskCount()
    {
        return riskCount;
    }

    public void setRiskCount(Integer riskCount)
    {
        this.riskCount = riskCount;
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
