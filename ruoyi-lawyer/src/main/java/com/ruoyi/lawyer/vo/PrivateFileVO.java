package com.ruoyi.lawyer.vo;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 私有文件视图对象（不暴露 storage_path）
 *
 * @author ruoyi
 */
public class PrivateFileVO
{
    /** 主键 */
    private Long id;

    /** 原始文件名 */
    private String originalName;

    /** 小写不带点扩展名 */
    private String fileExt;

    /** 文件字节数 */
    private Long fileSize;

    /** SHA-256 十六进制小写 */
    private String sha256;

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

    public String getOriginalName()
    {
        return originalName;
    }

    public void setOriginalName(String originalName)
    {
        this.originalName = originalName;
    }

    public String getFileExt()
    {
        return fileExt;
    }

    public void setFileExt(String fileExt)
    {
        this.fileExt = fileExt;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getSha256()
    {
        return sha256;
    }

    public void setSha256(String sha256)
    {
        this.sha256 = sha256;
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
