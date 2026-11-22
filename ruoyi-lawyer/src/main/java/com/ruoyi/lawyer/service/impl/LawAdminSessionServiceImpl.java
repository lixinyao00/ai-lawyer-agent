package com.ruoyi.lawyer.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.config.LawyerChatProperties;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.dto.AdminSessionQuery;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawAdminSessionService;
import com.ruoyi.lawyer.support.ChatMessageConverter;
import com.ruoyi.lawyer.support.LawSessionLockService;
import com.ruoyi.lawyer.vo.AdminSessionVO;
import com.ruoyi.lawyer.vo.ChatMessageVO;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台会话审计 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawAdminSessionServiceImpl implements ILawAdminSessionService
{
    private static final String DELETE_BUSY_MESSAGE = "会话正在生成回答，暂不能删除";

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Autowired
    private LawSessionLockService lockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LawyerChatProperties chatProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<AdminSessionVO> selectSessionList(AdminSessionQuery query)
    {
        return sessionMapper.selectAdminSessionList(query == null ? new AdminSessionQuery() : query);
    }

    @Override
    public List<ChatMessageVO> selectMessageList(Long sessionId, Integer pageNum, Integer pageSize)
    {
        if (sessionMapper.selectSessionById(sessionId) == null)
        {
            throw new ServiceException("会话不存在");
        }
        // 与前台一致：未持锁时收敛遗留 pending
        if (!lockService.isLocked(sessionId))
        {
            Date now = new Date();
            Date deadline = new Date(now.getTime() - chatProperties.getPendingTimeoutSeconds() * 1000L);
            messageMapper.failStalePending(sessionId, deadline, now);
        }
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 50 : pageSize;
        PageHelper.startPage(num, size);
        List<LawChatMessage> messages = messageMapper.selectMessagesBySessionIdAsc(sessionId);
        return ChatMessageConverter.toPageVOList(messages, objectMapper, num, size);
    }

    @Override
    public void deleteSession(Long sessionId)
    {
        if (sessionMapper.selectSessionById(sessionId) == null)
        {
            throw new ServiceException("会话不存在");
        }
        String token = lockService.tryLock(sessionId);
        if (token == null)
        {
            throw new ServiceException(DELETE_BUSY_MESSAGE, 409);
        }
        try
        {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                messageMapper.deleteMessagesBySessionId(sessionId);
                sessionMapper.deleteSessionById(sessionId);
            });
        }
        finally
        {
            lockService.unlock(sessionId, token);
        }
    }
}
