package com.ruoyi.lawyer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiClientException;
import com.ruoyi.lawyer.config.LawyerAiProperties;
import com.ruoyi.lawyer.config.LawyerChatProperties;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.support.LawSessionLockService;
import com.ruoyi.lawyer.vo.ChatReplyVO;
import com.ruoyi.lawyer.vo.CitationVO;
import tools.jackson.databind.ObjectMapper;

/**
 * LawChatServiceImpl 单元测试（Mockito，不启动 Spring 上下文）
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LawChatServiceImplTest
{
    private static final Long SESSION_ID = 1L;

    private static final Long USER_ID = 7L;

    private static final String REQUEST_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";

    @Mock
    private LawChatSessionMapper sessionMapper;

    @Mock
    private LawChatMessageMapper messageMapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private AiChatClient aiChatClient;

    @Mock
    private LawyerAiProperties aiProperties;

    @Mock
    private LawyerChatProperties chatProperties;

    @Mock
    private LawyerKnowledgeProperties knowledgeProperties;

    @Mock
    private KnowledgeRetriever knowledgeRetriever;

    @Mock
    private LawSessionLockService lockService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ThreadPoolTaskExecutor streamExecutor;

    @Mock
    private ScheduledExecutorService heartbeatScheduler;

    @InjectMocks
    private LawChatServiceImpl service;

    private LawChatSession session;

    private SendMessageRequest request;

    @BeforeEach
    void setUp() throws Exception
    {
        session = new LawChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setTitle("新咨询");
        when(sessionMapper.selectSessionByIdAndUserId(SESSION_ID, USER_ID)).thenReturn(session);

        lenient().when(chatProperties.getHistoryRounds()).thenReturn(10);
        lenient().when(chatProperties.getHistoryMaxChars()).thenReturn(12000);
        lenient().when(chatProperties.getPendingTimeoutSeconds()).thenReturn(120L);
        lenient().when(aiProperties.getModel()).thenReturn("test-model");
        lenient().when(knowledgeProperties.isEnabled()).thenReturn(false);

        Resource promptResource = mock(Resource.class);
        lenient().when(promptResource.getInputStream())
                .thenReturn(new ByteArrayInputStream("system prompt".getBytes(StandardCharsets.UTF_8)));
        ReflectionTestUtils.setField(service, "promptResource", promptResource);

        request = new SendMessageRequest();
        request.setContent("问题");
        request.setRequestId(REQUEST_ID);
    }

    @Test
    void duplicateRequestIdWithDifferentContentReturns422()
    {
        when(messageMapper.selectMessagesByRequestId(SESSION_ID, REQUEST_ID))
                .thenReturn(List.of(message("user", "旧问题", "success")));

        request.setContent("新问题");
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.sendMessage(SESSION_ID, USER_ID, request));

        assertEquals(422, exception.getCode());
        verify(lockService, never()).tryLock(any());
    }

    @Test
    void duplicateRequestIdWithSameContentReturnsExistingReplyWithoutLock()
    {
        when(messageMapper.selectMessagesByRequestId(SESSION_ID, REQUEST_ID))
                .thenReturn(List.of(message("user", "问题", "success"),
                        message("assistant", "已有回答", "success")));

        ChatReplyVO reply = service.sendMessage(SESSION_ID, USER_ID, request);

        assertEquals("success", reply.getStatus());
        assertEquals("已有回答", reply.getContent());
        verify(lockService, never()).tryLock(any());
    }

    @Test
    void lockConflictReturns409()
    {
        when(lockService.tryLock(SESSION_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.sendMessage(SESSION_ID, USER_ID, request));

        assertEquals(409, exception.getCode());
        verify(messageMapper, never()).failStalePending(any(), any(), any());
    }

    @Test
    void afterLockConvergesStalePendingAndWritesSuccess()
    {
        when(lockService.tryLock(SESSION_ID)).thenReturn("token-1");
        when(aiChatClient.chat(anyList())).thenReturn("模型回答");

        ChatReplyVO reply = service.sendMessage(SESSION_ID, USER_ID, request);

        assertEquals("success", reply.getStatus());
        assertEquals("模型回答", reply.getContent());
        verify(messageMapper).failStalePending(eq(SESSION_ID), any(Date.class), any(Date.class));

        ArgumentCaptor<LawChatMessage> captor = ArgumentCaptor.forClass(LawChatMessage.class);
        verify(messageMapper).updateMessageResult(captor.capture());
        assertEquals("success", captor.getValue().getStatus());
        assertEquals("模型回答", captor.getValue().getContent());
        verify(lockService).unlock(SESSION_ID, "token-1");
    }

    @Test
    void aiFailureWritesFailedWithAiTimeout()
    {
        when(lockService.tryLock(SESSION_ID)).thenReturn("token-1");
        when(aiChatClient.chat(anyList()))
                .thenThrow(new AiClientException(AiClientException.Type.TIMEOUT, "timeout"));

        ChatReplyVO reply = service.sendMessage(SESSION_ID, USER_ID, request);

        assertEquals("failed", reply.getStatus());
        assertEquals("ai_timeout", reply.getErrorCode());

        ArgumentCaptor<LawChatMessage> captor = ArgumentCaptor.forClass(LawChatMessage.class);
        verify(messageMapper).updateMessageResult(captor.capture());
        assertEquals("failed", captor.getValue().getStatus());
        assertEquals("ai_timeout", captor.getValue().getErrorCode());
        verify(lockService).unlock(SESSION_ID, "token-1");
    }

    @Test
    void historyIsTrimmedBeforeBeingSentToModel()
    {
        when(lockService.tryLock(SESSION_ID)).thenReturn("token-1");
        when(aiChatClient.chat(anyList())).thenReturn("回答");

        List<LawChatMessage> history = new ArrayList<>();
        for (int i = 0; i < 15; i++)
        {
            history.add(message("user", "u" + i + repeat('x', 700), "success"));
            history.add(message("assistant", "a" + i + repeat('y', 700), "success"));
        }
        when(messageMapper.selectRecentSuccessMessages(eq(SESSION_ID), anyInt())).thenReturn(history);

        service.sendMessage(SESSION_ID, USER_ID, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatClient.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).chat(captor.capture());
        // 15 对 -> 保留最近 10 对 -> 再按 12000 字预算裁剪为 8 对（16 条）+ system + 本次问题
        assertEquals(18, captor.getValue().size());
        assertEquals("system", captor.getValue().get(0).getRole());
        assertEquals("user", captor.getValue().get(1).getRole());
        assertNotNull(captor.getValue().get(17).getContent());
        assertEquals("user", captor.getValue().get(17).getRole());
    }

    @Test
    void knowledgeCitationsAreStoredAndReturned()
    {
        when(lockService.tryLock(SESSION_ID)).thenReturn("token-1");
        when(knowledgeProperties.isEnabled()).thenReturn(true);
        when(knowledgeProperties.getTopK()).thenReturn(4);
        when(knowledgeProperties.getPromptMaxChars()).thenReturn(4000);
        when(aiChatClient.chat(anyList())).thenReturn("回答");

        CitationVO citation = new CitationVO();
        citation.setChunkId(5L);
        citation.setDocId(1L);
        citation.setDocTitle("中华人民共和国劳动合同法");
        citation.setArticleNo("第四十七条");
        citation.setSnippet("经济补偿按劳动者在本单位工作的年限……");
        citation.setScore(7.42);
        when(knowledgeRetriever.search(anyString(), eq(4))).thenReturn(List.of(citation));
        when(knowledgeRetriever.buildPromptSection(anyList(), anyInt())).thenReturn("【参考法条】...");
        when(objectMapper.writeValueAsString(any())).thenReturn("[{\"chunkId\":5}]");

        ChatReplyVO reply = service.sendMessage(SESSION_ID, USER_ID, request);

        assertEquals("success", reply.getStatus());
        assertEquals(1, reply.getCitations().size());
        assertEquals("第四十七条", reply.getCitations().get(0).getArticleNo());

        ArgumentCaptor<LawChatMessage> captor = ArgumentCaptor.forClass(LawChatMessage.class);
        verify(messageMapper, times(2)).insertMessage(captor.capture());
        LawChatMessage assistant = captor.getAllValues().stream()
                .filter(message -> "assistant".equals(message.getRole()))
                .findFirst()
                .orElseThrow();
        assertEquals("[{\"chunkId\":5}]", assistant.getCitationsJson());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiChatClient.ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(aiChatClient).chat(messages.capture());
        assertEquals("system", messages.getValue().get(0).getRole());
        assertEquals(true, messages.getValue().get(0).getContent().contains("【参考法条】"));
    }

    private LawChatMessage message(String role, String content, String status)
    {
        LawChatMessage message = new LawChatMessage();
        message.setSessionId(SESSION_ID);
        message.setRequestId(REQUEST_ID);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        message.setCreateTime(new Date());
        message.setUpdateTime(new Date());
        return message;
    }

    private String repeat(char c, int count)
    {
        return String.valueOf(c).repeat(count);
    }
}
