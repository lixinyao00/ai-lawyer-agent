package com.ruoyi.lawyer.service;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.vo.ChatMessageVO;
import com.ruoyi.lawyer.vo.ChatReplyVO;

/**
 * 咨询聊天 业务层
 *
 * @author ruoyi
 */
public interface ILawChatService
{
    /**
     * 分页查询会话的历史消息，按时间正序返回
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 消息集合（Page 子类，保留总数）
     */
    List<ChatMessageVO> selectMessageList(Long sessionId, Long userId, Integer pageNum, Integer pageSize);

    /**
     * 发送问题并生成回答（同步，兼容 + 降级）
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @param request 问题内容与请求ID
     * @return 本次问答结果
     */
    ChatReplyVO sendMessage(Long sessionId, Long userId, SendMessageRequest request);

    /**
     * 流式发送问题并生成回答
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @param request 问题内容与请求ID
     * @return SSE 发射器
     */
    SseEmitter streamMessage(Long sessionId, Long userId, SendMessageRequest request);
}
