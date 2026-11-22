package com.ruoyi.lawyer.service;

import java.util.List;
import com.ruoyi.lawyer.dto.AdminSessionQuery;
import com.ruoyi.lawyer.vo.AdminObservabilityVO;
import com.ruoyi.lawyer.vo.AdminSessionVO;
import com.ruoyi.lawyer.vo.ChatMessageVO;

/**
 * 后台会话审计 业务层
 *
 * @author ruoyi
 */
public interface ILawAdminSessionService
{
    /**
     * 后台会话分页查询
     *
     * @param query 查询条件
     * @return 会话视图集合
     */
    List<AdminSessionVO> selectSessionList(AdminSessionQuery query);

    /**
     * 分页查询指定会话的全部消息，按 id 正序
     *
     * @param sessionId 会话ID
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 消息视图集合（Page 子类，保留总数）
     */
    List<ChatMessageVO> selectMessageList(Long sessionId, Integer pageNum, Integer pageSize);

    /**
     * 后台可观测性聚合统计（Token / TTFT / RAG 命中 / Query 改写 / 工具调用）
     *
     * @param beginTime 起始日期 yyyy-MM-dd（含），可为空
     * @param endTime 结束日期 yyyy-MM-dd（含当天），可为空
     * @return 统计视图
     */
    AdminObservabilityVO getObservability(String beginTime, String endTime);

    /**
     * 后台删除会话（加锁，忙则 409）
     *
     * @param sessionId 会话ID
     */
    void deleteSession(Long sessionId);
}
