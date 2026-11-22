package com.ruoyi.lawyer.service;

import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.lawyer.dto.KnowledgeChunkRequest;
import com.ruoyi.lawyer.dto.KnowledgeDocRequest;
import com.ruoyi.lawyer.dto.KnowledgeRechunkRequest;
import com.ruoyi.lawyer.dto.KnowledgeSearchDebugRequest;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.KnowledgeChunkVO;
import com.ruoyi.lawyer.vo.KnowledgeDocVO;
import com.ruoyi.lawyer.vo.KnowledgeSearchDebugVO;

/**
 * 法律知识库 业务层
 *
 * @author ruoyi
 */
public interface ILawKnowledgeService
{
    /**
     * 分页查询法规文档
     *
     * @param title 名称模糊匹配
     * @param category 分类
     * @param status 状态
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 文档视图集合（保留分页总数）
     */
    List<KnowledgeDocVO> selectDocList(String title, String category, String status, Integer pageNum,
            Integer pageSize);

    /**
     * 查询文档详情
     *
     * @param id 文档ID
     * @return 文档视图
     */
    KnowledgeDocVO selectDocById(Long id);

    /**
     * 新增文档，content 非空时自动切分入库
     *
     * @param request 请求
     * @param createBy 创建者
     * @return {docId, chunkCount}
     */
    Map<String, Object> addDoc(KnowledgeDocRequest request, String createBy);

    /**
     * 修改文档元数据（不含正文）
     *
     * @param id 文档ID
     * @param request 请求
     */
    void updateDoc(Long id, KnowledgeDocRequest request);

    /**
     * 删除旧切片后重新切分
     *
     * @param id 文档ID
     * @param request 重新切分请求
     * @return {docId, chunkCount}
     */
    Map<String, Object> rechunkDoc(Long id, KnowledgeRechunkRequest request);

    /**
     * 批量删除文档并级联删除切片
     *
     * @param ids 文档ID集合
     */
    void deleteDocByIds(Long[] ids);

    /**
     * 导入文件（.txt/.md/.docx）并自动切分入库
     *
     * @param file 文件
     * @param request 元数据
     * @param createBy 创建者
     * @return {docId, chunkCount}
     */
    Map<String, Object> importDoc(MultipartFile file, KnowledgeDocRequest request, String createBy);

    /**
     * 分页查询某文档的切片，按 id 正序
     *
     * @param docId 文档ID
     * @param keyword 内容/条号模糊匹配
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 切片视图集合（保留分页总数）
     */
    List<KnowledgeChunkVO> selectChunkList(Long docId, String keyword, Integer pageNum, Integer pageSize);

    /**
     * 修改切片
     *
     * @param docId 文档ID
     * @param chunkId 切片ID
     * @param request 请求
     */
    void updateChunk(Long docId, Long chunkId, KnowledgeChunkRequest request);

    /**
     * 删除切片并回写 chunk_count
     *
     * @param docId 文档ID
     * @param chunkId 切片ID
     */
    void deleteChunk(Long docId, Long chunkId);

    /**
     * 检索调试：与聊天共用检索器
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @return 引用列表
     */
    List<CitationVO> search(String query, Integer topK);

    /**
     * 指定检索模式的检索
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @param mode bm25 / vector / hybrid_rrf，空值使用配置默认模式
     * @return 引用列表
     */
    List<CitationVO> search(String query, Integer topK, String mode);

    /**
     * 检索调试：多轮改写 + 三路对比
     *
     * @param request 调试请求
     * @return 调试视图
     */
    KnowledgeSearchDebugVO searchDebug(KnowledgeSearchDebugRequest request);

    /**
     * 重建切片向量并回写 embedding_json / embedding_model
     *
     * @param docId 法规ID，为空表示重建全部启用文档
     * @return {totalChunks, updatedChunks, skippedChunks, model, embeddingMode, durationMs}
     */
    Map<String, Object> rebuildEmbeddings(Long docId);
}
