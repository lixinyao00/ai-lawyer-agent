package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * 内置语料 + 检索器联合测试：验证验收清单中的两条检索样例
 *
 * @author ruoyi
 */
class KnowledgeRetrieverSeedCorpusTest
{
    private static final Map<String, String> SEEDS = Map.of(
            "labor_contract_law.txt", "labor",
            "civil_code_contract.txt", "civil_contract",
            "civil_code_lease.txt", "civil_lease",
            "civil_code_loan.txt", "civil_loan",
            "civil_code_tort.txt", "civil_tort");

    private KnowledgeRetriever retriever;

    private long leaseDocId;

    @BeforeEach
    void setUp() throws Exception
    {
        LawKnowledgeDocMapper docMapper = mock(LawKnowledgeDocMapper.class);
        LawKnowledgeChunkMapper chunkMapper = mock(LawKnowledgeChunkMapper.class);
        LawyerKnowledgeProperties properties = mock(LawyerKnowledgeProperties.class);
        lenient().when(properties.getTopK()).thenReturn(4);
        lenient().when(properties.getMinScore()).thenReturn(1.0);
        lenient().when(properties.getSnippetMaxChars()).thenReturn(200);
        lenient().when(properties.getPromptMaxChars()).thenReturn(4000);
        lenient().when(properties.getRefreshMinutes()).thenReturn(10L);

        List<LawKnowledgeDoc> docs = new ArrayList<>();
        List<LawKnowledgeChunk> chunks = new ArrayList<>();
        long docId = 1;
        long chunkId = 1;
        for (Map.Entry<String, String> seed : SEEDS.entrySet())
        {
            SeedFile file = readSeed(seed.getKey(), seed.getValue());
            LawKnowledgeDoc doc = new LawKnowledgeDoc();
            doc.setId(docId);
            doc.setTitle(file.title);
            doc.setCategory(file.category);
            doc.setStatus("0");
            docs.add(doc);
            if ("civil_lease".equals(file.category))
            {
                leaseDocId = docId;
            }
            for (ArticleSplitter.Chunk piece : file.chunks)
            {
                LawKnowledgeChunk chunk = new LawKnowledgeChunk();
                chunk.setId(chunkId++);
                chunk.setDocId(docId);
                chunk.setArticleNo(piece.getArticleNo());
                chunk.setContent(piece.getContent());
                chunk.setKeywords(piece.getKeywords());
                chunks.add(chunk);
            }
            docId++;
        }
        when(docMapper.selectEnabledDocs()).thenReturn(docs);
        when(chunkMapper.selectChunksByDocIds(anyList())).thenReturn(chunks);
        retriever = new KnowledgeRetriever(docMapper, chunkMapper, properties);
    }

    @Test
    void laborArticleQueryHitsExactArticle()
    {
        List<CitationVO> citations = retriever.search("劳动合同法第四十七条", 4);

        assertFalse(citations.isEmpty());
        assertEquals("中华人民共和国劳动合同法", citations.get(0).getDocTitle());
        assertEquals("第四十七条", citations.get(0).getArticleNo());
    }

    @Test
    void depositQueryHitsLeaseChapter()
    {
        List<CitationVO> citations = retriever.search("房东不退押金", 4);

        assertFalse(citations.isEmpty());
        assertTrue(citations.stream().anyMatch(citation -> citation.getDocId() == leaseDocId));
    }

    private SeedFile readSeed(String fileName, String category) throws Exception
    {
        String text;
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("knowledge/seed/" + fileName))
        {
            if (inputStream == null)
            {
                throw new IllegalStateException("缺少内置语料: " + fileName);
            }
            text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        text = text.replace("\r\n", "\n");
        int separator = text.indexOf("\n---");
        String body = separator >= 0 ? text.substring(separator + 4) : text;
        String title = "中华人民共和国民法典";
        if ("labor".equals(category))
        {
            title = "中华人民共和国劳动合同法";
        }
        SeedFile file = new SeedFile();
        file.title = title;
        file.category = category;
        file.chunks = ArticleSplitter.split(body);
        return file;
    }

    private static final class SeedFile
    {
        private String title;

        private String category;

        private List<ArticleSplitter.Chunk> chunks;
    }
}
