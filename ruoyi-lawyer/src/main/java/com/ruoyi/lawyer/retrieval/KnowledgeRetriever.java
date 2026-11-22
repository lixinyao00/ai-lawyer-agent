package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.domain.LawKnowledgeChunk;
import com.ruoyi.lawyer.domain.LawKnowledgeDoc;
import com.ruoyi.lawyer.mapper.LawKnowledgeChunkMapper;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * 轻量法律知识库检索器
 *
 * <p>首次查询时从数据库加载全部启用文档的切片，构建进程内不可变快照；
 * 使用 BM25（bigram）+ 条号精确匹配 + 法规名匹配混合打分，不依赖外部向量库。</p>
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

    /** 检索调试接口最多返回条数 */
    private static final int MAX_TOP_K = 10;

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

    /** 当前内存快照，invalidate 置空后下次查询重建 */
    private volatile Snapshot snapshot;

    public KnowledgeRetriever(LawKnowledgeDocMapper docMapper, LawKnowledgeChunkMapper chunkMapper,
            LawyerKnowledgeProperties properties)
    {
        this.docMapper = docMapper;
        this.chunkMapper = chunkMapper;
        this.properties = properties;
    }

    /**
     * 检索法条
     *
     * @param query 查询文本
     * @param topK 返回条数，为空时使用配置默认值
     * @return 按相关度倒序的引用列表
     */
    public List<CitationVO> search(String query, Integer topK)
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

        Set<String> queryTerms = new LinkedHashSet<>(LegalTokenizer.tokenize(expandQuery(query)));
        Set<String> queryArticles = new HashSet<>(ChineseNumberUtils.extractArticleNos(query));
        // 条号由精确匹配加分处理，不参与 BM25，避免“本法第四十七条”这类交叉引用干扰排序
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

        Map<Integer, Double> scores = new HashMap<>();
        int chunkCount = current.chunks.size();
        for (String term : queryTerms)
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
            double score = entry.getValue();
            if (chunk.articleNo != null && queryArticles.contains(chunk.articleNo))
            {
                if (!queryHasDocName || (!chunk.coreName.isEmpty() && query.contains(chunk.coreName)))
                {
                    score += ARTICLE_MATCH_BOOST;
                }
            }
            if (!chunk.coreName.isEmpty() && query.contains(chunk.coreName))
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
            if (chunk.articleNo != null && queryArticles.contains(chunk.articleNo)
                    && (!queryHasDocName || (!chunk.coreName.isEmpty() && query.contains(chunk.coreName)))
                    && !scores.containsKey(chunk.index))
            {
                double score = ARTICLE_MATCH_BOOST;
                if (!chunk.coreName.isEmpty() && query.contains(chunk.coreName))
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

        List<CitationVO> citations = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
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
            citations.add(toCitation(candidate, snippetMaxChars));
        }
        return citations;
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

    private CitationVO toCitation(ScoredChunk scored, int snippetMaxChars)
    {
        IndexedChunk chunk = scored.chunk;
        CitationVO citation = new CitationVO();
        citation.setChunkId(chunk.id);
        citation.setDocId(chunk.docId);
        citation.setDocTitle(chunk.docTitle);
        citation.setArticleNo(chunk.articleNo);
        citation.setSnippet(snippet(chunk.content, snippetMaxChars));
        citation.setScore(Math.round(scored.score * 100.0) / 100.0);
        return citation;
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
            DocInfo info = new DocInfo(doc.getId(), doc.getTitle(), LegalTokenizer.coreName(doc.getTitle()));
            docMap.put(doc.getId(), info);
            docIds.add(doc.getId());
        }
        if (docIds.isEmpty())
        {
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
                    row.getArticleNo(), row.getContent(), termFreq, tokens.size());
            chunks.add(chunk);
            byChunkId.put(chunk.id, chunk);
            totalLength += chunk.length;
            for (String term : termFreq.keySet())
            {
                postings.computeIfAbsent(term, key -> new ArrayList<>()).add(new int[] { index, termFreq.get(term) });
            }
        }

        double avgLength = chunks.isEmpty() ? 1.0 : Math.max(1.0, (double) totalLength / chunks.size());
        log.info("知识库内存索引已构建, docs={}, chunks={}, avgLength={}", docMap.size(), chunks.size(), avgLength);
        return new Snapshot(Collections.unmodifiableMap(docMap), Collections.unmodifiableList(chunks),
                Collections.unmodifiableMap(byChunkId), Collections.unmodifiableMap(postings), avgLength, builtAt);
    }

    private static final class DocInfo
    {
        private final Long id;

        private final String title;

        private final String coreName;

        private DocInfo(Long id, String title, String coreName)
        {
            this.id = id;
            this.title = title == null ? "" : title;
            this.coreName = coreName == null ? "" : coreName;
        }
    }

    private static final class IndexedChunk
    {
        private final int index;

        private final long id;

        private final Long docId;

        private final String docTitle;

        private final String coreName;

        private final String articleNo;

        private final String content;

        private final Map<String, Integer> termFreq;

        private final double length;

        private IndexedChunk(int index, Long id, Long docId, String docTitle, String coreName, String articleNo,
                String content, Map<String, Integer> termFreq, int length)
        {
            this.index = index;
            this.id = id == null ? 0L : id;
            this.docId = docId;
            this.docTitle = docTitle;
            this.coreName = coreName;
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
