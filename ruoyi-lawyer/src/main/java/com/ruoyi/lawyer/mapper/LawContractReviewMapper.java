package com.ruoyi.lawyer.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawContractReview;
import com.ruoyi.lawyer.dto.ContractReviewQuery;
import com.ruoyi.lawyer.vo.ContractReviewListVO;

/**
 * 合同风险审查 数据层
 *
 * @author ruoyi
 */
public interface LawContractReviewMapper
{
    /**
     * 分页查询本人审查记录
     *
     * @param userId 所有者用户ID
     * @param query 查询条件
     * @return 审查列表
     */
    List<ContractReviewListVO> selectReviewList(@Param("userId") Long userId,
            @Param("query") ContractReviewQuery query);

    /**
     * 按主键与归属用户查询
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 审查记录，不存在或非本人返回 null
     */
    LawContractReview selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 新增审查记录
     *
     * @param review 审查记录
     * @return 影响行数
     */
    int insertReview(LawContractReview review);

    /**
     * 写回异步审查结果（仅 pending 记录可写回）
     *
     * @param review 结果字段
     * @return 影响行数
     */
    int updateReviewResult(LawContractReview review);

    /**
     * 收敛超时未完成的本人审查记录
     *
     * @param userId 所有者用户ID
     * @param deadline 创建时间早于此时间视为超时
     * @param now 当前时间
     * @return 影响行数
     */
    int failStalePending(@Param("userId") Long userId, @Param("deadline") Date deadline,
            @Param("now") Date now);

    /**
     * 按主键与归属用户删除
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 影响行数
     */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 统计某私有文件被多少审查记录引用
     *
     * @param fileId 私有文件ID
     * @return 引用数
     */
    long countByFileId(@Param("fileId") Long fileId);
}
