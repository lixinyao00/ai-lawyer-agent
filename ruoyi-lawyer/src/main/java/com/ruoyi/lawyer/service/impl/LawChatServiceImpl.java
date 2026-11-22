package com.ruoyi.lawyer.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawChatService;
import com.ruoyi.lawyer.vo.ChatReplyVO;

/**
 * 咨询聊天 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawChatServiceImpl implements ILawChatService
{
    private static final Logger log = LoggerFactory.getLogger(LawChatServiceImpl.class);

    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    private static final String DEFAULT_TITLE = "新咨询";
    private static final int AUTO_TITLE_LENGTH = 20;

    /** 带入模型的历史轮数，一轮包含一问一答 */
    private static final int HISTORY_ROUNDS = 10;

    private static final String ERROR_CODE_INTERNAL = "internal_error";

    /** 系统提示词文件路径，启动时加载一次 */
    @Value("classpath:prompts/legal-assistant.txt")
    private Resource promptResource;

    private String cachedPrompt;

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private com.ruoyi.lawyer.config.LawyerAiProperties aiProperties;

    /**
     * 每次使用时创建，保证事务边界明确：只包住数据库写入，不包住模型调用
     */
    private TransactionTemplate transaction()
    {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public List<LawChatMessage> selectMessageList(Long sessionId, Long userId)
    {
        checkSession(sessionId, userId);
        // 数据库按 id 倒序取最近一页，这里反转成时间正序后返回
        List<LawChatMessage> messages = messageMapper.selectMessagesBySessionId(sessionId);
        Collections.reverse(messages);
        return messages;
    }

    @Override
    public ChatReplyVO sendMessage(Long sessionId, Long userId, SendMessageRequest request)
    {
        LawChatSession session = checkSession(sessionId, userId);

        String requestId = request.getRequestId();
        String content = request.getContent().trim();
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("问题内容不能为空");
        }

        // 幂等：同一个请求ID已经有记录时，直接返回已有结果
        List<LawChatMessage> existing = messageMapper.selectMessagesByRequestId(sessionId, requestId);
        if (!existing.isEmpty())
        {
            return buildExistingReply(existing, requestId);
        }

        // 在本轮消息入库之前读取历史，避免把当前问题重复带入上下文
        List<AiChatClient.ChatMessage> history = loadHistory(sessionId);

        Date now = new Date();
        LawChatMessage userMessage = buildMessage(sessionId, requestId, ROLE_USER, content, STATUS_SUCCESS, now);
        LawChatMessage assistantMessage = buildMessage(sessionId, requestId, ROLE_ASSISTANT, "", STATUS_PENDING, now);

        try
        {
            // 第一个短事务：保存用户消息和 pending 占位，同时更新会话
            transaction().executeWithoutResult(status -> {
                messageMapper.insertMessage(userMessage);
                messageMapper.insertMessage(assistantMessage);
                updateSessionOnMessage(session, content, now);
            });
        }
        catch (DuplicateKeyException e)
        {
            // 并发下两个相同请求同时插入，唯一索引拦住其中一个，返回已存在的结果
            return buildExistingReply(messageMapper.selectMessagesByRequestId(sessionId, requestId), requestId);
        }

        ChatReplyVO reply = new ChatReplyVO();
        reply.setRequestId(requestId);
        reply.setUserMessageId(userMessage.getId());
        reply.setAssistantMessageId(assistantMessage.getId());

        try
        {
            // 模型调用放在事务之外
            String answer = generateReply(history, content);
            markAssistantResult(assistantMessage, STATUS_SUCCESS, answer, null, aiProperties.getModel());
            reply.setStatus(STATUS_SUCCESS);
            reply.setContent(answer);
        }
        catch (Exception e)
        {
            log.error("生成回答失败, sessionId={}, requestId={}", sessionId, requestId, e);
            markAssistantResult(assistantMessage, STATUS_FAILED, "", ERROR_CODE_INTERNAL, null);
            reply.setStatus(STATUS_FAILED);
            reply.setContent("");
            reply.setErrorMessage(e instanceof ServiceException ? e.getMessage() : "回答生成失败，请稍后重试");
        }
        return reply;
    }

    /**
     * 读取本会话最近若干轮成功问答，按时间正序转成模型消息
     *
     * @param sessionId 会话ID
     * @return 历史消息，最早的一轮在前
     */
    private List<AiChatClient.ChatMessage> loadHistory(Long sessionId)
    {
        List<LawChatMessage> history = messageMapper.selectRecentSuccessMessages(sessionId, HISTORY_ROUNDS);
        List<AiChatClient.ChatMessage> messages = new ArrayList<>(history.size());
        for (LawChatMessage message : history)
        {
            // 防御性跳过空正文，避免把空消息发给模型
            if (StringUtils.isEmpty(message.getContent()))
            {
                continue;
            }
            messages.add(new AiChatClient.ChatMessage(message.getRole(), message.getContent()));
        }
        return messages;
    }

    /**
     * 调用模型生成回答，按「系统提示词 → 历史问答 → 本次问题」顺序组装
     *
     * @param history 本会话此前成功的问答，最早的在最前
     * @param content 本次问题
     * @return 模型回答
     */
    private String generateReply(List<AiChatClient.ChatMessage> history, String content)
    {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(new AiChatClient.ChatMessage("system", loadSystemPrompt()));
        messages.addAll(history);
        messages.add(new AiChatClient.ChatMessage(ROLE_USER, content));
        log.info("请求模型, 历史消息={} 条, 本次请求共={} 条", history.size(), messages.size());
        return aiChatClient.chat(messages);
    }

    /**
     * 加载系统提示词，启动后缓存
     */
    private String loadSystemPrompt()
    {
        if (cachedPrompt != null)
        {
            return cachedPrompt;
        }
        try
        {
            cachedPrompt = new String(promptResource.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            return cachedPrompt;
        }
        catch (Exception e)
        {
            throw new ServiceException("系统提示词加载失败");
        }
    }

    /**
     * 第二个短事务：把助手消息从 pending 更新为成功或失败
     */
    private void markAssistantResult(LawChatMessage assistantMessage, String status, String content, String errorCode,
            String modelName)
    {
        assistantMessage.setStatus(status);
        assistantMessage.setContent(content);
        assistantMessage.setErrorCode(errorCode);
        assistantMessage.setModelName(modelName);
        assistantMessage.setUpdateTime(new Date());
        transaction().executeWithoutResult(s -> {
            messageMapper.updateMessageResult(assistantMessage);
            sessionMapper.updateSessionUpdateTime(assistantMessage.getSessionId(), assistantMessage.getUpdateTime());
        });
    }

    /**
     * 会话标题仍为默认值时，用第一句问题生成标题
     */
    private void updateSessionOnMessage(LawChatSession session, String content, Date now)
    {
        if (DEFAULT_TITLE.equals(session.getTitle()))
        {
            sessionMapper.updateSessionTitle(session.getId(), buildTitle(content), now);
        }
        else
        {
            sessionMapper.updateSessionUpdateTime(session.getId(), now);
        }
    }

    private String buildTitle(String content)
    {
        String text = content.replaceAll("\\s+", " ").trim();
        return text.length() > AUTO_TITLE_LENGTH ? text.substring(0, AUTO_TITLE_LENGTH) + "…" : text;
    }

    private LawChatMessage buildMessage(Long sessionId, String requestId, String role, String content, String status,
            Date now)
    {
        LawChatMessage message = new LawChatMessage();
        message.setSessionId(sessionId);
        message.setRequestId(requestId);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        return message;
    }

    /**
     * 校验会话存在且属于当前用户，否则统一报错，避免泄露他人会话是否存在
     */
    private LawChatSession checkSession(Long sessionId, Long userId)
    {
        LawChatSession session = sessionMapper.selectSessionByIdAndUserId(sessionId, userId);
        if (session == null)
        {
            throw new ServiceException("会话不存在或无权访问");
        }
        return session;
    }

    /**
     * 同一请求ID重复提交时，返回第一次的结果
     */
    private ChatReplyVO buildExistingReply(List<LawChatMessage> messages, String requestId)
    {
        ChatReplyVO reply = new ChatReplyVO();
        reply.setRequestId(requestId);

        LawChatMessage userMessage = null;
        LawChatMessage assistantMessage = null;
        for (LawChatMessage message : messages)
        {
            if (ROLE_USER.equals(message.getRole()))
            {
                userMessage = message;
            }
            else if (ROLE_ASSISTANT.equals(message.getRole()))
            {
                assistantMessage = message;
            }
        }

        if (userMessage != null)
        {
            reply.setUserMessageId(userMessage.getId());
        }
        if (assistantMessage == null)
        {
            reply.setStatus(STATUS_PENDING);
            return reply;
        }

        reply.setAssistantMessageId(assistantMessage.getId());
        reply.setStatus(assistantMessage.getStatus());
        reply.setContent(assistantMessage.getContent());
        if (STATUS_FAILED.equals(assistantMessage.getStatus()))
        {
            reply.setErrorMessage("上次回答失败，请重新提问");
        }
        return reply;
    }
}
