package com.ruoyi.lawyer.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;

/**
 * 法律知识库切片 数据层
 *
 * @author ruoyi
 */
public interface LawKnowledgeChunkMapper
{
    /**
     * 批量新增切片
     *
     * @param chunks 切片集合
     * @return 影响行数
     */
    int batchInsertChunks(@Param("chunks") List<LawKnowledgeChunk> chunks);

    /**
     * 分页查询某文档的切片，按 id 正序
     *
     * @param docId 文档ID
     * @param keyword 内容/条号模糊匹配
     * @return 切片集合
     */
    List<LawKnowledgeChunk> selectChunksByDocId(@Param("docId") Long docId, @Param("keyword") String keyword);

    /**
     * 按主键查询切片
     *
     * @param id 切片ID
     * @return 切片
     */
    LawKnowledgeChunk selectChunkById(@Param("id") Long id);

    /**
     * 更新切片
     *
     * @param chunk 切片
     * @return 影响行数
     */
    int updateChunk(LawKnowledgeChunk chunk);

    /**
     * 删除单个切片（同时校验所属文档）
     *
     * @param id 切片ID
     * @param docId 文档ID
     * @return 影响行数
     */
    int deleteChunkById(@Param("id") Long id, @Param("docId") Long docId);

    /**
     * 删除某文档的全部切片
     *
     * @param docId 文档ID
     * @return 影响行数
     */
    int deleteChunksByDocId(@Param("docId") Long docId);

    /**
     * 批量删除多个文档的全部切片
     *
     * @param docIds 文档ID集合
     * @return 影响行数
     */
    int deleteChunksByDocIds(@Param("docIds") Long[] docIds);

    /**
     * 统计某文档的切片数
     *
     * @param docId 文档ID
     * @return 切片数
     */
    int countChunksByDocId(@Param("docId") Long docId);

    /**
     * 查询全部启用文档的切片（检索索引，带法规名称）
     *
     * @param docIds 启用文档ID集合
     * @return 切片集合
     */
    List<LawKnowledgeChunk> selectChunksByDocIds(@Param("docIds") List<Long> docIds);
}
