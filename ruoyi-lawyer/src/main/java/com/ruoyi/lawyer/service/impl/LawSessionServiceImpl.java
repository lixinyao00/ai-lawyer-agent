package com.ruoyi.lawyer.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawSessionService;
import com.ruoyi.lawyer.support.LawSessionLockService;

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

    private static final String DELETE_BUSY_MESSAGE = "会话正在生成回答，暂不能删除";

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Autowired
    private LawSessionLockService lockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    public void deleteSession(Long id, Long userId)
    {
        // 先校验归属，再抢锁；不存在与无权访问统一文案
        if (sessionMapper.selectSessionByIdAndUserId(id, userId) == null)
        {
            throw new ServiceException("会话不存在或无权访问");
        }

        String token = lockService.tryLock(id);
        if (token == null)
        {
            throw new ServiceException(DELETE_BUSY_MESSAGE, 409);
        }
        try
        {
            // 同一短事务内删除消息与会话
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                int rows = sessionMapper.deleteSessionByIdAndUserId(id, userId);
                if (rows == 0)
                {
                    throw new ServiceException("会话不存在或无权访问");
                }
                messageMapper.deleteMessagesBySessionId(id);
            });
        }
        finally
        {
            lockService.unlock(id, token);
        }
    }
}
