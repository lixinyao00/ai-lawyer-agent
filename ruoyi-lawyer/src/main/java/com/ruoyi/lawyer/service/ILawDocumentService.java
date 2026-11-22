package com.ruoyi.lawyer.service;

import java.util.List;
import com.ruoyi.lawyer.dto.CreateDraftRequest;
import com.ruoyi.lawyer.dto.DraftQuery;
import com.ruoyi.lawyer.dto.UpdateDraftRequest;
import com.ruoyi.lawyer.vo.DocumentDraftListVO;
import com.ruoyi.lawyer.vo.DocumentDraftVO;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;

/**
 * 法律文书草稿 业务层
 *
 * @author ruoyi
 */
public interface ILawDocumentService
{
    /**
     * 全部文书模板
     *
     * @return 模板列表
     */
    List<DocumentTemplateVO> listTemplates();

    /**
     * 创建文书草稿：校验模板与要素，同步调用模型生成正文（失败也落库）
     *
     * @param userId 所有者用户ID
     * @param request 创建请求
     * @return 草稿详情
     */
    DocumentDraftVO createDraft(Long userId, CreateDraftRequest request);

    /**
     * 编辑文书草稿并将状态置为 success
     *
     * @param id 草稿ID
     * @param userId 所有者用户ID
     * @param request 更新请求
     * @return 更新后的草稿详情
     */
    DocumentDraftVO updateDraft(Long id, Long userId, UpdateDraftRequest request);

    /**
     * 分页查询本人草稿
     *
     * @param userId 所有者用户ID
     * @param query 查询条件
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 草稿列表
     */
    List<DocumentDraftListVO> selectDraftList(Long userId, DraftQuery query, Integer pageNum, Integer pageSize);

    /**
     * 查询本人草稿详情
     *
     * @param id 草稿ID
     * @param userId 所有者用户ID
     * @return 草稿详情
     */
    DocumentDraftVO selectDraftById(Long id, Long userId);

    /**
     * 删除本人草稿
     *
     * @param id 草稿ID
     * @param userId 所有者用户ID
     */
    void deleteDraft(Long id, Long userId);

    /**
     * 查询可导出的草稿（必须 success 且正文非空）
     *
     * @param id 草稿ID
     * @param userId 所有者用户ID
     * @return 草稿详情
     */
    DocumentDraftVO selectExportableDraft(Long id, Long userId);

    /**
     * 将草稿导出为 DOCX 字节流
     *
     * @param draft 草稿详情
     * @return .docx 字节
     */
    byte[] exportDocx(DocumentDraftVO draft);
}
