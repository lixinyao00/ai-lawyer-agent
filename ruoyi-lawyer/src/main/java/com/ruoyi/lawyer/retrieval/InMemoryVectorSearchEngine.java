package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 纯内存余弦相似度向量检索引擎
 *
 * <p>内部持有一个 volatile 不可变快照（{@link VectorIndex}），无锁并发读、原子替换写：
 * 重建时一次性构建数组并替换引用，查询线程永远看到完整的旧快照或新快照。</p>
 *
 * <p>外部向量库切换契约见 {@link VectorSearchEngine}。当前实现无任何外部中间件依赖，
 * 保证离线可构建与可单测。</p>
 *
 * @author ruoyi
 */
@Component
public class InMemoryVectorSearchEngine implements VectorSearchEngine
{
    private static final Logger log = LoggerFactory.getLogger(InMemoryVectorSearchEngine.class);

    /** 当前不可变索引快照 */
    private volatile VectorIndex index = VectorIndex.empty();

    @Override
    public void rebuild(List<VectorDocument> documents)
    {
        if (documents == null || documents.isEmpty())
        {
            index = VectorIndex.empty();
            return;
        }
        int dimensions = resolveDimensions(documents);
        if (dimensions <= 0)
        {
            index = VectorIndex.empty();
            return;
        }
        List<Long> chunkIds = new ArrayList<>(documents.size());
        List<Long> docIds = new ArrayList<>(documents.size());
        List<String> categories = new ArrayList<>(documents.size());
        List<float[]> vectors = new ArrayList<>(documents.size());
        for (VectorDocument document : documents)
        {
            if (document == null || document.chunkId() == null || document.vector() == null
                    || document.vector().length != dimensions)
            {
                continue;
            }
            float[] normalized = normalizeCopy(document.vector());
            chunkIds.add(document.chunkId());
            docIds.add(document.docId());
            categories.add(document.category());
            vectors.add(normalized);
        }
        index = new VectorIndex(toLongArray(chunkIds), docIds.toArray(new Long[0]),
                categories.toArray(new String[0]), vectors.toArray(new float[0][]), dimensions);
        log.info("内存向量索引已重建, engine={}, size={}, dimensions={}", engineName(), index.size, dimensions);
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int topK)
    {
        return search(queryVector, topK, null);
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int topK, String category)
    {
        VectorIndex current = index;
        if (queryVector == null || topK <= 0 || !current.ready || queryVector.length != current.dimensions)
        {
            return Collections.emptyList();
        }
        float[] query = normalizeCopy(queryVector);
        boolean filterCategory = category != null && !category.isBlank();
        List<VectorHit> hits = new ArrayList<>();
        for (int i = 0; i < current.size; i++)
        {
            if (filterCategory && !category.equals(current.categories[i]))
            {
                continue;
            }
            double score = cosine(query, current.vectors[i]);
            if (score <= 0.0)
            {
                continue;
            }
            hits.add(new VectorHit(current.chunkIds[i], score));
        }
        hits.sort((left, right) -> {
            int compare = Double.compare(right.score(), left.score());
            if (compare != 0)
            {
                return compare;
            }
            return Long.compare(left.chunkId(), right.chunkId());
        });
        if (hits.size() > topK)
        {
            return new ArrayList<>(hits.subList(0, topK));
        }
        return hits;
    }

    @Override
    public boolean isReady()
    {
        return index.ready;
    }

    @Override
    public int size()
    {
        return index.size;
    }

    @Override
    public int dimensions()
    {
        return index.ready ? index.dimensions : 0;
    }

    @Override
    public String engineName()
    {
        return "in-memory-cosine";
    }

    private int resolveDimensions(List<VectorDocument> documents)
    {
        for (VectorDocument document : documents)
        {
            if (document != null && document.vector() != null && document.vector().length > 0)
            {
                return document.vector().length;
            }
        }
        return 0;
    }

    private double cosine(float[] left, float[] right)
    {
        double sum = 0.0;
        for (int i = 0; i < left.length; i++)
        {
            sum += (double) left[i] * right[i];
        }
        return sum;
    }

    private float[] normalizeCopy(float[] vector)
    {
        float[] copy = vector.clone();
        double sum = 0.0;
        for (float value : copy)
        {
            sum += (double) value * value;
        }
        if (sum <= 0.0)
        {
            return copy;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < copy.length; i++)
        {
            copy[i] = (float) (copy[i] / norm);
        }
        return copy;
    }

    private long[] toLongArray(List<Long> values)
    {
        long[] array = new long[values.size()];
        for (int i = 0; i < values.size(); i++)
        {
            array[i] = values.get(i);
        }
        return array;
    }

    /**
     * 不可变索引快照
     */
    private static final class VectorIndex
    {
        private final long[] chunkIds;

        private final Long[] docIds;

        private final String[] categories;

        private final float[][] vectors;

        private final int dimensions;

        private final int size;

        private final boolean ready;

        private VectorIndex(long[] chunkIds, Long[] docIds, String[] categories, float[][] vectors, int dimensions)
        {
            this.chunkIds = chunkIds;
            this.docIds = docIds;
            this.categories = categories;
            this.vectors = vectors;
            this.dimensions = dimensions;
            this.size = chunkIds.length;
            this.ready = this.size > 0;
        }

        private static VectorIndex empty()
        {
            return new VectorIndex(new long[0], new Long[0], new String[0], new float[0][], 0);
        }
    }
}
