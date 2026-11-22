package com.ruoyi.lawyer.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;

/**
 * 法律知识库法规文档 数据层
 *
 * @author ruoyi
 */
public interface LawKnowledgeDocMapper
{
    /**
     * 分页查询法规文档
     *
     * @param title 名称模糊匹配
     * @param category 分类
     * @param status 状态
     * @return 文档集合
     */
    List<LawKnowledgeDoc> selectDocList(@Param("title") String title, @Param("category") String category,
            @Param("status") String status);

    /**
     * 按主键查询
     *
     * @param id 主键
     * @return 文档
     */
    LawKnowledgeDoc selectDocById(@Param("id") Long id);

    /**
     * 查询全部启用文档（检索索引）
     *
     * @return 启用文档集合
     */
    List<LawKnowledgeDoc> selectEnabledDocs();

    /**
     * 新增文档
     *
     * @param doc 文档
     * @return 影响行数
     */
    int insertDoc(LawKnowledgeDoc doc);

    /**
     * 更新文档元数据
     *
     * @param doc 文档
     * @return 影响行数
     */
    int updateDoc(LawKnowledgeDoc doc);

    /**
     * 批量删除文档
     *
     * @param ids 主键集合
     * @return 影响行数
     */
    int deleteDocByIds(@Param("ids") Long[] ids);

    /**
     * 回写切片数
     *
     * @param id 文档ID
     * @param chunkCount 切片数
     * @return 影响行数
     */
    int updateChunkCount(@Param("id") Long id, @Param("chunkCount") Integer chunkCount);

    /**
     * 统计文档总数（内置语料初始化判断）
     *
     * @return 文档总数
     */
    long countDocs();
}
