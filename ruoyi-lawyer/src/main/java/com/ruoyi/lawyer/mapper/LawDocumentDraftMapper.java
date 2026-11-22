package com.ruoyi.lawyer.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawDocumentDraft;
import com.ruoyi.lawyer.dto.DraftQuery;
import com.ruoyi.lawyer.vo.DocumentDraftListVO;

/**
 * 法律文书草稿 数据层
 *
 * @author ruoyi
 */
public interface LawDocumentDraftMapper
{
    /**
     * 分页查询本人草稿列表
     *
     * @param userId 所有者用户ID
     * @param query 查询条件
     * @return 草稿列表
     */
    List<DocumentDraftListVO> selectDraftList(@Param("userId") Long userId, @Param("query") DraftQuery query);

    /**
     * 按主键与归属用户查询
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 草稿，不存在或非本人返回 null
     */
    LawDocumentDraft selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 新增草稿
     *
     * @param draft 草稿
     * @return 影响行数
     */
    int insertDraft(LawDocumentDraft draft);

    /**
     * 更新标题、正文并置为 success
     *
     * @param draft 更新字段
     * @return 影响行数
     */
    int updateDraftContent(LawDocumentDraft draft);

    /**
     * 按主键与归属用户删除
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 影响行数
     */
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
