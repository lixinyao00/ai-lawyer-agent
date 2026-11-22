package com.ruoyi.lawyer.dto;

import java.util.Date;

/**
 * 后台会话审计查询条件
 *
 * @author ruoyi
 */
public class AdminSessionQuery
{
    /** 用户ID */
    private Long userId;

    /** 登录账号，模糊匹配 */
    private String userName;

    /** 会话标题，模糊匹配 */
    private String title;

    /** 开始时间（yyyy-MM-dd，按会话更新时间过滤） */
    private Date beginTime;

    /** 结束时间（yyyy-MM-dd，含当天） */
    private Date endTime;

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

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Date getBeginTime()
    {
        return beginTime;
    }

    public void setBeginTime(Date beginTime)
    {
        this.beginTime = beginTime;
    }

    public Date getEndTime()
    {
        return endTime;
    }

    public void setEndTime(Date endTime)
    {
        this.endTime = endTime;
    }
}
