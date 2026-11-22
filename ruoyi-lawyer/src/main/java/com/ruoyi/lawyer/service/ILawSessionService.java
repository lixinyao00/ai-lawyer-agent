package com.ruoyi.lawyer.service;

import java.util.List;
import com.ruoyi.lawyer.domain.LawChatSession;

/**
 * 咨询会话 业务层
 *
 * @author ruoyi
 */
public interface ILawSessionService
{
    /**
     * 新建会话
     *
     * @param userId 所属用户ID
     * @param title 会话标题，为空时使用默认标题
     * @return 新建的会话
     */
    LawChatSession createSession(Long userId, String title);

    /**
     * 查询某个用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话集合
     */
    List<LawChatSession> selectSessionList(Long userId);

    /**
     * 删除会话，删除前校验归属
     *
     * @param id 会话ID
     * @param userId 当前用户ID
     */
    void deleteSession(Long id, Long userId);
}
