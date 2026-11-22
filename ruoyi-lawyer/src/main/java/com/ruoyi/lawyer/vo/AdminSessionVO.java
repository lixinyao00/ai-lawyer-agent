package com.ruoyi.lawyer.vo;

import java.util.Date;

/**
 * 后台会话审计视图
 *
 * @author ruoyi
 */
public class AdminSessionVO
{
    /** 会话ID */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 登录账号 */
    private String userName;

    /** 用户昵称 */
    private String nickName;

    /** 会话标题 */
    private String title;

    /** 消息总数 */
    private Long messageCount;

    /** 失败消息数 */
    private Long failedCount;

    /** 创建时间 */
    private Date createTime;

    /** 最近更新时间 */
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

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getMessageCount()
    {
        return messageCount;
    }

    public void setMessageCount(Long messageCount)
    {
        this.messageCount = messageCount;
    }

    public Long getFailedCount()
    {
        return failedCount;
    }

    public void setFailedCount(Long failedCount)
    {
        this.failedCount = failedCount;
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
