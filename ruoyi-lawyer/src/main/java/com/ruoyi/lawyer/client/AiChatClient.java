package com.ruoyi.lawyer.client;

import java.util.List;

/**
 * 模型问答客户端
 *
 * @author ruoyi
 */
public interface AiChatClient
{
    /**
     * 一次非流式问答调用
     *
     * @param messages 按顺序排列的角色和消息，第一条通常是 system 提示词
     * @return 模型回答正文
     */
    String chat(List<ChatMessage> messages);

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
