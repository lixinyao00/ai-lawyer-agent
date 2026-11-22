package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.client.AiEmbeddingClient;
import com.ruoyi.lawyer.client.EmbeddingStatus;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * KnowledgeRetriever 双路召回 + RRF 融合单元测试
 *
 * @author ruoyi
 */
class KnowledgeRetrieverHybridTest
{
    private static final String LABOR_TITLE = "中华人民共和国劳动合同法";

    private static final String CIVIL_TITLE = "中华人民共和国民法典";

    private LawKnowledgeDocMapper docMapper;

    private LawKnowledgeChunkMapper chunkMapper;

    private LawyerKnowledgeProperties properties;

    private InMemoryVectorSearchEngine engine;

    private AiEmbeddingClient embeddingClient;

    @BeforeEach
    void setUp()
    {
        docMapper = mock(LawKnowledgeDocMapper.class);
        chunkMapper = mock(LawKnowledgeChunkMapper.class);
        properties = new LawyerKnowledgeProperties();
        properties.setTopK(4);
        properties.setMinScore(1.0);
        properties.setSnippetMaxChars(200);
        properties.setPromptMaxChars(4000);
        properties.setRefreshMinutes(10L);
        properties.setMode("hybrid_rrf");
        properties.setRrfK(60);
        properties.setRewriteEnabled(true);

        LawKnowledgeDoc labor = doc(1L, LABOR_TITLE, "labor");
        LawKnowledgeDoc civil = doc(2L, CIVIL_TITLE, "civil_lease");
        when(docMapper.selectEnabledDocs()).thenReturn(List.of(labor, civil));

        List<LawKnowledgeChunk> chunks = new ArrayList<>();
        chunks.add(chunk(11L, 1L, "第四十七条",
                "经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准向劳动者支付。"
                        + "六个月以上不满一年的，按一年计算；不满六个月的，向劳动者支付半个月工资的经济补偿。"));
        chunks.add(chunk(12L, 1L, "第八十七条",
                "用人单位违反本法规定解除或者终止劳动合同的，应当依照本法第四十七条规定的经济补偿标准的二倍向劳动者支付赔偿金。"));
        chunks.add(chunk(13L, 2L, "第七百零三条",
                "租赁合同是出租人将租赁物交付承租人使用、收益，承租人支付租金的合同。"
                        + "承租人应当妥善保管租赁物，租赁期限届满，承租人应当返还租赁物。"));
        chunks.add(chunk(14L, 2L, "第七百一十六条",
                "承租人经出租人同意，可以将租赁物转租给第三人。承租人转租的，承租人与出租人之间的租赁合同继续有效。"));
        when(chunkMapper.selectChunksByDocIds(anyList())).thenReturn(chunks);

        engine = new InMemoryVectorSearchEngine();
        embeddingClient = new LocalEmbeddingClient();
    }

    @Test
    void legacyThreeArgConstructorKeepsPureBm25()
    {
        KnowledgeRetriever retriever = new KnowledgeRetriever(docMapper, chunkMapper, properties);

        List<CitationVO> citations = retriever.search("房东不退押金", 4);

        assertFalse(citations.isEmpty());
        assertEquals("bm25", citations.get(0).getRetrievalMode());
        assertNotNull(citations.get(0).getBm25Rank());
        assertNull(citations.get(0).getRrfScore());
        assertTrue(citations.stream().allMatch(citation -> "bm25".equals(citation.getRetrievalMode())));
    }

    @Test
    void nullCollaboratorsFallBackToBm25()
    {
        KnowledgeRetriever retriever = new KnowledgeRetriever(docMapper, chunkMapper, properties, null, null, null);

        List<CitationVO> citations = retriever.search("租赁合同", 4, "hybrid_rrf");

        assertFalse(citations.isEmpty());
        assertTrue(citations.stream().allMatch(citation -> "bm25".equals(citation.getRetrievalMode())));
    }

    @Test
    void vectorModeReturnsCosineRankAndScore()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("房东不退押金还扣着租金", 4, "vector");

        assertFalse(citations.isEmpty());
        assertEquals(2L, citations.get(0).getDocId());
        assertEquals("vector", citations.get(0).getRetrievalMode());
        assertEquals(1, citations.get(0).getVectorRank());
        assertNotNull(citations.get(0).getVectorScore());
        assertEquals(citations.get(0).getVectorScore(), citations.get(0).getScore(), 1e-9);
        assertTrue(citations.stream().allMatch(citation -> "vector".equals(citation.getRetrievalMode())));
    }

    @Test
    void hybridModeExposesRrfAndDualRanks()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("房东不退押金还扣着租金", 4, "hybrid_rrf");

        assertFalse(citations.isEmpty());
        assertEquals("hybrid_rrf", citations.get(0).getRetrievalMode());
        assertNotNull(citations.get(0).getRrfScore());
        assertTrue(citations.stream().anyMatch(citation -> citation.getBm25Rank() != null));
        assertTrue(citations.stream().anyMatch(citation -> citation.getVectorRank() != null));
    }

    @Test
    void exactArticleDirectPassKeepsTopOneInHybrid()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("劳动合同法第四十七条", 4, "hybrid_rrf");

        assertFalse(citations.isEmpty());
        assertEquals("第四十七条", citations.get(0).getArticleNo());
        assertTrue(citations.get(0).getRrfScore() >= 1.0);
    }

    @Test
    void bm25CategoryFilterOnlyReturnsRequestedCategory()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("合同 租赁", 4, "bm25", "civil_lease");

        assertFalse(citations.isEmpty());
        assertTrue(citations.stream().allMatch(citation -> citation.getDocId() == 2L));
    }

    @Test
    void vectorCategoryFilterOnlyReturnsRequestedCategory()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("劳动合同解除赔偿", 4, "vector", "labor");

        assertTrue(citations.stream().allMatch(citation -> citation.getDocId() == 1L));
    }

    @Test
    void illegalModeFallsBackToConfiguredHybrid()
    {
        KnowledgeRetriever retriever = retriever();

        List<CitationVO> citations = retriever.search("房东不退押金", 4, "unknown-mode");

        assertFalse(citations.isEmpty());
        assertEquals("hybrid_rrf", citations.get(0).getRetrievalMode());
    }

    @Test
    void vectorEngineIsRebuiltFromSnapshot()
    {
        KnowledgeRetriever retriever = retriever();

        retriever.search("押金", 4, "vector");

        assertTrue(engine.isReady());
        assertEquals(4, engine.size());
        assertEquals(256, engine.dimensions());
    }

    private KnowledgeRetriever retriever()
    {
        return new KnowledgeRetriever(docMapper, chunkMapper, properties, engine, embeddingClient, null);
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

    /**
     * 确定性本地伪向量客户端，模拟 403 降级场景
     */
    private static final class LocalEmbeddingClient implements AiEmbeddingClient
    {
        private final LocalSemanticEmbeddingEncoder encoder = new LocalSemanticEmbeddingEncoder(256);

        @Override
        public float[] embed(String text)
        {
            return encoder.encode(text);
        }

        @Override
        public List<float[]> embedBatch(List<String> texts)
        {
            return encoder.encodeBatch(texts);
        }

        @Override
        public String currentModel()
        {
            return encoder.modelName();
        }

        @Override
        public EmbeddingStatus getStatus()
        {
            return new EmbeddingStatus("local_fallback", encoder.modelName(), 256, true, false,
                    "HTTP_403:embeddings_disabled", "gateway disabled");
        }
    }
}
