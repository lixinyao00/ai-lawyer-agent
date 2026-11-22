package com.ruoyi.lawyer.service;

import java.util.List;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.vo.ChatReplyVO;

/**
 * 咨询聊天 业务层
 *
 * @author ruoyi
 */
public interface ILawChatService
{
    /**
     * 查询会话的历史消息，按时间正序返回
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @return 消息集合
     */
    List<LawChatMessage> selectMessageList(Long sessionId, Long userId);

    /**
     * 发送问题并生成回答
     *
     * @param sessionId 会话ID
     * @param userId 当前用户ID
     * @param request 问题内容与请求ID
     * @return 本次问答结果
     */
    ChatReplyVO sendMessage(Long sessionId, Long userId, SendMessageRequest request);
}
