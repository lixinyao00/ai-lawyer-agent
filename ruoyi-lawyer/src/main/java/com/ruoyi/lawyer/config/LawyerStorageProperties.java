package com.ruoyi.lawyer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 私有文件存储配置
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "lawyer.storage")
public class LawyerStorageProperties
{
    /** 私有文件存储根目录 */
    private String root = "./data/lawyer-files";

    /** 允许的扩展名，逗号分隔，不带点 */
    private String allowedExt = "docx,pdf,txt,md";

    /** 单文件大小上限（MB） */
    private long maxSizeMb = 10L;

    public String getRoot()
    {
        return root;
    }

    public void setRoot(String root)
    {
        this.root = root;
    }

    public String getAllowedExt()
    {
        return allowedExt;
    }

    public void setAllowedExt(String allowedExt)
    {
        this.allowedExt = allowedExt;
    }

    public long getMaxSizeMb()
    {
        return maxSizeMb;
    }

    public void setMaxSizeMb(long maxSizeMb)
    {
        this.maxSizeMb = maxSizeMb;
    }
}
