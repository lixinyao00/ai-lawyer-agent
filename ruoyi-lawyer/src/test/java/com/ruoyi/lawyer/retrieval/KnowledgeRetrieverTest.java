package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * KnowledgeRetriever 单元测试
 *
 * @author ruoyi
 */
class KnowledgeRetrieverTest
{
    private static final String LABOR_TITLE = "中华人民共和国劳动合同法";

    private static final String CIVIL_TITLE = "中华人民共和国民法典";

    private LawKnowledgeDocMapper docMapper;

    private LawKnowledgeChunkMapper chunkMapper;

    private LawyerKnowledgeProperties properties;

    private KnowledgeRetriever retriever;

    @BeforeEach
    void setUp()
    {
        docMapper = mock(LawKnowledgeDocMapper.class);
        chunkMapper = mock(LawKnowledgeChunkMapper.class);
        properties = mock(LawyerKnowledgeProperties.class);
        lenient().when(properties.getTopK()).thenReturn(4);
        lenient().when(properties.getMinScore()).thenReturn(1.0);
        lenient().when(properties.getSnippetMaxChars()).thenReturn(200);
        lenient().when(properties.getPromptMaxChars()).thenReturn(4000);
        lenient().when(properties.getRefreshMinutes()).thenReturn(10L);

        LawKnowledgeDoc labor = doc(1L, LABOR_TITLE, "labor");
        LawKnowledgeDoc civil = doc(2L, CIVIL_TITLE, "civil_contract");
        LawKnowledgeDoc disabled = doc(3L, "中华人民共和国劳动合同法（已废止）", "labor");
        when(docMapper.selectEnabledDocs()).thenReturn(List.of(labor, civil));

        List<LawKnowledgeChunk> chunks = new ArrayList<>();
        chunks.add(chunk(11L, 1L, "第四十七条",
                "经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。"));
        chunks.add(chunk(12L, 1L, "第八十七条",
                "用人单位违反本法规定解除或者终止劳动合同的，应当依照本法第四十七条规定的经济补偿标准的二倍向劳动者支付赔偿金。"));
        chunks.add(chunk(13L, 2L, "第五百六十三条", "有下列情形之一的，当事人可以解除合同。"));
        chunks.add(chunk(14L, 2L, "第七百零三条", "租赁合同是出租人将租赁物交付承租人使用、收益，承租人支付租金的合同。"));
        // 停用文档的切片：即便被查询返回，也必须被检索器过滤
        chunks.add(chunk(15L, 3L, "第九十九条", "已废止法规中的劳动者特别规定。"));
        when(chunkMapper.selectChunksByDocIds(anyList())).thenReturn(chunks);

        retriever = new KnowledgeRetriever(docMapper, chunkMapper, properties);
    }

    @Test
    void exactArticleQueryHitsTopOne()
    {
        List<CitationVO> citations = retriever.search("劳动合同法第四十七条", 4);

        assertFalse(citations.isEmpty());
        assertEquals("第四十七条", citations.get(0).getArticleNo());
        assertEquals(LABOR_TITLE, citations.get(0).getDocTitle());
        assertEquals(11L, citations.get(0).getChunkId());
        assertTrue(citations.get(0).getScore() >= 5.0);
    }

    @Test
    void articleQueryWithoutDocNameStillHitsTopOne()
    {
        List<CitationVO> citations = retriever.search("第四十七条", 4);

        assertFalse(citations.isEmpty());
        assertEquals("第四十七条", citations.get(0).getArticleNo());
    }

    @Test
    void docNameBoostPrefersMatchingRegulation()
    {
        List<CitationVO> citations = retriever.search("劳动合同法 合同", 4);

        assertFalse(citations.isEmpty());
        assertEquals(LABOR_TITLE, citations.get(0).getDocTitle());
    }

    @Test
    void disabledDocChunksAreNotRecalled()
    {
        List<CitationVO> citations = retriever.search("已废止法规中的劳动者特别规定", 10);

        assertTrue(citations.stream().noneMatch(citation -> citation.getDocId() == 3L));
    }

    @Test
    void colloquialQueryMatchesLeaseChapter()
    {
        List<CitationVO> citations = retriever.search("房东不退押金", 4);

        assertFalse(citations.isEmpty());
        assertEquals(2L, citations.get(0).getDocId());
    }

    @Test
    void minScoreFiltersWeakMatches()
    {
        when(properties.getMinScore()).thenReturn(1000.0);
        List<CitationVO> citations = retriever.search("租赁合同", 4);
        assertTrue(citations.isEmpty());
    }

    @Test
    void promptSectionRespectsBudgetAndContainsHeader()
    {
        List<CitationVO> citations = retriever.search("劳动合同法第四十七条", 4);
        String section = retriever.buildPromptSection(citations, 4000);

        assertTrue(section.startsWith("【参考法条】"));
        assertTrue(section.contains("《中华人民共和国劳动合同法》第四十七条"));
        assertTrue(section.length() <= 4000);

        String tiny = retriever.buildPromptSection(citations, 120);
        assertTrue(tiny.length() <= 120);
    }

    private LawKnowledgeDoc doc(Long id, String title, String category)
    {
        LawKnowledgeDoc doc = new LawKnowledgeDoc();
        doc.setId(id);
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setStatus("0");
        return doc;
    }

    private LawKnowledgeChunk chunk(Long id, Long docId, String articleNo, String content)
    {
        LawKnowledgeChunk chunk = new LawKnowledgeChunk();
        chunk.setId(id);
        chunk.setDocId(docId);
        chunk.setArticleNo(articleNo);
        chunk.setContent(content);
        chunk.setKeywords("");
        return chunk;
    }
}
