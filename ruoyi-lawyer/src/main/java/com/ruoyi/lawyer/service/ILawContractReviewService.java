package com.ruoyi.lawyer.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.lawyer.dto.ContractReviewQuery;
import com.ruoyi.lawyer.vo.ContractReviewListVO;
import com.ruoyi.lawyer.vo.ContractReviewVO;

/**
 * 合同风险审查 业务层
 *
 * @author ruoyi
 */
public interface ILawContractReviewService
{
    /**
     * 发起合同审查：同步取文本并插入 pending 记录，异步调用模型
     *
     * @param userId 所有者用户ID
     * @param file 上传的合同文件，可为 null
     * @param fileId 已上传私有文件ID，可为 null
     * @param contractText 直接粘贴的合同文本，可为 null
     * @param stance 立场：party_a / party_b / neutral
     * @param title 标题，可为 null
     * @return 审查详情（status=pending）
     */
    ContractReviewVO createReview(Long userId, MultipartFile file, Long fileId, String contractText, String stance,
            String title);

    /**
     * 分页查询本人审查记录
     *
     * @param userId 所有者用户ID
     * @param query 查询条件
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 审查列表
     */
    List<ContractReviewListVO> selectReviewList(Long userId, ContractReviewQuery query, Integer pageNum,
            Integer pageSize);

    /**
     * 查询本人审查详情
     *
     * @param id 审查ID
     * @param userId 所有者用户ID
     * @return 审查详情
     */
    ContractReviewVO selectReviewById(Long id, Long userId);

    /**
     * 删除审查记录；若关联文件不再被引用则级联删除文件记录与物理文件
     *
     * @param id 审查ID
     * @param userId 所有者用户ID
     */
    void deleteReview(Long id, Long userId);
}
