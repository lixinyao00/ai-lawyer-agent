package com.ruoyi.lawyer.dto;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 知识库法规文档新增/修改请求
 *
 * @author ruoyi
 */
public class KnowledgeDocRequest
{
    /** 法规名称 */
    @NotBlank(message = "法规名称不能为空")
    @Size(max = 200, message = "法规名称不能超过200个字符")
    private String title;

    /** 分类：labor/civil_contract/civil_lease/civil_loan/civil_tort/other */
    @NotBlank(message = "分类不能为空")
    @Size(max = 32, message = "分类不能超过32个字符")
    private String category;

    /** 文号/版本 */
    @Size(max = 100, message = "文号不能超过100个字符")
    private String docNumber;

    /** 施行日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date effectiveDate;

    /** 状态：0 启用 / 1 停用 */
    private String status;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    /** 法规全文（仅新增时可选，传入后自动切分入库） */
    @Size(max = 500000, message = "法规内容不能超过500000个字符")
    private String content;

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String category)
    {
        this.category = category;
    }

    public String getDocNumber()
    {
        return docNumber;
    }

    public void setDocNumber(String docNumber)
    {
        this.docNumber = docNumber;
    }

    public Date getEffectiveDate()
    {
        return effectiveDate;
    }

    public void setEffectiveDate(Date effectiveDate)
    {
        this.effectiveDate = effectiveDate;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }
}
