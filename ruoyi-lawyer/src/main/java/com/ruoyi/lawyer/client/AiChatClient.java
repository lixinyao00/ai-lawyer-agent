package com.ruoyi.lawyer.client;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 模型问答客户端
 *
 * @author ruoyi
 */
public interface AiChatClient
{
    /**
     * 一次非流式问答调用（使用 request-timeout）
     *
     * @param messages 按顺序排列的角色和消息，第一条通常是 system 提示词
     * @return 模型回答正文
     */
    String chat(List<ChatMessage> messages);

    /**
     * 一次非流式问答调用，自定义总超时
     *
     * @param messages 按顺序排列的角色和消息
     * @param timeout 本次调用总超时
     * @return 模型回答正文
     */
    String chat(List<ChatMessage> messages, java.time.Duration timeout);

    /**
     * 流式问答调用
     *
     * @param messages 按顺序排列的角色和消息
     * @param onDelta 增量内容回调
     * @param cancelled 取消判断，返回 true 时停止读取
     * @return 已接收的完整回答正文
     */
    String streamChat(List<ChatMessage> messages, Consumer<String> onDelta, BooleanSupplier cancelled);

    /**
     * 聊天消息
     */
    class ChatMessage
    {
        private final String role;
        private final String content;

        public ChatMessage(String role, String content)
        {
            this.role = role;
            this.content = content;
        }

        public String getRole()
        {
            return role;
        }

        public String getContent()
        {
            return content;
        }
    }
}
