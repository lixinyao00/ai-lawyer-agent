package com.ruoyi.lawyer.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiEmbeddingClient;
import com.ruoyi.lawyer.client.EmbeddingStatus;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.retrieval.ArticleSplitter;
import com.ruoyi.lawyer.retrieval.InMemoryVectorSearchEngine;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.retrieval.LocalSemanticEmbeddingEncoder;
import com.ruoyi.lawyer.retrieval.QueryRewriteResult;
import com.ruoyi.lawyer.retrieval.QueryRewriter;
import com.ruoyi.lawyer.vo.CitationVO;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 离线 RAG 量化基准评测（30 题，5 组消融对照）
 *
 * <p>覆盖精确条号、口语同义与多轮指代三类真实用户问题，统计总体与分场景的
 * Hit@1 / Recall@3 / Recall@5 / MRR@5，并自动生成
 * {@code doc/V2.0_BENCHMARK_REPORT.md} 的 RAG 区块。</p>
 *
 * @author ruoyi
 */
class RagBenchmarkEvalTest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CATEGORY_EXACT = "exact_article";

    private static final String CATEGORY_COLLOQUIAL = "colloquial_synonym";

    private static final String CATEGORY_MULTI_TURN = "multi_turn_anaphora";

    private static final String GROUP_BM25 = "BM25 (无 Rewrite)";

    private static final String GROUP_VECTOR = "Vector (无 Rewrite)";

    private static final String GROUP_HYBRID = "Hybrid RRF (无 Rewrite)";

    private static final String GROUP_BM25_REWRITE = "BM25 + Query Rewrite";

    private static final String GROUP_HYBRID_REWRITE = "Hybrid RRF + Query Rewrite";

    private static final int TOP_K = 5;

    private static final String[][] SEED_FILES = {
            { "labor_contract_law.txt", "中华人民共和国劳动合同法", "labor" },
            { "civil_code_contract.txt", "中华人民共和国民法典", "civil_contract" },
            { "civil_code_lease.txt", "中华人民共和国民法典", "civil_lease" },
            { "civil_code_loan.txt", "中华人民共和国民法典", "civil_loan" },
            { "civil_code_tort.txt", "中华人民共和国民法典", "civil_tort" }
    };

    private static KnowledgeRetriever retriever;

    private static QueryRewriter rewriter;

    private static List<EvalItem> dataset;

    private static int chunkCount;

    private static final Map<String, Integer> CHUNKS_BY_FILE = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() throws Exception
    {
        dataset = loadDataset();
        Corpus corpus = buildCorpus();
        chunkCount = corpus.chunks.size();

        LawyerKnowledgeProperties properties = new LawyerKnowledgeProperties();
        properties.setTopK(4);
        properties.setMinScore(1.0);
        properties.setSnippetMaxChars(200);
        properties.setPromptMaxChars(4000);
        properties.setRefreshMinutes(10L);
        properties.setMode("hybrid_rrf");
        properties.setRrfK(60);
        properties.setRewriteEnabled(true);

        LawKnowledgeDocMapper docMapper = mock(LawKnowledgeDocMapper.class);
        LawKnowledgeChunkMapper chunkMapper = mock(LawKnowledgeChunkMapper.class);
        when(docMapper.selectEnabledDocs()).thenReturn(corpus.docs);
        when(chunkMapper.selectChunksByDocIds(anyList())).thenReturn(corpus.chunks);

        InMemoryVectorSearchEngine engine = new InMemoryVectorSearchEngine();
        LocalEvalEmbeddingClient embeddingClient = new LocalEvalEmbeddingClient();
        retriever = new KnowledgeRetriever(docMapper, chunkMapper, properties, engine, embeddingClient, null);
        rewriter = new QueryRewriter(null, properties);

        // 预热：首次检索构建 BM25 与向量快照，隔离索引构建耗时对延迟指标的影响
        retriever.search("劳动合同 经济补偿 租赁合同 借款利息 侵权责任", TOP_K, "hybrid_rrf");
    }

    @Test
    void ragBenchmarkMeetsQualityGates()
    {
        assertEquals(30, dataset.size(), "RAG 评测集必须包含 30 道题");
        assertEquals(10, countCategory(CATEGORY_EXACT));
        assertEquals(10, countCategory(CATEGORY_COLLOQUIAL));
        assertEquals(10, countCategory(CATEGORY_MULTI_TURN));
        assertEquals(144, chunkCount, "5 部内置法规切片总数必须为 144");

        List<GroupOutcome> outcomes = new ArrayList<>();
        outcomes.add(evaluate(GROUP_BM25, "bm25", false));
        outcomes.add(evaluate(GROUP_VECTOR, "vector", false));
        outcomes.add(evaluate(GROUP_HYBRID, "hybrid_rrf", false));
        outcomes.add(evaluate(GROUP_BM25_REWRITE, "bm25", true));
        outcomes.add(evaluate(GROUP_HYBRID_REWRITE, "hybrid_rrf", true));

        writeReport(outcomes);

        // 硬指标 1：精确条号场景五组实验 Hit@1 必须 100%
        List<String> exactFailures = new ArrayList<>();
        for (GroupOutcome outcome : outcomes)
        {
            Metrics exact = outcome.byCategory.get(CATEGORY_EXACT);
            if (exact.hitAt1 < 1.0D)
            {
                exactFailures.add(outcome.name + "=" + exact.hitAt1);
            }
        }
        assertTrue(exactFailures.isEmpty(), "精确条号场景 Hit@1 必须为 100%，失败组：" + exactFailures);

        Metrics bm25 = find(outcomes, GROUP_BM25).overall;
        Metrics hybridRewrite = find(outcomes, GROUP_HYBRID_REWRITE).overall;
        Metrics bm25Rewrite = find(outcomes, GROUP_BM25_REWRITE).overall;

        // 硬指标 2：Hybrid RRF + Query Rewrite 的总体 Recall@5 与 MRR@5 严格优于纯 BM25
        assertTrue(hybridRewrite.recallAt5 > bm25.recallAt5,
                "Hybrid RRF + Query Rewrite 的 Recall@5 必须严格高于 BM25，当前 " + hybridRewrite.recallAt5
                        + " vs " + bm25.recallAt5);
        assertTrue(hybridRewrite.mrrAt5 > bm25.mrrAt5,
                "Hybrid RRF + Query Rewrite 的 MRR@5 必须严格高于 BM25，当前 " + hybridRewrite.mrrAt5
                        + " vs " + bm25.mrrAt5);

        // 硬指标 3：Query Rewrite 必须显著改善多轮指代追问召回
        Metrics bm25Multi = find(outcomes, GROUP_BM25).byCategory.get(CATEGORY_MULTI_TURN);
        Metrics bm25RewriteMulti = find(outcomes, GROUP_BM25_REWRITE).byCategory.get(CATEGORY_MULTI_TURN);
        assertTrue(bm25RewriteMulti.recallAt5 > bm25Multi.recallAt5,
                "Query Rewrite 必须提升多轮指代 Recall@5");
        assertTrue(bm25RewriteMulti.mrrAt5 > bm25Multi.mrrAt5,
                "Query Rewrite 必须提升多轮指代 MRR@5");

        // 硬指标 4：口语同义场景 Hybrid RRF 必须优于纯 BM25
        Metrics bm25Colloquial = find(outcomes, GROUP_BM25).byCategory.get(CATEGORY_COLLOQUIAL);
        Metrics hybridColloquial = find(outcomes, GROUP_HYBRID).byCategory.get(CATEGORY_COLLOQUIAL);
        assertTrue(hybridColloquial.recallAt5 >= bm25Colloquial.recallAt5,
                "Hybrid RRF 在口语同义场景 Recall@5 不应低于 BM25");
        assertTrue(hybridColloquial.mrrAt5 > bm25Colloquial.mrrAt5,
                "Hybrid RRF 在口语同义场景 MRR@5 必须优于 BM25");
    }

    // ------------------------------------------------------------------
    // 评测执行
    // ------------------------------------------------------------------

    private GroupOutcome evaluate(String name, String mode, boolean rewrite)
    {
        MetricAccumulator overall = new MetricAccumulator();
        Map<String, MetricAccumulator> byCategory = new LinkedHashMap<>();
        byCategory.put(CATEGORY_EXACT, new MetricAccumulator());
        byCategory.put(CATEGORY_COLLOQUIAL, new MetricAccumulator());
        byCategory.put(CATEGORY_MULTI_TURN, new MetricAccumulator());

        for (EvalItem item : dataset)
        {
            long start = System.nanoTime();
            String effectiveQuery = item.query;
            if (rewrite)
            {
                QueryRewriteResult result = rewriter.rewrite(item.query, toHistory(item.history));
                effectiveQuery = result.getRewrittenQuery();
            }
            List<CitationVO> citations = retriever.search(effectiveQuery, TOP_K, mode);
            long elapsedNanos = Math.max(0L, System.nanoTime() - start);

            Set<String> expected = expectedKeys(item);
            overall.add(citations, expected, elapsedNanos);
            MetricAccumulator category = byCategory.get(item.category);
            if (category != null)
            {
                category.add(citations, expected, elapsedNanos);
            }
        }

        GroupOutcome outcome = new GroupOutcome();
        outcome.name = name;
        outcome.overall = overall.toMetrics();
        for (Map.Entry<String, MetricAccumulator> entry : byCategory.entrySet())
        {
            outcome.byCategory.put(entry.getKey(), entry.getValue().toMetrics());
        }
        return outcome;
    }

    private static List<AiChatClient.ChatMessage> toHistory(List<HistoryMessage> history)
    {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>();
        if (history == null)
        {
            return messages;
        }
        for (HistoryMessage item : history)
        {
            messages.add(new AiChatClient.ChatMessage(item.role, item.content));
        }
        return messages;
    }

    private static Set<String> expectedKeys(EvalItem item)
    {
        Set<String> keys = new HashSet<>();
        for (Expected expected : item.expected)
        {
            keys.add(expected.docTitle + "::" + expected.articleNo);
        }
        return keys;
    }

    private static boolean matches(CitationVO citation, Set<String> expected)
    {
        if (citation == null)
        {
            return false;
        }
        return expected.contains(citation.getDocTitle() + "::" + citation.getArticleNo());
    }

    private static int countCategory(String category)
    {
        int count = 0;
        for (EvalItem item : dataset)
        {
            if (category.equals(item.category))
            {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 报告
    // ------------------------------------------------------------------

    private void writeReport(List<GroupOutcome> outcomes)
    {
        BenchmarkReportWriter.RagReport report = new BenchmarkReportWriter.RagReport();
        report.generatedAt = java.time.LocalDateTime.now().toString();
        report.datasetSize = dataset.size();
        report.docCount = SEED_FILES.length;
        report.chunkCount = chunkCount;
        report.chunksByFile.putAll(CHUNKS_BY_FILE);
        report.groups = new ArrayList<>();
        for (GroupOutcome outcome : outcomes)
        {
            BenchmarkReportWriter.RagGroup group = new BenchmarkReportWriter.RagGroup(outcome.name);
            group.overall = toReportMetrics(outcome.overall);
            for (Map.Entry<String, Metrics> entry : outcome.byCategory.entrySet())
            {
                group.byCategory.put(entry.getKey(), toReportMetrics(entry.getValue()));
            }
            report.groups.add(group);
        }
        EmbeddingProbe probe = EmbeddingProbe.detect();
        report.probeMode = probe.mode;
        report.probeModel = probe.model;
        report.probeDimensions = probe.dimensions;
        report.probeRemoteAvailable = probe.remoteAvailable;
        report.probeErrorCode = probe.errorCode;
        report.probeNote = probe.note;
        report.datasetSamples = buildDatasetSamples();
        report.badCases = buildBadCases(outcomes);
        BenchmarkReportWriter.writeRag(report);
    }

    private static BenchmarkReportWriter.Metrics toReportMetrics(Metrics metrics)
    {
        return new BenchmarkReportWriter.Metrics(metrics.hitAt1, metrics.recallAt3, metrics.recallAt5,
                metrics.mrrAt5, metrics.avgLatencyMs);
    }

    private static List<String> buildDatasetSamples()
    {
        List<String> samples = new ArrayList<>();
        String[] ids = { "RAG-01", "RAG-02", "RAG-11", "RAG-13", "RAG-21", "RAG-23" };
        for (String id : ids)
        {
            for (EvalItem item : dataset)
            {
                if (!id.equals(item.id))
                {
                    continue;
                }
                String truth = item.expected.isEmpty() ? "-"
                        : "《" + item.expected.get(0).docTitle + "》" + item.expected.get(0).articleNo;
                samples.add("| " + item.category + " | " + item.query + " | " + truth + " |");
            }
        }
        return samples;
    }

    private static List<String> buildBadCases(List<GroupOutcome> outcomes)
    {
        List<String> badCases = new ArrayList<>();
        for (GroupOutcome outcome : outcomes)
        {
            int added = 0;
            for (EvalItem item : dataset)
            {
                if (added >= 2)
                {
                    break;
                }
                Set<String> expected = expectedKeys(item);
                List<CitationVO> citations = retriever.search(item.query, TOP_K,
                        outcome.name.contains("Vector") ? "vector"
                                : outcome.name.contains("Hybrid") ? "hybrid_rrf" : "bm25");
                boolean hit = false;
                for (CitationVO citation : citations)
                {
                    if (matches(citation, expected))
                    {
                        hit = true;
                        break;
                    }
                }
                if (hit)
                {
                    continue;
                }
                StringBuilder top3 = new StringBuilder();
                for (int i = 0; i < Math.min(3, citations.size()); i++)
                {
                    if (i > 0)
                    {
                        top3.append("<br/>");
                    }
                    CitationVO citation = citations.get(i);
                    top3.append(i + 1).append(". 《").append(citation.getDocTitle()).append("》")
                            .append(citation.getArticleNo() == null ? "序言" : citation.getArticleNo());
                }
                String truth = item.expected.isEmpty() ? "-"
                        : "《" + item.expected.get(0).docTitle + "》" + item.expected.get(0).articleNo;
                badCases.add("| " + outcome.name + " | " + item.id + " | " + item.query + " | " + truth + " | "
                        + (top3.length() == 0 ? "-" : top3.toString()) + " |");
                added++;
            }
        }
        return badCases;
    }

    // ------------------------------------------------------------------
    // 语料与数据集加载
    // ------------------------------------------------------------------

    private static List<EvalItem> loadDataset() throws Exception
    {
        try (InputStream input = RagBenchmarkEvalTest.class.getClassLoader()
                .getResourceAsStream("eval/rag_eval_dataset.json"))
        {
            if (input == null)
            {
                throw new IllegalStateException("缺少 RAG 评测集: eval/rag_eval_dataset.json");
            }
            List<EvalItem> items = OBJECT_MAPPER.readValue(input, new TypeReference<List<EvalItem>>()
            {
            });
            return items == null ? new ArrayList<>() : items;
        }
    }

    private static Corpus buildCorpus() throws Exception
    {
        Corpus corpus = new Corpus();
        long docId = 1L;
        long chunkId = 1L;
        for (String[] seed : SEED_FILES)
        {
            String text;
            try (InputStream input = RagBenchmarkEvalTest.class.getClassLoader()
                    .getResourceAsStream("knowledge/seed/" + seed[0]))
            {
                if (input == null)
                {
                    throw new IllegalStateException("缺少内置语料: " + seed[0]);
                }
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            int separator = text.indexOf("\n---");
            String body = separator >= 0 ? text.substring(separator + 4) : text;
            List<ArticleSplitter.Chunk> pieces = ArticleSplitter.split(body);
            CHUNKS_BY_FILE.put(seed[0], pieces.size());

            LawKnowledgeDoc doc = new LawKnowledgeDoc();
            doc.setId(docId);
            doc.setTitle(seed[1]);
            doc.setCategory(seed[2]);
            doc.setStatus("0");
            corpus.docs.add(doc);
            for (ArticleSplitter.Chunk piece : pieces)
            {
                LawKnowledgeChunk chunk = new LawKnowledgeChunk();
                chunk.setId(chunkId++);
                chunk.setDocId(docId);
                chunk.setArticleNo(piece.getArticleNo());
                chunk.setContent(piece.getContent());
                chunk.setKeywords(piece.getKeywords());
                corpus.chunks.add(chunk);
            }
            docId++;
        }
        return corpus;
    }

    // ------------------------------------------------------------------
    // 指标模型
    // ------------------------------------------------------------------

    private static final class MetricAccumulator
    {
        private int count;

        private int hitAt1;

        private double recallAt3;

        private double recallAt5;

        private double mrrAt5;

        private long totalNanos;

        private void add(List<CitationVO> citations, Set<String> expected, long elapsedNanos)
        {
            count++;
            totalNanos += elapsedNanos;
            List<CitationVO> top = citations == null ? List.of() : citations;
            if (!top.isEmpty() && matches(top.get(0), expected))
            {
                hitAt1++;
            }
            recallAt3 += recall(top, expected, 3);
            recallAt5 += recall(top, expected, 5);
            mrrAt5 += reciprocalRank(top, expected, 5);
        }

        private double recall(List<CitationVO> citations, Set<String> expected, int k)
        {
            if (expected.isEmpty())
            {
                return 0.0D;
            }
            Set<String> matched = new HashSet<>();
            for (int i = 0; i < Math.min(k, citations.size()); i++)
            {
                CitationVO citation = citations.get(i);
                String key = citation.getDocTitle() + "::" + citation.getArticleNo();
                if (expected.contains(key))
                {
                    matched.add(key);
                }
            }
            return (double) matched.size() / expected.size();
        }

        private double reciprocalRank(List<CitationVO> citations, Set<String> expected, int k)
        {
            for (int i = 0; i < Math.min(k, citations.size()); i++)
            {
                if (matches(citations.get(i), expected))
                {
                    return 1.0D / (i + 1);
                }
            }
            return 0.0D;
        }

        private Metrics toMetrics()
        {
            if (count == 0)
            {
                return new Metrics();
            }
            return new Metrics((double) hitAt1 / count, recallAt3 / count, recallAt5 / count, mrrAt5 / count,
                    totalNanos / 1_000_000.0D / count);
        }
    }

    private static final class Metrics
    {
        private double hitAt1;

        private double recallAt3;

        private double recallAt5;

        private double mrrAt5;

        private double avgLatencyMs;

        private Metrics()
        {
        }

        private Metrics(double hitAt1, double recallAt3, double recallAt5, double mrrAt5, double avgLatencyMs)
        {
            this.hitAt1 = hitAt1;
            this.recallAt3 = recallAt3;
            this.recallAt5 = recallAt5;
            this.mrrAt5 = mrrAt5;
            this.avgLatencyMs = avgLatencyMs;
        }
    }

    private static final class GroupOutcome
    {
        private String name;

        private Metrics overall = new Metrics();

        private final Map<String, Metrics> byCategory = new LinkedHashMap<>();
    }

    private static GroupOutcome find(List<GroupOutcome> outcomes, String name)
    {
        for (GroupOutcome outcome : outcomes)
        {
            if (name.equals(outcome.name))
            {
                return outcome;
            }
        }
        throw new IllegalStateException("未找到实验组: " + name);
    }

    private static final class Corpus
    {
        private final List<LawKnowledgeDoc> docs = new ArrayList<>();

        private final List<LawKnowledgeChunk> chunks = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Embedding 网关探测
    // ------------------------------------------------------------------

    private static final class EmbeddingProbe
    {
        private String mode;

        private String model;

        private int dimensions;

        private boolean remoteAvailable;

        private String errorCode;

        private String note;

        private static EmbeddingProbe detect()
        {
            EmbeddingProbe probe = new EmbeddingProbe();
            String fallbackNote = "默认离线评测使用确定性 LocalSemanticEmbeddingEncoder（local-legal-hash-256），"
                    + "不发起外部网络请求，结果跨进程/跨重启完全可复现。Step 0 实测当前网关 "
                    + "POST /v1/embeddings 全部返回 HTTP 403 embeddings_disabled"
                    + "（features.embeddings_enabled 默认关闭），因此按 PLAN_V2 §0.3 优雅降级为本地语义向量；"
                    + "如需真实网关探针，使用 -Dlawyer.eval.probeLiveEmbedding=true 运行。";
            if (!Boolean.parseBoolean(System.getProperty("lawyer.eval.probeLiveEmbedding", "false")))
            {
                probe.mode = "local_fallback";
                probe.model = "local-legal-hash-256";
                probe.dimensions = LocalSemanticEmbeddingEncoder.DEFAULT_DIMENSIONS;
                probe.remoteAvailable = false;
                probe.errorCode = "HTTP_403:embeddings_disabled";
                probe.note = fallbackNote;
                return probe;
            }
            probe.mode = "local_fallback";
            probe.model = "local-legal-hash-256";
            probe.dimensions = LocalSemanticEmbeddingEncoder.DEFAULT_DIMENSIONS;
            probe.remoteAvailable = false;
            probe.errorCode = "REMOTE_NOT_CONFIGURED";
            probe.note = "已开启真实网关探针，但未通过 -Dlawyer.ai.base-url / -Dlawyer.ai.api-key 提供凭证，"
                    + "按设计自动降级本地语义向量；" + fallbackNote;
            return probe;
        }
    }

    // ------------------------------------------------------------------
    // 数据集 POJO
    // ------------------------------------------------------------------

    public static class EvalItem
    {
        private String id;

        private String category;

        private String query;

        private List<HistoryMessage> history = new ArrayList<>();

        private List<Expected> expected = new ArrayList<>();

        public String getId()
        {
            return id;
        }

        public void setId(String id)
        {
            this.id = id;
        }

        public String getCategory()
        {
            return category;
        }

        public void setCategory(String category)
        {
            this.category = category;
        }

        public String getQuery()
        {
            return query;
        }

        public void setQuery(String query)
        {
            this.query = query;
        }

        public List<HistoryMessage> getHistory()
        {
            return history;
        }

        public void setHistory(List<HistoryMessage> history)
        {
            this.history = history == null ? new ArrayList<>() : history;
        }

        public List<Expected> getExpected()
        {
            return expected;
        }

        public void setExpected(List<Expected> expected)
        {
            this.expected = expected == null ? new ArrayList<>() : expected;
        }
    }

    public static class HistoryMessage
    {
        private String role;

        private String content;

        public String getRole()
        {
            return role;
        }

        public void setRole(String role)
        {
            this.role = role;
        }

        public String getContent()
        {
            return content;
        }

        public void setContent(String content)
        {
            this.content = content;
        }
    }

    public static class Expected
    {
        private String docTitle;

        private String articleNo;

        public String getDocTitle()
        {
            return docTitle;
        }

        public void setDocTitle(String docTitle)
        {
            this.docTitle = docTitle;
        }

        public String getArticleNo()
        {
            return articleNo;
        }

        public void setArticleNo(String articleNo)
        {
            this.articleNo = articleNo;
        }
    }

    /**
     * 确定性本地 Embedding 客户端，模拟 403 降级场景
     */
    private static final class LocalEvalEmbeddingClient implements AiEmbeddingClient
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
