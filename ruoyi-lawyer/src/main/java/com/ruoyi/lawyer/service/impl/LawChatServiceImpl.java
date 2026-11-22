package com.ruoyi.lawyer.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiClientException;
import com.ruoyi.lawyer.config.LawyerAiProperties;
import com.ruoyi.lawyer.config.LawyerChatProperties;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawChatService;
import com.ruoyi.lawyer.support.ChatMessageConverter;
import com.ruoyi.lawyer.support.ContextTrimmer;
import com.ruoyi.lawyer.support.LawErrorCodes;
import com.ruoyi.lawyer.support.LawSessionLockService;
import com.ruoyi.lawyer.support.SseEventWriter;
import com.ruoyi.lawyer.vo.ChatMessageVO;
import com.ruoyi.lawyer.vo.ChatReplyVO;
import tools.jackson.databind.ObjectMapper;

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

    private static final String SESSION_BUSY_MESSAGE = "当前会话正在生成回答，请稍后再试";
    private static final String CONTENT_CONFLICT_MESSAGE = "请求ID已被使用且内容不一致，请刷新后重试";
    private static final String REPLAY_FAILED_MESSAGE = "上次回答失败，请重新提问";
    private static final String BUSY_MESSAGE = "服务繁忙，请稍后再试";

    /** 系统提示词文件路径，启动时加载一次 */
    @Value("classpath:prompts/legal-assistant.txt")
    private Resource promptResource;

    /** 系统提示词缓存，volatile 保证多线程可见性 */
    private volatile String cachedPrompt;

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private LawyerAiProperties aiProperties;

    @Autowired
    private LawyerChatProperties chatProperties;

    @Autowired
    private LawSessionLockService lockService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("lawyerStreamExecutor")
    private ThreadPoolTaskExecutor streamExecutor;

    @Autowired
    @Qualifier("lawyerHeartbeatScheduler")
    private ScheduledExecutorService heartbeatScheduler;

    /**
     * 每次使用时创建，保证事务边界明确：只包住数据库写入，不包住模型调用
     */
    private TransactionTemplate transaction()
    {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public List<ChatMessageVO> selectMessageList(Long sessionId, Long userId, Integer pageNum, Integer pageSize)
    {
        checkSession(sessionId, userId);
        // 未持锁时收敛遗留 pending，避免刷新页面永远看到“生成中”
        if (!lockService.isLocked(sessionId))
        {
            convergeStalePending(sessionId);
        }
        // 归属校验与收敛都完成后再分页，避免 PageHelper 被前面的查询消费
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 50 : pageSize;
        PageHelper.startPage(num, size);
        // 数据库按 id 倒序取最近一页，这里反转成时间正序后返回
        List<LawChatMessage> messages = messageMapper.selectMessagesBySessionId(sessionId);
        Collections.reverse(messages);
        return ChatMessageConverter.toPageVOList(messages, objectMapper, num, size);
    }

    @Override
    public ChatReplyVO sendMessage(Long sessionId, Long userId, SendMessageRequest request)
    {
        LawChatSession session = checkSession(sessionId, userId);
        String requestId = request.getRequestId();
        String content = normalizeContent(request);

        // 幂等预检：同一个请求ID已经有记录时，校验正文一致性后直接返回已有结果
        List<LawChatMessage> existing = messageMapper.selectMessagesByRequestId(sessionId, requestId);
        if (!existing.isEmpty())
        {
            validateIdempotentContent(existing, content);
            return buildExistingReply(existing, requestId);
        }

        String token = lockService.tryLock(sessionId);
        if (token == null)
        {
            throw new ServiceException(SESSION_BUSY_MESSAGE, 409);
        }
        try
        {
            // 拿到锁后重新校验会话归属与幂等，防止等待期间状态变化
            LawChatSession lockedSession = checkSession(sessionId, userId);
            List<LawChatMessage> rechecked = messageMapper.selectMessagesByRequestId(sessionId, requestId);
            if (!rechecked.isEmpty())
            {
                validateIdempotentContent(rechecked, content);
                return buildExistingReply(rechecked, requestId);
            }

            // 此时自己持锁，旧 pending 必为遗留
            convergeStalePending(sessionId);

            // 在本轮消息入库之前读取历史，避免把当前问题重复带入上下文
            List<AiChatClient.ChatMessage> history = loadHistory(sessionId);

            Date now = new Date();
            LawChatMessage userMessage = buildMessage(sessionId, requestId, ROLE_USER, content, STATUS_SUCCESS, now);
            LawChatMessage assistantMessage = buildMessage(sessionId, requestId, ROLE_ASSISTANT, "",
                    STATUS_PENDING, now);

            try
            {
                // 第一个短事务：保存用户消息和 pending 占位，同时更新会话
                transaction().executeWithoutResult(status -> {
                    messageMapper.insertMessage(userMessage);
                    messageMapper.insertMessage(assistantMessage);
                    updateSessionOnMessage(lockedSession, content, now);
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
                Failure failure = classifyFailure(e);
                log.error("生成回答失败, sessionId={}, requestId={}, errorCode={}", sessionId, requestId,
                        failure.errorCode, e);
                markAssistantResult(assistantMessage, STATUS_FAILED, "", failure.errorCode, null);
                reply.setStatus(STATUS_FAILED);
                reply.setContent("");
                reply.setErrorCode(failure.errorCode);
                reply.setErrorMessage(failure.userMessage);
            }
            return reply;
        }
        finally
        {
            lockService.unlock(sessionId, token);
        }
    }

    @Override
    public SseEmitter streamMessage(Long sessionId, Long userId, SendMessageRequest request)
    {
        LawChatSession session = checkSession(sessionId, userId);
        String requestId = request.getRequestId();
        String content = normalizeContent(request);

        // 幂等预检：命中则直接回放，不加锁
        List<LawChatMessage> existing = messageMapper.selectMessagesByRequestId(sessionId, requestId);
        if (!existing.isEmpty())
        {
            validateIdempotentContent(existing, content);
            return buildReplayEmitter(session, existing, requestId);
        }

        String token = lockService.tryLock(sessionId);
        if (token == null)
        {
            throw new ServiceException(SESSION_BUSY_MESSAGE, 409);
        }

        boolean handedOff = false;
        try
        {
            LawChatSession lockedSession = checkSession(sessionId, userId);
            List<LawChatMessage> rechecked = messageMapper.selectMessagesByRequestId(sessionId, requestId);
            if (!rechecked.isEmpty())
            {
                validateIdempotentContent(rechecked, content);
                return buildReplayEmitter(lockedSession, rechecked, requestId);
            }

            convergeStalePending(sessionId);
            List<AiChatClient.ChatMessage> history = loadHistory(sessionId);

            Date now = new Date();
            LawChatMessage userMessage = buildMessage(sessionId, requestId, ROLE_USER, content, STATUS_SUCCESS, now);
            LawChatMessage assistantMessage = buildMessage(sessionId, requestId, ROLE_ASSISTANT, "",
                    STATUS_PENDING, now);
            // M1 阶段无知识库，citations_json 保持 NULL
            assistantMessage.setCitationsJson(null);

            try
            {
                transaction().executeWithoutResult(status -> {
                    messageMapper.insertMessage(userMessage);
                    messageMapper.insertMessage(assistantMessage);
                    updateSessionOnMessage(lockedSession, content, now);
                });
            }
            catch (DuplicateKeyException e)
            {
                return buildReplayEmitter(lockedSession,
                        messageMapper.selectMessagesByRequestId(sessionId, requestId), requestId);
            }

            String sessionTitle = resolveSessionTitle(lockedSession, content);
            SseEmitter emitter = new SseEmitter(chatProperties.getSseTimeout());
            StreamContext context = new StreamContext(sessionId, requestId, sessionTitle,
                    userMessage.getId(), assistantMessage.getId(), history, content,
                    new SseEventWriter(emitter, objectMapper), token);

            // 客户端断开 / 超时 / 完成时置取消标志，通知模型读取停止
            emitter.onCompletion(context::cancel);
            emitter.onTimeout(context::cancel);
            emitter.onError(error -> context.cancel());

            try
            {
                streamExecutor.execute(() -> runStream(context, assistantMessage));
            }
            catch (TaskRejectedException e)
            {
                log.warn("流式线程池已满, sessionId={}, requestId={}", sessionId, requestId);
                markAssistantResult(assistantMessage, STATUS_FAILED, "", LawErrorCodes.INTERNAL_ERROR, null);
                throw new ServiceException(BUSY_MESSAGE, 409);
            }

            handedOff = true;
            return emitter;
        }
        finally
        {
            if (!handedOff)
            {
                lockService.unlock(sessionId, token);
            }
        }
    }

    /**
     * 异步流式任务：meta → delta → done / error，finally 释放锁
     */
    private void runStream(StreamContext context, LawChatMessage assistantMessage)
    {
        ScheduledFuture<?> heartbeat = null;
        try
        {
            try
            {
                context.writer.send("meta", meta(context, false));
            }
            catch (IOException e)
            {
                // 客户端在流开始前已断开
                handleAborted(context, assistantMessage, "");
                return;
            }
            if (context.cancelled.get())
            {
                handleAborted(context, assistantMessage, "");
                return;
            }

            heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> ping(context),
                    chatProperties.getHeartbeatSeconds(), chatProperties.getHeartbeatSeconds(), TimeUnit.SECONDS);

            List<AiChatClient.ChatMessage> modelMessages = buildModelMessages(context.history, context.content);
            String answer = aiChatClient.streamChat(modelMessages, delta -> {
                context.fullText.append(delta);
                try
                {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("content", delta);
                    context.writer.send("delta", data);
                }
                catch (IOException e)
                {
                    // 客户端已断开：置取消标志，让模型读取尽快停止
                    context.cancel();
                    throw new ClientAbortedException(e);
                }
            }, context.cancelled::get);

            if (context.cancelled.get())
            {
                handleAborted(context, assistantMessage, answer);
                return;
            }

            markAssistantResult(assistantMessage, STATUS_SUCCESS, answer, null, aiProperties.getModel());
            context.writer.send("done", doneData(STATUS_SUCCESS, assistantMessage.getId(), aiProperties.getModel(),
                    answer));
            context.writer.complete();
        }
        catch (ClientAbortedException e)
        {
            handleAborted(context, assistantMessage, context.fullText.toString());
        }
        catch (AiClientException e)
        {
            if (context.cancelled.get())
            {
                handleAborted(context, assistantMessage, context.fullText.toString());
                return;
            }
            Failure failure = classifyFailure(e);
            log.warn("流式回答失败, sessionId={}, requestId={}, errorCode={}, status={}", context.sessionId,
                    context.requestId, failure.errorCode, e.getStatusCode());
            markAssistantResult(assistantMessage, STATUS_FAILED, "", failure.errorCode, null);
            sendErrorQuietly(context, failure.sseCode, failure.userMessage, assistantMessage.getId());
        }
        catch (Exception e)
        {
            if (context.cancelled.get())
            {
                handleAborted(context, assistantMessage, context.fullText.toString());
                return;
            }
            log.error("流式回答异常, sessionId={}, requestId={}", context.sessionId, context.requestId, e);
            markAssistantResult(assistantMessage, STATUS_FAILED, "", LawErrorCodes.INTERNAL_ERROR, null);
            sendErrorQuietly(context, LawErrorCodes.SSE_INTERNAL, "回答生成失败，请稍后重试", assistantMessage.getId());
        }
        finally
        {
            if (heartbeat != null)
            {
                heartbeat.cancel(false);
            }
            lockService.unlock(context.sessionId, context.token);
        }
    }

    /**
     * 客户端中断：已有内容按 success + aborted 保存部分正文，为空则 failed/aborted
     */
    private void handleAborted(StreamContext context, LawChatMessage assistantMessage, String partialContent)
    {
        String content = partialContent == null ? "" : partialContent;
        if (!content.isEmpty())
        {
            markAssistantResult(assistantMessage, STATUS_SUCCESS, content, LawErrorCodes.ABORTED,
                    aiProperties.getModel());
        }
        else
        {
            markAssistantResult(assistantMessage, STATUS_FAILED, "", LawErrorCodes.ABORTED, null);
        }
        log.info("客户端中断生成, sessionId={}, requestId={}, length={}", context.sessionId, context.requestId,
                content.length());
        try
        {
            context.writer.complete();
        }
        catch (Exception ignored)
        {
            // 客户端已断开，忽略
        }
    }

    /**
     * 相同 requestId 回放：不加锁，按已有结果推送事件
     */
    private SseEmitter buildReplayEmitter(LawChatSession session, List<LawChatMessage> messages, String requestId)
    {
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

        SseEmitter emitter = new SseEmitter(chatProperties.getSseTimeout());
        SseEventWriter writer = new SseEventWriter(emitter, objectMapper);
        Long userMessageId = userMessage == null ? null : userMessage.getId();
        Long assistantMessageId = assistantMessage == null ? null : assistantMessage.getId();
        String title = session == null ? DEFAULT_TITLE : session.getTitle();

        try
        {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("requestId", requestId);
            meta.put("sessionId", session.getId());
            meta.put("userMessageId", userMessageId);
            meta.put("assistantMessageId", assistantMessageId);
            meta.put("sessionTitle", title);
            meta.put("replay", true);
            writer.send("meta", meta);

            if (assistantMessage == null || STATUS_PENDING.equals(assistantMessage.getStatus()))
            {
                writer.send("done", doneData(STATUS_PENDING, assistantMessageId, null, ""));
            }
            else if (STATUS_SUCCESS.equals(assistantMessage.getStatus()))
            {
                Map<String, Object> delta = new LinkedHashMap<>();
                delta.put("content", assistantMessage.getContent() == null ? "" : assistantMessage.getContent());
                writer.send("delta", delta);
                writer.send("done", doneData(STATUS_SUCCESS, assistantMessageId, assistantMessage.getModelName(),
                        assistantMessage.getContent() == null ? "" : assistantMessage.getContent()));
            }
            else
            {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("code", LawErrorCodes.SSE_AI_ERROR);
                error.put("message", REPLAY_FAILED_MESSAGE);
                error.put("assistantMessageId", assistantMessageId);
                writer.send("error", error);
            }
            writer.complete();
        }
        catch (IOException e)
        {
            log.debug("回放 SSE 写出失败, sessionId={}, requestId={}: {}", session.getId(), requestId,
                    e.getMessage());
        }
        return emitter;
    }

    /**
     * 组装 meta 事件
     */
    private Map<String, Object> meta(StreamContext context, boolean replay)
    {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("requestId", context.requestId);
        meta.put("sessionId", context.sessionId);
        meta.put("userMessageId", context.userMessageId);
        meta.put("assistantMessageId", context.assistantMessageId);
        meta.put("sessionTitle", context.sessionTitle);
        meta.put("replay", replay);
        return meta;
    }

    /**
     * done 事件数据
     */
    private Map<String, Object> doneData(String status, Long assistantMessageId, String modelName, String content)
    {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("status", status);
        done.put("assistantMessageId", assistantMessageId);
        done.put("modelName", modelName);
        done.put("content", content);
        done.put("citations", Collections.emptyList());
        return done;
    }

    private void sendErrorQuietly(StreamContext context, String code, String message, Long assistantMessageId)
    {
        if (context.cancelled.get())
        {
            return;
        }
        try
        {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);
            error.put("assistantMessageId", assistantMessageId);
            context.writer.send("error", error);
            context.writer.complete();
        }
        catch (Exception e)
        {
            log.debug("SSE error 事件写出失败, sessionId={}: {}", context.sessionId, e.getMessage());
        }
    }

    /**
     * 心跳：写失败说明客户端已断开
     */
    private void ping(StreamContext context)
    {
        if (context.cancelled.get())
        {
            return;
        }
        try
        {
            context.writer.ping();
        }
        catch (Exception e)
        {
            context.cancel();
        }
    }

    /**
     * 读取本会话最近若干轮成功问答，按时间正序转成模型消息，并做上下文预算裁剪
     */
    private List<AiChatClient.ChatMessage> loadHistory(Long sessionId)
    {
        List<LawChatMessage> history = messageMapper.selectRecentSuccessMessages(sessionId,
                chatProperties.getHistoryRounds());
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
        return ContextTrimmer.trim(messages, chatProperties.getHistoryRounds(), chatProperties.getHistoryMaxChars());
    }

    /**
     * 按「系统提示词 → 历史问答 → 本次问题」顺序组装模型消息
     */
    private List<AiChatClient.ChatMessage> buildModelMessages(List<AiChatClient.ChatMessage> history, String content)
    {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>(history.size() + 2);
        messages.add(new AiChatClient.ChatMessage("system", loadSystemPrompt()));
        messages.addAll(history);
        messages.add(new AiChatClient.ChatMessage(ROLE_USER, content));
        log.info("请求模型, 历史消息={} 条, 本次请求共={} 条", history.size(), messages.size());
        return messages;
    }

    private String generateReply(List<AiChatClient.ChatMessage> history, String content)
    {
        return aiChatClient.chat(buildModelMessages(history, content));
    }

    /**
     * 加载系统提示词，启动后缓存
     */
    private String loadSystemPrompt()
    {
        String local = cachedPrompt;
        if (local != null)
        {
            return local;
        }
        try
        {
            local = new String(promptResource.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            cachedPrompt = local;
            return local;
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
            int rows = messageMapper.updateMessageResult(assistantMessage);
            if (rows == 0)
            {
                log.warn("助手消息写回未生效（可能已被收敛）, messageId={}, status={}",
                        assistantMessage.getId(), status);
            }
            sessionMapper.updateSessionUpdateTime(assistantMessage.getSessionId(), assistantMessage.getUpdateTime());
        });
    }

    /**
     * 收敛超过 pending-timeout 的遗留助手消息
     */
    private void convergeStalePending(Long sessionId)
    {
        Date now = new Date();
        Date deadline = new Date(now.getTime() - chatProperties.getPendingTimeoutSeconds() * 1000L);
        int rows = messageMapper.failStalePending(sessionId, deadline, now);
        if (rows > 0)
        {
            log.info("收敛遗留 pending 消息, sessionId={}, rows={}", sessionId, rows);
        }
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

    /**
     * 计算本次发送后的会话标题（用于 meta 事件）
     */
    private String resolveSessionTitle(LawChatSession session, String content)
    {
        if (session != null && DEFAULT_TITLE.equals(session.getTitle()))
        {
            return buildTitle(content);
        }
        return session == null ? DEFAULT_TITLE : session.getTitle();
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
     * 校验请求正文
     */
    private String normalizeContent(SendMessageRequest request)
    {
        if (request == null || StringUtils.isEmpty(request.getContent()))
        {
            throw new ServiceException("问题内容不能为空", 422);
        }
        String content = request.getContent().trim();
        if (StringUtils.isEmpty(content))
        {
            throw new ServiceException("问题内容不能为空", 422);
        }
        return content;
    }

    /**
     * 重复 requestId 时校验正文一致性
     */
    private void validateIdempotentContent(List<LawChatMessage> messages, String content)
    {
        for (LawChatMessage message : messages)
        {
            if (ROLE_USER.equals(message.getRole()))
            {
                String existingContent = message.getContent() == null ? "" : message.getContent().trim();
                if (!existingContent.equals(content))
                {
                    throw new ServiceException(CONTENT_CONFLICT_MESSAGE, 422);
                }
                return;
            }
        }
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
        reply.setErrorCode(assistantMessage.getErrorCode());
        if (STATUS_FAILED.equals(assistantMessage.getStatus()))
        {
            reply.setErrorMessage(REPLAY_FAILED_MESSAGE);
        }
        return reply;
    }

    /**
     * 异常分类：落库 error_code、面向用户文案、SSE error code
     */
    private Failure classifyFailure(Exception e)
    {
        if (e instanceof AiClientException aiException)
        {
            if (aiException.getType() == AiClientException.Type.TIMEOUT)
            {
                return new Failure(LawErrorCodes.AI_TIMEOUT, "模型响应超时，请稍后重试", LawErrorCodes.SSE_AI_TIMEOUT);
            }
            String message = StringUtils.isNotEmpty(aiException.getMessage()) ? aiException.getMessage()
                    : "模型服务暂时不可用，请稍后重试";
            return new Failure(LawErrorCodes.AI_ERROR, message, LawErrorCodes.SSE_AI_ERROR);
        }
        if (e instanceof ServiceException serviceException && StringUtils.isNotEmpty(serviceException.getMessage()))
        {
            return new Failure(LawErrorCodes.INTERNAL_ERROR, serviceException.getMessage(),
                    LawErrorCodes.SSE_INTERNAL);
        }
        return new Failure(LawErrorCodes.INTERNAL_ERROR, "回答生成失败，请稍后重试", LawErrorCodes.SSE_INTERNAL);
    }

    /**
     * 流式任务上下文
     */
    private static final class StreamContext
    {
        private final Long sessionId;

        private final String requestId;

        private final String sessionTitle;

        private final Long userMessageId;

        private final Long assistantMessageId;

        private final List<AiChatClient.ChatMessage> history;

        private final String content;

        private final SseEventWriter writer;

        private final String token;

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private final StringBuilder fullText = new StringBuilder();

        private StreamContext(Long sessionId, String requestId, String sessionTitle, Long userMessageId,
                Long assistantMessageId, List<AiChatClient.ChatMessage> history, String content,
                SseEventWriter writer, String token)
        {
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.sessionTitle = sessionTitle;
            this.userMessageId = userMessageId;
            this.assistantMessageId = assistantMessageId;
            this.history = history;
            this.content = content;
            this.writer = writer;
            this.token = token;
        }

        private void cancel()
        {
            cancelled.set(true);
        }
    }

    /**
     * 客户端已断开
     */
    private static final class ClientAbortedException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private ClientAbortedException(Throwable cause)
        {
            super(cause);
        }
    }

    /**
     * 失败分类结果
     */
    private static final class Failure
    {
        private final String errorCode;

        private final String userMessage;

        private final String sseCode;

        private Failure(String errorCode, String userMessage, String sseCode)
        {
            this.errorCode = errorCode;
            this.userMessage = userMessage;
            this.sseCode = sseCode;
        }
    }
}
