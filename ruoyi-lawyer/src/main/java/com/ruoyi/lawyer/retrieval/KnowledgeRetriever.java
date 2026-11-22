package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.ruoyi.lawyer.client.AiEmbeddingClient;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.retrieval.VectorSearchEngine.VectorDocument;
import com.ruoyi.lawyer.retrieval.VectorSearchEngine.VectorHit;
import com.ruoyi.lawyer.vo.CitationVO;
import tools.jackson.databind.ObjectMapper;

/**
 * 轻量法律知识库检索器
 *
 * <p>首次查询时从数据库加载全部启用文档的切片，构建进程内不可变快照。
 * 默认沿用 V1.0 的 BM25（bigram）+ 条号精确匹配 + 法规名匹配打分；在注入
 * {@link VectorSearchEngine} 与 {@link AiEmbeddingClient} 后支持
 * {@code bm25} / {@code vector} / {@code hybrid_rrf} 三种检索模式。</p>
 *
 * <p><b>向后兼容：</b>保留原三参构造器，此时向量引擎与 Embedding 客户端为 {@code null}，
 * 自动回退纯 BM25，行为与 V1.0 完全一致。</p>
 *
 * @author ruoyi
 */
@Service
public class KnowledgeRetriever
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetriever.class);

    /** BM25 参数 */
    private static final double K1 = 1.2;

    private static final double B = 0.75;

    /** 条号精确匹配加分 */
    private static final double ARTICLE_MATCH_BOOST = 5.0;

    /** 法规名匹配加分 */
    private static final double DOC_NAME_BOOST = 2.0;

    /** RRF 精确法条号直通权重 */
    private static final double EXACT_ARTICLE_RRF_BOOST = 1.0;

    /** RRF 默认平滑常数 */
    private static final int DEFAULT_RRF_K = 60;

    /** 向量路最低余弦相似度门槛 */
    private static final double VECTOR_MIN_SCORE = 0.05;

    /** 单向量路极低分噪音过滤阈值 */
    private static final double VECTOR_NOISE_SCORE = 0.12;

    /** 检索调试接口最多返回条数 */
    private static final int MAX_TOP_K = 10;

    /** 合法检索模式 */
    private static final String MODE_BM25 = "bm25";

    private static final String MODE_VECTOR = "vector";

    private static final String MODE_HYBRID = "hybrid_rrf";

    /** 提示词引用段标题 */
    private static final String PROMPT_HEADER = "【参考法条】以下条文由系统从知识库检索，"
            + "仅在与问题相关时引用，引用时写明《法规名》第X条；无关则忽略，不得编造条文。\n";

    /** 口语化法律词汇到检索词的轻量扩展（不改变对用户展示的引用内容） */
    private static final Map<String, String> QUERY_ALIASES = Map.of(
            "房东", "出租人",
            "租客", "承租人",
            "房客", "承租人",
            "押金", "租金 返还 租赁物",
            "退租", "解除合同 返还租赁物",
            "辞退", "解除劳动合同",
            "赔偿", "赔偿金 经济补偿");

    private final LawKnowledgeDocMapper docMapper;

    private final LawKnowledgeChunkMapper chunkMapper;

    private final LawyerKnowledgeProperties properties;

    /** 向量检索引擎；三参构造器创建时为 null */
    private final VectorSearchEngine vectorSearchEngine;

    /** Embedding 客户端；三参构造器创建时为 null */
    private final AiEmbeddingClient aiEmbeddingClient;

    /** Jackson 序列化器（保留扩展位，三参构造器创建时为 null） */
    private final ObjectMapper objectMapper;

    /** 当前内存快照，invalidate 置空后下次查询重建 */
    private volatile Snapshot snapshot;

    /**
     * V1.0 兼容构造器：不启用向量检索，保持纯 BM25 行为
     *
     * @param docMapper 文档数据层
     * @param chunkMapper 切片数据层
     * @param properties 知识库配置
     */
    public KnowledgeRetriever(LawKnowledgeDocMapper docMapper, LawKnowledgeChunkMapper chunkMapper,
            LawyerKnowledgeProperties properties)
    {
        this(docMapper, chunkMapper, properties, null, null, null);
    }

    /**
     * V2.0 构造器：可选注入向量引擎与 Embedding 客户端
     *
     * @param docMapper 文档数据层
     * @param chunkMapper 切片数据层
     * @param properties 知识库配置
     * @param vectorSearchEngine 向量引擎，可为 null
     * @param aiEmbeddingClient Embedding 客户端，可为 null
     * @param objectMapper Jackson 序列化器，可为 null
     */
    @Autowired
    public KnowledgeRetriever(LawKnowledgeDocMapper docMapper, LawKnowledgeChunkMapper chunkMapper,
            LawyerKnowledgeProperties properties,
            @Autowired(required = false) VectorSearchEngine vectorSearchEngine,
            @Autowired(required = false) AiEmbeddingClient aiEmbeddingClient,
            @Autowired(required = false) ObjectMapper objectMapper)
    {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.properties = properties;
        this.vectorSearchEngine = vectorSearchEngine;
        this.aiEmbeddingClient = aiEmbeddingClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 检索法条（使用配置默认模式）
     *
     * @param query 查询文本
     * @param topK 返回条数，为空时使用配置默认值
     * @return 按相关度倒序的引用列表
     */
    public List<CitationVO> search(String query, Integer topK)
    {
        return search(query, topK, null, null);
    }

    /**
     * 指定检索模式
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @param mode bm25 / vector / hybrid_rrf，空值使用配置默认模式
     * @return 引用列表
     */
    public List<CitationVO> search(String query, Integer topK, String mode)
    {
        return search(query, topK, mode, null);
    }

    /**
     * 指定检索模式与分类
     *
     * @param query 查询文本
     * @param topK 返回条数
     * @param mode bm25 / vector / hybrid_rrf，空值使用配置默认模式
     * @param category 法规分类，空值表示不过滤
     * @return 引用列表
     */
    public List<CitationVO> search(String query, Integer topK, String mode, String category)
    {
        if (query == null || query.isBlank())
        {
            return new ArrayList<>();
        }
        Snapshot current;
        try
        {
            current = snapshot();
        }
        catch (Exception e)
        {
            log.warn("知识库索引加载失败: {}", e.getMessage());
            return new ArrayList<>();
        }
        if (current.chunks.isEmpty())
        {
            return new ArrayList<>();
        }

        int limit = resolveTopK(topK);
        double minScore = properties.getMinScore();
        int snippetMaxChars = properties.getSnippetMaxChars() <= 0 ? 200 : properties.getSnippetMaxChars();
        String resolvedMode = resolveMode(mode);

        // 无向量能力：无条件回退 V1.0 纯 BM25
        if (vectorSearchEngine == null || aiEmbeddingClient == null)
        {
            return bm25Citations(current, query, limit, category, minScore, snippetMaxChars);
        }
        if (MODE_BM25.equals(resolvedMode))
        {
            return bm25Citations(current, query, limit, category, minScore, snippetMaxChars);
        }
        if (MODE_VECTOR.equals(resolvedMode))
        {
            List<CitationVO> vector = vectorCitations(current, query, limit, category, snippetMaxChars);
            if (vector != null)
            {
                return vector;
            }
            return bm25Citations(current, query, limit, category, minScore, snippetMaxChars);
        }
        return hybridCitations(current, query, limit, category, minScore, snippetMaxChars);
    }

    /**
     * 组装注入系统提示词的参考法条段，总长不超过 prompt-max-chars
     *
     * @param citations 检索结果
     * @return 提示词片段，无命中返回空串
     */
    public String buildPromptSection(List<CitationVO> citations)
    {
        int maxChars = properties.getPromptMaxChars() <= 0 ? 4000 : properties.getPromptMaxChars();
        return buildPromptSection(citations, maxChars);
    }

    /**
     * 组装注入系统提示词的参考法条段
     *
     * @param citations 检索结果
     * @param maxChars 本段最大字符数
     * @return 提示词片段，无命中返回空串
     */
    public String buildPromptSection(List<CitationVO> citations, int maxChars)
    {
        if (citations == null || citations.isEmpty() || maxChars <= PROMPT_HEADER.length())
        {
            return "";
        }
        Snapshot current = snapshot;
        StringBuilder builder = new StringBuilder(PROMPT_HEADER);
        int index = 1;
        for (CitationVO citation : citations)
        {
            String prefix = "[" + index + "]《" + citation.getDocTitle() + "》"
                    + (citation.getArticleNo() == null ? "" : citation.getArticleNo()) + "：";
            String body = resolveContent(current, citation);
            String line = prefix + body + "\n";
            if (builder.length() + line.length() <= maxChars)
            {
                builder.append(line);
                index++;
                continue;
            }
            int remaining = maxChars - builder.length() - prefix.length();
            if (remaining > 12)
            {
                builder.append(prefix);
                builder.append(body, 0, Math.min(remaining - 2, body.length()));
                builder.append("…\n");
            }
            break;
        }
        return builder.toString();
    }

    /**
     * 标记内存索引失效，下次查询重建
     */
    public void invalidate()
    {
        snapshot = null;
    }

    // ------------------------------------------------------------------
    // BM25 路（V1.0 逻辑原样保留）
    // ------------------------------------------------------------------

    private List<CitationVO> bm25Citations(Snapshot current, String query, int limit, String category,
            double minScore, int snippetMaxChars)
    {
        QueryContext context = analyzeQuery(current, query);
        List<ScoredChunk> candidates = bm25Candidates(current, context, category, minScore);
        List<CitationVO> citations = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int rank = 0;
        for (ScoredChunk candidate : candidates)
        {
            if (citations.size() >= limit)
            {
                break;
            }
            if (!seen.add(candidate.chunk.id))
            {
                continue;
            }
            rank++;
            CitationVO citation = toCitation(candidate.chunk, snippetMaxChars);
            citation.setBm25Rank(rank);
            citation.setBm25Score(round(candidate.score, 2));
            citation.setRetrievalMode(MODE_BM25);
            citation.setScore(round(candidate.score, 2));
            citations.add(citation);
        }
        return citations;
    }

    /**
     * BM25 候选打分（含条号精确匹配、法规名加权与分类过滤），按分数倒序
     */
    private List<ScoredChunk> bm25Candidates(Snapshot current, QueryContext context, String category,
            double minScore)
    {
        Map<Integer, Double> scores = new HashMap<>();
        int chunkCount = current.chunks.size();
        for (String term : context.queryTerms)
        {
            List<int[]> posting = current.postings.get(term);
            if (posting == null)
            {
                continue;
            }
            int df = posting.size();
            double idf = Math.log(1.0 + (chunkCount - df + 0.5) / (df + 0.5));
            for (int[] item : posting)
            {
                int index = item[0];
                int tf = item[1];
                IndexedChunk chunk = current.chunks.get(index);
                double denominator = tf + K1 * (1 - B + B * chunk.length / current.avgLength);
                double score = idf * tf * (K1 + 1) / denominator;
                scores.merge(index, score, Double::sum);
            }
        }

        List<ScoredChunk> candidates = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : scores.entrySet())
        {
            IndexedChunk chunk = current.chunks.get(entry.getKey());
            if (!matchesCategory(chunk, category))
            {
                continue;
            }
            double score = entry.getValue();
            if (context.matchesExactArticle(chunk))
            {
                score += ARTICLE_MATCH_BOOST;
            }
            if (context.matchesDocName(chunk))
            {
                score += DOC_NAME_BOOST;
            }
            if (score >= minScore)
            {
                candidates.add(new ScoredChunk(chunk, score));
            }
        }
        // 条号精确命中的条文即使 BM25 无候选词，也应参与打分
        for (IndexedChunk chunk : current.chunks)
        {
            if (!matchesCategory(chunk, category))
            {
                continue;
            }
            if (context.matchesExactArticle(chunk) && !scores.containsKey(chunk.index))
            {
                double score = ARTICLE_MATCH_BOOST;
                if (context.matchesDocName(chunk))
                {
                    score += DOC_NAME_BOOST;
                }
                if (score >= minScore)
                {
                    candidates.add(new ScoredChunk(chunk, score));
                }
            }
        }
        candidates.sort((left, right) -> {
            int compare = Double.compare(right.score, left.score);
            if (compare != 0)
            {
                return compare;
            }
            return Long.compare(left.chunk.id, right.chunk.id);
        });
        return candidates;
    }

    // ------------------------------------------------------------------
    // Vector 路
    // ------------------------------------------------------------------

    /**
     * 纯向量检索；引擎未就绪或查询向量为空时返回 null 触发 BM25 回退
     */
    private List<CitationVO> vectorCitations(Snapshot current, String query, int limit, String category,
            int snippetMaxChars)
    {
        float[] queryVector = embedQuery(query);
        if (queryVector == null || !vectorSearchEngine.isReady()
                || queryVector.length != vectorSearchEngine.dimensions())
        {
            return null;
        }
        int candidateDepth = Math.max(limit * 4, 20);
        List<VectorHit> hits = safeVectorSearch(queryVector, candidateDepth, category);
        List<VectorHit> filtered = new ArrayList<>();
        for (VectorHit hit : hits)
        {
            if (hit != null && hit.score() >= VECTOR_MIN_SCORE && current.byChunkId.containsKey(hit.chunkId()))
            {
                filtered.add(hit);
            }
        }
        if (filtered.isEmpty())
        {
            return null;
        }
        List<CitationVO> citations = new ArrayList<>();
        int rank = 0;
        for (VectorHit hit : filtered)
        {
            if (citations.size() >= limit)
            {
                break;
            }
            IndexedChunk chunk = current.byChunkId.get(hit.chunkId());
            rank++;
            CitationVO citation = toCitation(chunk, snippetMaxChars);
            citation.setVectorRank(rank);
            citation.setVectorScore(round(hit.score(), 4));
            citation.setRetrievalMode(MODE_VECTOR);
            citation.setScore(round(hit.score(), 4));
            citations.add(citation);
        }
        return citations;
    }

    // ------------------------------------------------------------------
    // Hybrid RRF 融合
    // ------------------------------------------------------------------

    private List<CitationVO> hybridCitations(Snapshot current, String query, int limit, String category,
            double minScore, int snippetMaxChars)
    {
        QueryContext context = analyzeQuery(current, query);
        int candidateDepth = Math.max(limit * 4, 20);
        int rrfK = properties.getRrfK() > 0 ? properties.getRrfK() : DEFAULT_RRF_K;

        // BM25 路
        List<ScoredChunk> bm25Candidates = bm25Candidates(current, context, category, minScore);
        Map<Long, Integer> bm25Ranks = new LinkedHashMap<>();
        Map<Long, Double> bm25Scores = new LinkedHashMap<>();
        int bm25Count = 0;
        for (ScoredChunk candidate : bm25Candidates)
        {
            if (bm25Count >= candidateDepth)
            {
                break;
            }
            bm25Count++;
            bm25Ranks.put(candidate.chunk.id, bm25Count);
            bm25Scores.put(candidate.chunk.id, candidate.score);
        }

        // Vector 路
        float[] queryVector = embedQuery(query);
        boolean vectorReady = queryVector != null && vectorSearchEngine.isReady()
                && queryVector.length == vectorSearchEngine.dimensions();
        if (!vectorReady)
        {
            // 向量不可用：无缝降级纯 BM25
            return bm25Citations(current, query, limit, category, minScore, snippetMaxChars);
        }
        List<VectorHit> hits = safeVectorSearch(queryVector, candidateDepth, category);
        Map<Long, Integer> vectorRanks = new LinkedHashMap<>();
        Map<Long, Double> vectorScores = new LinkedHashMap<>();
        int vectorCount = 0;
        for (VectorHit hit : hits)
        {
            if (hit == null || hit.score() < VECTOR_MIN_SCORE || !current.byChunkId.containsKey(hit.chunkId()))
            {
                continue;
            }
            vectorCount++;
            vectorRanks.put(hit.chunkId(), vectorCount);
            vectorScores.put(hit.chunkId(), hit.score());
        }

        // 融合
        Map<Long, FusionItem> fused = new LinkedHashMap<>();
        for (Long chunkId : bm25Ranks.keySet())
        {
            FusionItem item = fused.computeIfAbsent(chunkId, id -> new FusionItem(current.byChunkId.get(id)));
            item.bm25Rank = bm25Ranks.get(chunkId);
            item.bm25Score = bm25Scores.get(chunkId);
        }
        for (Long chunkId : vectorRanks.keySet())
        {
            FusionItem item = fused.computeIfAbsent(chunkId, id -> new FusionItem(current.byChunkId.get(id)));
            item.vectorRank = vectorRanks.get(chunkId);
            item.vectorScore = vectorScores.get(chunkId);
        }

        List<FusionItem> result = new ArrayList<>();
        for (FusionItem item : fused.values())
        {
            if (item.chunk == null)
            {
                continue;
            }
            // 噪音过滤：BM25 未命中且向量分极低
            if (item.bm25Rank == null && (item.vectorScore == null || item.vectorScore < VECTOR_NOISE_SCORE))
            {
                continue;
            }
            double rrf = 0.0;
            if (item.bm25Rank != null)
            {
                rrf += 1.0 / (rrfK + item.bm25Rank);
            }
            if (item.vectorRank != null)
            {
                rrf += 1.0 / (rrfK + item.vectorRank);
            }
            if (context.matchesExactArticle(item.chunk))
            {
                rrf += EXACT_ARTICLE_RRF_BOOST;
            }
            item.rrfScore = rrf;
            result.add(item);
        }
        result.sort((left, right) -> {
            int compare = Double.compare(right.rrfScore, left.rrfScore);
            if (compare != 0)
            {
                return compare;
            }
            compare = Double.compare(safeScore(right.bm25Score), safeScore(left.bm25Score));
            if (compare != 0)
            {
                return compare;
            }
            return Long.compare(left.chunk.id, right.chunk.id);
        });

        List<CitationVO> citations = new ArrayList<>();
        for (FusionItem item : result)
        {
            if (citations.size() >= limit)
            {
                break;
            }
            CitationVO citation = toCitation(item.chunk, snippetMaxChars);
            citation.setBm25Rank(item.bm25Rank);
            citation.setVectorRank(item.vectorRank);
            citation.setBm25Score(item.bm25Score == null ? null : round(item.bm25Score, 2));
            citation.setVectorScore(item.vectorScore == null ? null : round(item.vectorScore, 4));
            citation.setRrfScore(round(item.rrfScore, 4));
            citation.setRetrievalMode(MODE_HYBRID);
            citation.setScore(round(item.rrfScore, 4));
            citations.add(citation);
        }
        return citations;
    }

    // ------------------------------------------------------------------
    // 查询分析 / 工具方法
    // ------------------------------------------------------------------

    private QueryContext analyzeQuery(Snapshot current, String query)
    {
        Set<String> queryTerms = new LinkedHashSet<>(LegalTokenizer.tokenize(expandQuery(query)));
        Set<String> queryArticles = new HashSet<>(ChineseNumberUtils.extractArticleNos(query));
        // 条号由精确匹配加分处理，不参与 BM25，避免交叉引用干扰排序
        queryTerms.removeAll(queryArticles);
        boolean queryHasDocName = false;
        for (DocInfo doc : current.docs.values())
        {
            if (!doc.coreName.isEmpty() && query.contains(doc.coreName))
            {
                queryHasDocName = true;
                break;
            }
        }
        return new QueryContext(query, queryTerms, queryArticles, queryHasDocName);
    }

    private String resolveMode(String mode)
    {
        if (MODE_BM25.equals(mode) || MODE_VECTOR.equals(mode) || MODE_HYBRID.equals(mode))
        {
            return mode;
        }
        String configured = properties.getMode();
        if (MODE_BM25.equals(configured) || MODE_VECTOR.equals(configured) || MODE_HYBRID.equals(configured))
        {
            return configured;
        }
        return MODE_HYBRID;
    }

    private boolean matchesCategory(IndexedChunk chunk, String category)
    {
        return category == null || category.isBlank() || category.equals(chunk.category);
    }

    private float[] embedQuery(String query)
    {
        if (aiEmbeddingClient == null)
        {
            return null;
        }
        try
        {
            return aiEmbeddingClient.embed(query);
        }
        catch (Exception e)
        {
            log.warn("查询向量生成失败，回退 BM25: {}", e.getMessage());
            return null;
        }
    }

    private List<VectorHit> safeVectorSearch(float[] queryVector, int topK, String category)
    {
        try
        {
            List<VectorHit> hits = vectorSearchEngine.search(queryVector, topK, category);
            return hits == null ? Collections.emptyList() : hits;
        }
        catch (Exception e)
        {
            log.warn("向量检索失败，回退 BM25: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private double safeScore(Double value)
    {
        return value == null ? 0.0 : value;
    }

    private int resolveTopK(Integer topK)
    {
        int configured = properties.getTopK() <= 0 ? 4 : properties.getTopK();
        int value = topK == null || topK <= 0 ? configured : topK;
        return Math.min(value, MAX_TOP_K);
    }

    /**
     * 口语化法律词汇扩展：仅用于 BM25 词项，不影响条号匹配与对外引用
     */
    private String expandQuery(String query)
    {
        StringBuilder builder = new StringBuilder(query);
        for (Map.Entry<String, String> entry : QUERY_ALIASES.entrySet())
        {
            if (query.contains(entry.getKey()))
            {
                builder.append(' ').append(entry.getValue());
            }
        }
        return builder.toString();
    }

    private CitationVO toCitation(IndexedChunk chunk, int snippetMaxChars)
    {
        CitationVO citation = new CitationVO();
        citation.setChunkId(chunk.id);
        citation.setDocId(chunk.docId);
        citation.setDocTitle(chunk.docTitle);
        citation.setArticleNo(chunk.articleNo);
        citation.setSnippet(snippet(chunk.content, snippetMaxChars));
        return citation;
    }

    private double round(double value, int scale)
    {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private String snippet(String content, int maxChars)
    {
        String text = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        if (text.length() <= maxChars)
        {
            return text;
        }
        return text.substring(0, maxChars) + "…";
    }

    private String resolveContent(Snapshot current, CitationVO citation)
    {
        if (current != null && citation.getChunkId() != null)
        {
            IndexedChunk chunk = current.byChunkId.get(citation.getChunkId());
            if (chunk != null && chunk.content != null && !chunk.content.isBlank())
            {
                return chunk.content.replaceAll("\\s+", " ").trim();
            }
        }
        return citation.getSnippet() == null ? "" : citation.getSnippet();
    }

    private Snapshot snapshot()
    {
        Snapshot current = snapshot;
        long now = System.currentTimeMillis();
        long refreshMillis = Math.max(1, properties.getRefreshMinutes()) * 60_000L;
        if (current != null && now - current.builtAt < refreshMillis)
        {
            return current;
        }
        synchronized (this)
        {
            current = snapshot;
            if (current != null && now - current.builtAt < refreshMillis)
            {
                return current;
            }
            Snapshot built = build();
            snapshot = built;
            return built;
        }
    }

    private Snapshot build()
    {
        long builtAt = System.currentTimeMillis();
        List<LawKnowledgeDoc> docs = docMapper.selectEnabledDocs();
        if (docs == null || docs.isEmpty())
        {
            rebuildVectorIndex(Collections.emptyList());
            return Snapshot.empty(builtAt);
        }
        Map<Long, DocInfo> docMap = new HashMap<>();
        List<Long> docIds = new ArrayList<>();
        for (LawKnowledgeDoc doc : docs)
        {
            if (doc == null || doc.getId() == null)
            {
                continue;
            }
            DocInfo info = new DocInfo(doc.getId(), doc.getTitle(), LegalTokenizer.coreName(doc.getTitle()),
                    doc.getCategory());
            docMap.put(doc.getId(), info);
            docIds.add(doc.getId());
        }
        if (docIds.isEmpty())
        {
            rebuildVectorIndex(Collections.emptyList());
            return Snapshot.empty(builtAt);
        }

        List<LawKnowledgeChunk> rows = chunkMapper.selectChunksByDocIds(docIds);
        if (rows == null)
        {
            rows = Collections.emptyList();
        }
        List<IndexedChunk> chunks = new ArrayList<>();
        Map<Long, IndexedChunk> byChunkId = new HashMap<>();
        Map<String, List<int[]>> postings = new HashMap<>();
        List<String> vectorTexts = new ArrayList<>();
        List<float[]> chunkVectors = new ArrayList<>();
        long totalLength = 0;

        for (LawKnowledgeChunk row : rows)
        {
            DocInfo doc = docMap.get(row.getDocId());
            if (doc == null)
            {
                // 文档已停用或不存在：不参与检索
                continue;
            }
            String title = doc.title;
            String text = (row.getContent() == null ? "" : row.getContent()) + "\n"
                    + (row.getKeywords() == null ? "" : row.getKeywords());
            List<String> tokens = LegalTokenizer.tokenize(text, title);
            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens)
            {
                termFreq.merge(token, 1, Integer::sum);
            }
            int index = chunks.size();
            IndexedChunk chunk = new IndexedChunk(index, row.getId(), row.getDocId(), title, doc.coreName,
                    doc.category, row.getArticleNo(), row.getContent(), termFreq, tokens.size());
            chunks.add(chunk);
            byChunkId.put(chunk.id, chunk);
            totalLength += chunk.length;
            for (String term : termFreq.keySet())
            {
                postings.computeIfAbsent(term, key -> new ArrayList<>()).add(new int[] { index, termFreq.get(term) });
            }

            float[] vector = parseEmbedding(row.getEmbeddingJson());
            chunkVectors.add(vector);
            vectorTexts.add(text);
        }

        // 存量切片 embedding_json 为空时，内存临时编码补齐，兼容升级后尚未执行 rebuild-embeddings
        fillMissingVectors(chunkVectors, vectorTexts);
        List<VectorDocument> vectorDocs = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++)
        {
            float[] vector = i < chunkVectors.size() ? chunkVectors.get(i) : null;
            if (vector == null || vector.length == 0)
            {
                continue;
            }
            IndexedChunk chunk = chunks.get(i);
            vectorDocs.add(new VectorDocument(chunk.id, chunk.docId, chunk.category, vector));
        }
        rebuildVectorIndex(vectorDocs);

        double avgLength = chunks.isEmpty() ? 1.0 : Math.max(1.0, (double) totalLength / chunks.size());
        log.info("知识库内存索引已构建, docs={}, chunks={}, vectors={}, avgLength={}", docMap.size(),
                chunks.size(), vectorDocs.size(), avgLength);
        return new Snapshot(Collections.unmodifiableMap(docMap), Collections.unmodifiableList(chunks),
                Collections.unmodifiableMap(byChunkId), Collections.unmodifiableMap(postings), avgLength, builtAt);
    }

    private void fillMissingVectors(List<float[]> chunkVectors, List<String> texts)
    {
        if (aiEmbeddingClient == null)
        {
            return;
        }
        List<Integer> pendingIndexes = new ArrayList<>();
        List<String> pendingTexts = new ArrayList<>();
        for (int i = 0; i < chunkVectors.size(); i++)
        {
            float[] vector = chunkVectors.get(i);
            if (vector == null || vector.length == 0)
            {
                pendingIndexes.add(i);
                pendingTexts.add(texts.get(i));
            }
        }
        if (pendingTexts.isEmpty())
        {
            return;
        }
        try
        {
            List<float[]> encoded = aiEmbeddingClient.embedBatch(pendingTexts);
            for (int i = 0; i < pendingIndexes.size(); i++)
            {
                if (encoded != null && i < encoded.size())
                {
                    chunkVectors.set(pendingIndexes.get(i), encoded.get(i));
                }
            }
            log.info("存量切片向量内存补齐完成, pending={}", pendingTexts.size());
        }
        catch (Exception e)
        {
            log.warn("存量切片向量内存补齐失败，本次仅使用已有向量: {}", e.getMessage());
        }
    }

    private void rebuildVectorIndex(List<VectorDocument> vectorDocs)
    {
        if (vectorSearchEngine == null)
        {
            return;
        }
        try
        {
            vectorSearchEngine.rebuild(vectorDocs);
        }
        catch (Exception e)
        {
            log.warn("向量索引重建失败: {}", e.getMessage());
        }
    }

    private float[] parseEmbedding(String json)
    {
        if (json == null || json.isBlank())
        {
            return null;
        }
        try
        {
            JSONArray array = JSON.parseArray(json);
            if (array == null || array.isEmpty())
            {
                return null;
            }
            float[] vector = new float[array.size()];
            for (int i = 0; i < array.size(); i++)
            {
                vector[i] = array.getFloatValue(i);
            }
            return vector;
        }
        catch (Exception e)
        {
            log.warn("切片向量 JSON 解析失败，忽略该向量: {}", e.getMessage());
            return null;
        }
    }

    private static final class DocInfo
    {
        private final Long id;

        private final String title;

        private final String coreName;

        private final String category;

        private DocInfo(Long id, String title, String coreName, String category)
        {
            this.id = id;
            this.title = title == null ? "" : title;
            this.coreName = coreName == null ? "" : coreName;
            this.category = category == null ? "" : category;
        }
    }

    private static final class IndexedChunk
    {
        private final int index;

        private final long id;

        private final Long docId;

        private final String docTitle;

        private final String coreName;

        private final String category;

        private final String articleNo;

        private final String content;

        private final Map<String, Integer> termFreq;

        private final double length;

        private IndexedChunk(int index, Long id, Long docId, String docTitle, String coreName, String category,
                String articleNo, String content, Map<String, Integer> termFreq, int length)
        {
            this.index = index;
            this.id = id == null ? 0L : id;
            this.docId = docId;
            this.docTitle = docTitle;
            this.coreName = coreName;
            this.category = category;
            this.articleNo = articleNo;
            this.content = content;
            this.termFreq = termFreq;
            this.length = Math.max(1, length);
        }
    }

    private static final class ScoredChunk
    {
        private final IndexedChunk chunk;

        private final double score;

        private ScoredChunk(IndexedChunk chunk, double score)
        {
            this.chunk = chunk;
            this.score = score;
        }
    }

    private static final class FusionItem
    {
        private final IndexedChunk chunk;

        private Integer bm25Rank;

        private Integer vectorRank;

        private Double bm25Score;

        private Double vectorScore;

        private double rrfScore;

        private FusionItem(IndexedChunk chunk)
        {
            this.chunk = chunk;
        }
    }

    /**
     * 单次查询的规则分析结果
     */
    private static final class QueryContext
    {
        private final String query;

        private final Set<String> queryTerms;

        private final Set<String> queryArticles;

        private final boolean queryHasDocName;

        private QueryContext(String query, Set<String> queryTerms, Set<String> queryArticles,
                boolean queryHasDocName)
        {
            this.query = query;
            this.queryTerms = queryTerms;
            this.queryArticles = queryArticles;
            this.queryHasDocName = queryHasDocName;
        }

        private boolean matchesExactArticle(IndexedChunk chunk)
        {
            return chunk.articleNo != null && queryArticles.contains(chunk.articleNo)
                    && (!queryHasDocName || matchesDocName(chunk));
        }

        private boolean matchesDocName(IndexedChunk chunk)
        {
            return !chunk.coreName.isEmpty() && query.contains(chunk.coreName);
        }
    }

    private static final class Snapshot
    {
        private final Map<Long, DocInfo> docs;

        private final List<IndexedChunk> chunks;

        private final Map<Long, IndexedChunk> byChunkId;

        private final Map<String, List<int[]>> postings;

        private final double avgLength;

        private final long builtAt;

        private Snapshot(Map<Long, DocInfo> docs, List<IndexedChunk> chunks,
                Map<Long, IndexedChunk> byChunkId, Map<String, List<int[]>> postings, double avgLength, long builtAt)
        {
            this.docs = docs;
            this.chunks = chunks;
            this.byChunkId = byChunkId;
            this.postings = postings;
            this.avgLength = avgLength;
            this.builtAt = builtAt;
        }

        private static Snapshot empty(long builtAt)
        {
            return new Snapshot(Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap(),
                    Collections.emptyMap(), 1.0, builtAt);
        }
    }
}
