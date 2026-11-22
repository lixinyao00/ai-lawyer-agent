package com.ruoyi.lawyer.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.dto.AdminSessionQuery;
import com.ruoyi.lawyer.vo.AdminSessionVO;

/**
 * 咨询会话 数据层
 *
 * @author ruoyi
 */
public interface LawChatSessionMapper
{
    /**
     * 新增会话
     *
     * @param session 会话信息
     * @return 影响行数
     */
    int insertSession(LawChatSession session);

    /**
     * 查询某个用户的会话列表
     *
     * @param userId 用户ID
     * @return 会话集合
     */
    List<LawChatSession> selectSessionListByUserId(@Param("userId") Long userId);

    /**
     * 按会话ID和用户ID查询会话，用于校验归属
     *
     * @param id 会话ID
     * @param userId 用户ID
     * @return 会话信息
     */
    LawChatSession selectSessionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 按会话ID查询会话（后台审计）
     *
     * @param id 会话ID
     * @return 会话信息
     */
    LawChatSession selectSessionById(@Param("id") Long id);

    /**
     * 后台会话审计分页查询
     *
     * @param query 查询条件
     * @return 会话视图集合
     */
    List<AdminSessionVO> selectAdminSessionList(AdminSessionQuery query);

    /**
     * 更新会话的最近更新时间
     *
     * @param id 会话ID
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateSessionUpdateTime(@Param("id") Long id, @Param("updateTime") Date updateTime);

    /**
     * 更新会话标题和最近更新时间
     *
     * @param id 会话ID
     * @param title 新标题
     * @param updateTime 更新时间
     * @return 影响行数
     */
    int updateSessionTitle(@Param("id") Long id, @Param("title") String title, @Param("updateTime") Date updateTime);

    /**
     * 按会话ID和用户ID删除会话，同时校验归属
     *
     * @param id 会话ID
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteSessionByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 按会话ID删除会话（后台删除，调用方已校验存在性）
     *
     * @param id 会话ID
     * @return 影响行数
     */
    int deleteSessionById(@Param("id") Long id);
}
