package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.retrieval.VectorSearchEngine.VectorDocument;
import com.ruoyi.lawyer.retrieval.VectorSearchEngine.VectorHit;

/**
 * InMemoryVectorSearchEngine 单元测试
 *
 * @author ruoyi
 */
class VectorSearchEngineTest
{
    private InMemoryVectorSearchEngine engine;

    @BeforeEach
    void setUp()
    {
        engine = new InMemoryVectorSearchEngine();
    }

    @Test
    void searchReturnsCosineTopKInDescendingOrder()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { 1.0f, 0.0f }),
                new VectorDocument(2L, 20L, "civil_lease", new float[] { 0.8f, 0.6f }),
                new VectorDocument(3L, 30L, "civil_loan", new float[] { 0.0f, 1.0f })));

        List<VectorHit> hits = engine.search(new float[] { 1.0f, 0.0f }, 2);

        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).chunkId());
        assertEquals(1.0, hits.get(0).score(), 1e-6);
        assertEquals(2L, hits.get(1).chunkId());
        assertEquals(0.8, hits.get(1).score(), 1e-6);
        assertEquals("in-memory-cosine", engine.engineName());
        assertEquals(3, engine.size());
        assertEquals(2, engine.dimensions());
        assertTrue(engine.isReady());
    }

    @Test
    void categoryFilterOnlyReturnsMatchingDocuments()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { 1.0f, 0.0f }),
                new VectorDocument(2L, 20L, "civil_lease", new float[] { 1.0f, 0.0f })));

        List<VectorHit> hits = engine.search(new float[] { 1.0f, 0.0f }, 10, "civil_lease");

        assertEquals(1, hits.size());
        assertEquals(2L, hits.get(0).chunkId());
    }

    @Test
    void dimensionMismatchReturnsEmpty()
    {
        engine.rebuild(List.of(new VectorDocument(1L, 10L, "labor", new float[] { 1.0f, 0.0f })));

        assertTrue(engine.search(new float[] { 1.0f, 0.0f, 0.0f }, 5).isEmpty());
    }

    @Test
    void emptyRebuildMakesEngineNotReady()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { 1.0f, 0.0f })));
        assertTrue(engine.isReady());

        engine.rebuild(List.of());

        assertFalse(engine.isReady());
        assertEquals(0, engine.size());
        assertEquals(0, engine.dimensions());
        assertTrue(engine.search(new float[] { 1.0f, 0.0f }, 5).isEmpty());
    }

    @Test
    void nonPositiveSimilarityIsFiltered()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { -1.0f, 0.0f })));

        assertTrue(engine.search(new float[] { 1.0f, 0.0f }, 5).isEmpty());
    }

    @Test
    void libraryVectorsAreNormalizedOnRebuild()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { 3.0f, 4.0f })));

        List<VectorHit> hits = engine.search(new float[] { 1.0f, 0.0f }, 5);

        assertEquals(1, hits.size());
        assertEquals(0.6, hits.get(0).score(), 1e-6);
    }

    @Test
    void topKLessThanOrEqualZeroReturnsEmpty()
    {
        engine.rebuild(List.of(
                new VectorDocument(1L, 10L, "labor", new float[] { 1.0f, 0.0f })));

        assertTrue(engine.search(new float[] { 1.0f, 0.0f }, 0).isEmpty());
    }
}
