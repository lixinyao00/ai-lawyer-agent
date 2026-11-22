package com.ruoyi.lawyer.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawSessionService;

/**
 * 咨询会话 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawSessionServiceImpl implements ILawSessionService
{
    /** 默认会话标题 */
    private static final String DEFAULT_TITLE = "新咨询";

    /** 标题最大长度，与数据库字段保持一致 */
    private static final int TITLE_MAX_LENGTH = 100;

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Override
    public LawChatSession createSession(Long userId, String title)
    {
        if (userId == null)
        {
            throw new ServiceException("用户信息无效");
        }

        String sessionTitle = StringUtils.isBlank(title) ? DEFAULT_TITLE : title.trim();
        if (sessionTitle.length() > TITLE_MAX_LENGTH)
        {
            sessionTitle = sessionTitle.substring(0, TITLE_MAX_LENGTH);
        }

        Date now = new Date();
        LawChatSession session = new LawChatSession();
        session.setUserId(userId);
        session.setTitle(sessionTitle);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        sessionMapper.insertSession(session);
        return session;
    }

    @Override
    public List<LawChatSession> selectSessionList(Long userId)
    {
        return sessionMapper.selectSessionListByUserId(userId);
    }

    @Override
    @Transactional
    public void deleteSession(Long id, Long userId)
    {
        int rows = sessionMapper.deleteSessionByIdAndUserId(id, userId);
        if (rows == 0)
        {
            // 不区分“不存在”和“不属于当前用户”，避免泄露他人会话是否存在
            throw new ServiceException("会话不存在或无权访问");
        }
        // 归属校验通过后，同一事务内删除会话下的全部消息
        messageMapper.deleteMessagesBySessionId(id);
    }
}
