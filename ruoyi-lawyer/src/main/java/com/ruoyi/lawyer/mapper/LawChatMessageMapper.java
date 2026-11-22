package com.ruoyi.lawyer.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawChatMessage;

/**
 * 咨询消息 数据层
 *
 * @author ruoyi
 */
public interface LawChatMessageMapper
{
    /**
     * 新增消息
     *
     * @param message 消息信息
     * @return 影响行数
     */
    int insertMessage(LawChatMessage message);

    /**
     * 按会话ID和请求ID查询本次发送产生的消息
     *
     * @param sessionId 会话ID
     * @param requestId 请求ID
     * @return 消息集合
     */
    List<LawChatMessage> selectMessagesByRequestId(@Param("sessionId") Long sessionId,
            @Param("requestId") String requestId);

    /**
     * 更新助手消息的正文、状态和错误信息
     *
     * @param message 消息信息
     * @return 影响行数
     */
    int updateMessageResult(LawChatMessage message);

    /**
     * 分页查询会话消息，按消息ID倒序返回
     *
     * @param sessionId 会话ID
     * @return 消息集合
     */
    List<LawChatMessage> selectMessagesBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 读取最近 limit 轮 status=success 的问答，按时间正序返回（user 与 assistant 成对）
     *
     * @param sessionId 会话ID
     * @param limit 最近轮数
     * @return 消息集合
     */
    List<LawChatMessage> selectRecentSuccessMessages(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    /**
     * 删除会话下的全部消息
     *
     * @param sessionId 会话ID
     * @return 影响行数
     */
    int deleteMessagesBySessionId(@Param("sessionId") Long sessionId);
}
