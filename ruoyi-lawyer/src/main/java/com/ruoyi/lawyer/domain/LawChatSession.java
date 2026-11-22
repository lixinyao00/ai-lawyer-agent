package com.ruoyi.lawyer.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 咨询会话对象 law_chat_session
 *
 * @author ruoyi
 */
public class LawChatSession extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 咨询标题 */
    private String title;

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

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }
}
