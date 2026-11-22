package com.ruoyi.lawyer.domain;

import java.io.Serializable;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 用户私有文件对象 law_private_file
 *
 * <p>物理文件存储在 lawyer.storage.root 下，storage_path 仅为相对路径，
 * 绝不通过 /profile/** 暴露，也不直接下发前端。</p>
 *
 * @author ruoyi
 */
public class LawPrivateFile implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所有者用户ID */
    private Long userId;

    /** 原始文件名（已去除路径成分） */
    private String originalName;

    /** 相对存储根的路径：{userId}/{yyyyMM}/{uuid}.{ext} */
    private String storagePath;

    /** 小写不带点扩展名：docx/pdf/txt/md */
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

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getOriginalName()
    {
        return originalName;
    }

    public void setOriginalName(String originalName)
    {
        this.originalName = originalName;
    }

    public String getStoragePath()
    {
        return storagePath;
    }

    public void setStoragePath(String storagePath)
    {
        this.storagePath = storagePath;
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
