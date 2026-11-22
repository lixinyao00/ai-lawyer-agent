package com.ruoyi.lawyer.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.dto.KnowledgeChunkRequest;
import com.ruoyi.lawyer.dto.KnowledgeDocRequest;
import com.ruoyi.lawyer.dto.KnowledgeRechunkRequest;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.retrieval.ArticleSplitter;
import com.ruoyi.lawyer.retrieval.ChineseNumberUtils;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.service.ILawKnowledgeService;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.KnowledgeChunkVO;
import com.ruoyi.lawyer.vo.KnowledgeDocVO;

/**
 * 法律知识库 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawKnowledgeServiceImpl implements ILawKnowledgeService
{
    private static final Logger log = LoggerFactory.getLogger(LawKnowledgeServiceImpl.class);

    /** 允许的分类 */
    private static final Set<String> CATEGORIES = Set.of(
            "labor", "civil_contract", "civil_lease", "civil_loan", "civil_tort", "other");

    /** 允许的扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("txt", "md", "docx");

    /** 导入文件大小上限：10MB */
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    /** 批量插入切片的分批大小 */
    private static final int BATCH_SIZE = 200;

    /** 查询文本最大长度 */
    private static final int MAX_QUERY_CHARS = 500;

    @Autowired
    private LawKnowledgeDocMapper docMapper;

    @Autowired
    private LawKnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeRetriever knowledgeRetriever;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction()
    {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public List<KnowledgeDocVO> selectDocList(String title, String category, String status, Integer pageNum,
            Integer pageSize)
    {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(num, size);
        List<LawKnowledgeDoc> rows = docMapper.selectDocList(trimToNull(title), trimToNull(category),
                trimToNull(status));
        long total = rows instanceof Page<?> page ? page.getTotal() : rows.size();
        Page<KnowledgeDocVO> result = new Page<>(num, size);
        result.setTotal(total);
        for (LawKnowledgeDoc row : rows)
        {
            result.add(toDocVO(row));
        }
        return result;
    }

    @Override
    public KnowledgeDocVO selectDocById(Long id)
    {
        LawKnowledgeDoc doc = docMapper.selectDocById(id);
        if (doc == null)
        {
            throw new ServiceException("法规不存在");
        }
        return toDocVO(doc);
    }

    @Override
    public Map<String, Object> addDoc(KnowledgeDocRequest request, String createBy)
    {
        validateDocRequest(request);
        Date now = new Date();
        LawKnowledgeDoc doc = new LawKnowledgeDoc();
        applyDocFields(doc, request);
        doc.setChunkCount(0);
        doc.setCreateBy(createBy);
        doc.setCreateTime(now);
        doc.setUpdateTime(now);

        int chunkCount = transaction().execute(status -> {
            docMapper.insertDoc(doc);
            return insertChunks(doc.getId(), request.getContent(), now);
        });
        docMapper.updateChunkCount(doc.getId(), chunkCount);
        knowledgeRetriever.invalidate();
        return result(doc.getId(), chunkCount);
    }

    @Override
    public void updateDoc(Long id, KnowledgeDocRequest request)
    {
        LawKnowledgeDoc existing = docMapper.selectDocById(id);
        if (existing == null)
        {
            throw new ServiceException("法规不存在");
        }
        validateDocRequest(request);
        LawKnowledgeDoc doc = new LawKnowledgeDoc();
        doc.setId(id);
        applyDocFields(doc, request);
        doc.setUpdateTime(new Date());
        docMapper.updateDoc(doc);
        knowledgeRetriever.invalidate();
    }

    @Override
    public Map<String, Object> rechunkDoc(Long id, KnowledgeRechunkRequest request)
    {
        LawKnowledgeDoc doc = docMapper.selectDocById(id);
        if (doc == null)
        {
            throw new ServiceException("法规不存在");
        }
        if (request == null || StringUtils.isBlank(request.getContent()))
        {
            throw new ServiceException("法规内容不能为空", 422);
        }
        Date now = new Date();
        int chunkCount = transaction().execute(status -> {
            chunkMapper.deleteChunksByDocId(id);
            return insertChunks(id, request.getContent(), now);
        });
        docMapper.updateChunkCount(id, chunkCount);
        knowledgeRetriever.invalidate();
        return result(id, chunkCount);
    }

    @Override
    public void deleteDocByIds(Long[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的法规", 422);
        }
        transaction().executeWithoutResult(status -> {
            chunkMapper.deleteChunksByDocIds(ids);
            docMapper.deleteDocByIds(ids);
        });
        knowledgeRetriever.invalidate();
    }

    @Override
    public Map<String, Object> importDoc(MultipartFile file, KnowledgeDocRequest request, String createBy)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择要导入的文件", 422);
        }
        if (file.getSize() > MAX_FILE_BYTES)
        {
            throw new ServiceException("文件大小不能超过10MB", 422);
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = extension(originalName);
        if (!ALLOWED_EXT.contains(ext))
        {
            throw new ServiceException("仅支持导入 .txt/.md/.docx 文件", 422);
        }
        if (request == null)
        {
            request = new KnowledgeDocRequest();
        }
        if (StringUtils.isBlank(request.getTitle()))
        {
            request.setTitle(baseName(originalName));
        }
        request.setContent(readContent(file, ext));
        return addDoc(request, createBy);
    }

    @Override
    public List<KnowledgeChunkVO> selectChunkList(Long docId, String keyword, Integer pageNum, Integer pageSize)
    {
        LawKnowledgeDoc doc = docMapper.selectDocById(docId);
        if (doc == null)
        {
            throw new ServiceException("法规不存在");
        }
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(num, size);
        List<LawKnowledgeChunk> rows = chunkMapper.selectChunksByDocId(docId, trimToNull(keyword));
        long total = rows instanceof Page<?> page ? page.getTotal() : rows.size();
        Page<KnowledgeChunkVO> result = new Page<>(num, size);
        result.setTotal(total);
        for (LawKnowledgeChunk row : rows)
        {
            result.add(toChunkVO(row));
        }
        return result;
    }

    @Override
    public void updateChunk(Long docId, Long chunkId, KnowledgeChunkRequest request)
    {
        LawKnowledgeChunk existing = chunkMapper.selectChunkById(chunkId);
        if (existing == null || !docId.equals(existing.getDocId()))
        {
            throw new ServiceException("切片不存在");
        }
        if (request == null || StringUtils.isBlank(request.getContent()))
        {
            throw new ServiceException("条文内容不能为空", 422);
        }
        String articleNo = request.getArticleNo();
        if (StringUtils.isNotBlank(articleNo))
        {
            String normalized = ChineseNumberUtils.normalizeArticleNo(articleNo);
            articleNo = normalized == null ? articleNo.trim() : normalized;
        }
        LawKnowledgeChunk chunk = new LawKnowledgeChunk();
        chunk.setId(chunkId);
        chunk.setArticleNo(StringUtils.isBlank(articleNo) ? null : articleNo);
        chunk.setContent(request.getContent().trim());
        chunk.setKeywords(trimToNull(request.getKeywords()));
        chunk.setTokenCount(chunk.getContent().length());
        chunkMapper.updateChunk(chunk);
        knowledgeRetriever.invalidate();
    }

    @Override
    public void deleteChunk(Long docId, Long chunkId)
    {
        int rows = chunkMapper.deleteChunkById(chunkId, docId);
        if (rows == 0)
        {
            throw new ServiceException("切片不存在");
        }
        docMapper.updateChunkCount(docId, chunkMapper.countChunksByDocId(docId));
        knowledgeRetriever.invalidate();
    }

    @Override
    public List<CitationVO> search(String query, Integer topK)
    {
        if (StringUtils.isBlank(query))
        {
            return new ArrayList<>();
        }
        String text = query.trim();
        if (text.length() > MAX_QUERY_CHARS)
        {
            throw new ServiceException("查询内容不能超过500个字符", 422);
        }
        return knowledgeRetriever.search(text, topK);
    }

    private void validateDocRequest(KnowledgeDocRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("请求参数不能为空", 422);
        }
        if (StringUtils.isBlank(request.getTitle()))
        {
            throw new ServiceException("法规名称不能为空", 422);
        }
        if (request.getTitle().trim().length() > 200)
        {
            throw new ServiceException("法规名称不能超过200个字符", 422);
        }
        if (StringUtils.isBlank(request.getCategory()) || !CATEGORIES.contains(request.getCategory().trim()))
        {
            throw new ServiceException("分类不合法", 422);
        }
        if (request.getDocNumber() != null && request.getDocNumber().length() > 100)
        {
            throw new ServiceException("文号不能超过100个字符", 422);
        }
        if (request.getRemark() != null && request.getRemark().length() > 500)
        {
            throw new ServiceException("备注不能超过500个字符", 422);
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()
                && !"0".equals(request.getStatus()) && !"1".equals(request.getStatus()))
        {
            throw new ServiceException("状态不合法", 422);
        }
        if (request.getContent() != null && request.getContent().length() > 500000)
        {
            throw new ServiceException("法规内容不能超过500000个字符", 422);
        }
    }

    private void applyDocFields(LawKnowledgeDoc doc, KnowledgeDocRequest request)
    {
        doc.setTitle(request.getTitle().trim());
        doc.setCategory(request.getCategory().trim());
        doc.setDocNumber(trimToNull(request.getDocNumber()));
        doc.setEffectiveDate(request.getEffectiveDate());
        doc.setStatus(StringUtils.isBlank(request.getStatus()) ? "0" : request.getStatus().trim());
        doc.setRemark(trimToNull(request.getRemark()));
    }

    private int insertChunks(Long docId, String content, Date now)
    {
        if (StringUtils.isBlank(content))
        {
            return 0;
        }
        List<ArticleSplitter.Chunk> pieces = ArticleSplitter.split(content);
        List<LawKnowledgeChunk> batch = new ArrayList<>(Math.min(pieces.size(), BATCH_SIZE));
        int total = 0;
        for (ArticleSplitter.Chunk piece : pieces)
        {
            LawKnowledgeChunk chunk = new LawKnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setArticleNo(piece.getArticleNo());
            chunk.setContent(piece.getContent());
            chunk.setKeywords(piece.getKeywords());
            chunk.setTokenCount(piece.getContent() == null ? 0 : piece.getContent().length());
            chunk.setCreateTime(now);
            batch.add(chunk);
            total++;
            if (batch.size() >= BATCH_SIZE)
            {
                chunkMapper.batchInsertChunks(batch);
                batch = new ArrayList<>(BATCH_SIZE);
            }
        }
        if (!batch.isEmpty())
        {
            chunkMapper.batchInsertChunks(batch);
        }
        log.info("法规切分完成, docId={}, chunks={}", docId, total);
        return total;
    }

    private String readContent(MultipartFile file, String ext)
    {
        try
        {
            byte[] bytes = file.getBytes();
            if ("docx".equals(ext))
            {
                return readDocx(bytes);
            }
            return decodeText(bytes);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("文件读取失败：" + e.getMessage(), 422);
        }
    }

    private String readDocx(byte[] bytes) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                String text = paragraph.getText();
                if (StringUtils.isNotBlank(text))
                {
                    builder.append(text.trim()).append('\n');
                }
            }
        }
        String content = builder.toString().trim();
        if (content.isEmpty())
        {
            throw new ServiceException("未能从文件中提取到文字，请检查文件内容", 422);
        }
        return content;
    }

    /**
     * txt/md：UTF-8 优先，失败回退 GB18030
     */
    private String decodeText(byte[] bytes)
    {
        String text = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (text == null)
        {
            text = decodeStrict(bytes, java.nio.charset.Charset.forName("GB18030"));
        }
        if (text == null)
        {
            throw new ServiceException("文件编码无法识别，请使用 UTF-8 或 GB18030 编码", 422);
        }
        if (text.startsWith("\uFEFF"))
        {
            text = text.substring(1);
        }
        text = text.trim();
        if (text.isEmpty())
        {
            throw new ServiceException("未能从文件中提取到文字，请检查文件内容", 422);
        }
        return text;
    }

    private String decodeStrict(byte[] bytes, java.nio.charset.Charset charset)
    {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try
        {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException e)
        {
            return null;
        }
    }

    private String extension(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }

    private String baseName(String fileName)
    {
        String name = fileName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0)
        {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> result(Long docId, int chunkCount)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docId", docId);
        result.put("chunkCount", chunkCount);
        return result;
    }

    private KnowledgeDocVO toDocVO(LawKnowledgeDoc doc)
    {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        vo.setId(doc.getId());
        vo.setTitle(doc.getTitle());
        vo.setCategory(doc.getCategory());
        vo.setDocNumber(doc.getDocNumber());
        vo.setEffectiveDate(doc.getEffectiveDate());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCreateBy(doc.getCreateBy());
        vo.setCreateTime(doc.getCreateTime());
        vo.setUpdateTime(doc.getUpdateTime());
        vo.setRemark(doc.getRemark());
        return vo;
    }

    private KnowledgeChunkVO toChunkVO(LawKnowledgeChunk chunk)
    {
        KnowledgeChunkVO vo = new KnowledgeChunkVO();
        vo.setId(chunk.getId());
        vo.setDocId(chunk.getDocId());
        vo.setArticleNo(chunk.getArticleNo());
        vo.setContent(chunk.getContent());
        vo.setKeywords(chunk.getKeywords());
        vo.setTokenCount(chunk.getTokenCount());
        vo.setCreateTime(chunk.getCreateTime());
        return vo;
    }
}
