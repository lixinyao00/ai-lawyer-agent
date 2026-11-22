package com.ruoyi.lawyer.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.lawyer.retrieval.KnowledgeRetriever;
import com.ruoyi.lawyer.vo.CitationVO;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具 1：按主题/分类主动检索法条，命中结果合并到当前回答的 citations
 *
 * @author ruoyi
 */
@Component
public class SearchLawArticlesTool implements LawAgentTool
{
    /** 工具名 */
    public static final String TOOL_NAME = "search_law_articles";

    private static final int DEFAULT_TOP_K = 4;

    private static final int MIN_TOP_K = 1;

    private static final int MAX_TOP_K = 8;

    private static final int MAX_QUERY_LENGTH = 300;

    private static final Set<String> CATEGORIES = Set.of("labor", "civil_contract", "civil_lease",
            "civil_loan", "civil_tort", "other");

    private final KnowledgeRetriever knowledgeRetriever;

    private final ObjectMapper objectMapper;

    @Autowired
    public SearchLawArticlesTool(KnowledgeRetriever knowledgeRetriever, ObjectMapper objectMapper)
    {
        this.knowledgeRetriever = knowledgeRetriever;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name()
    {
        return TOOL_NAME;
    }

    @Override
    public String description()
    {
        return "按主题、关键词或法规分类检索中国法律法规条文；当初始参考法条不足或需要补充具体条文时主动调用。";
    }

    @Override
    public String displayTitle(Map<String, Object> arguments)
    {
        Object query = arguments == null ? null : arguments.get("query");
        return query == null ? "正在检索相关法条..." : "正在检索法条：" + query;
    }

    @Override
    public Map<String, Object> parametersSchema()
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "string", "description", "检索问句或关键词（1~300 字）"));
        properties.put("category", Map.of("type", "string",
                "enum", List.of("labor", "civil_contract", "civil_lease", "civil_loan", "civil_tort", "other"),
                "description", "限定法规分类，可选"));
        properties.put("topK", Map.of("type", "integer", "description", "返回条数，默认 4，范围 1~8"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments)
    {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        String query = stringValue(args.get("query"));
        if (query == null || query.isBlank())
        {
            return ToolExecutionResult.error("法条检索参数缺失",
                    "参数 query 为必填项且不能为空，请提供一句可直接检索的中文查询后重试。");
        }
        query = query.trim();
        if (query.length() > MAX_QUERY_LENGTH)
        {
            return ToolExecutionResult.error("检索词过长",
                    "参数 query 长度不能超过 " + MAX_QUERY_LENGTH + " 字，请精简为关键法律问题后重试。");
        }
        String category = stringValue(args.get("category"));
        if (category != null)
        {
            category = category.trim();
            if (category.isEmpty())
            {
                category = null;
            }
            else if (!CATEGORIES.contains(category))
            {
                return ToolExecutionResult.error("法规分类非法", "参数 category 仅支持 labor | civil_contract | "
                        + "civil_lease | civil_loan | civil_tort | other，当前值为 " + category + "，请修正后重试。");
            }
        }
        Integer topK = intValue(args.get("topK"));
        if (args.get("topK") != null && topK == null)
        {
            return ToolExecutionResult.error("topK 格式非法",
                    "参数 topK 必须为整数（1~8），例如 4，请修正后重试。");
        }
        if (topK == null)
        {
            topK = DEFAULT_TOP_K;
        }
        if (topK < MIN_TOP_K || topK > MAX_TOP_K)
        {
            return ToolExecutionResult.error("topK 越界",
                    "参数 topK 取值范围为 " + MIN_TOP_K + "~" + MAX_TOP_K + "，当前值为 " + topK + "，请修正后重试。");
        }

        List<CitationVO> hits;
        try
        {
            hits = knowledgeRetriever.search(query, topK, null, category);
        }
        catch (Exception e)
        {
            return ToolExecutionResult.error("法条检索执行失败",
                    "知识库检索暂时失败（" + safeMessage(e) + "），请稍后重试，或直接基于已有参考法条回答并向用户说明。");
        }
        if (hits == null)
        {
            hits = new ArrayList<>();
        }
        List<CitationVO> added = context == null ? new ArrayList<>() : context.mergeCitations(hits);
        String summary = buildSummary(hits);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("query", query);
        detail.put("category", category);
        detail.put("topK", topK);
        detail.put("count", hits.size());
        detail.put("addedCount", added.size());
        detail.put("citations", hits);
        String modelContent = buildModelContent(summary, hits, added, category);
        return ToolExecutionResult.success(summary, detail, modelContent, null, added);
    }

    private String buildSummary(List<CitationVO> hits)
    {
        if (hits.isEmpty())
        {
            return "知识库中未检索到直接匹配的条文";
        }
        StringBuilder builder = new StringBuilder("检索到 ").append(hits.size()).append(" 条参考法条：");
        int limit = Math.min(3, hits.size());
        for (int i = 0; i < limit; i++)
        {
            CitationVO citation = hits.get(i);
            if (i > 0)
            {
                builder.append("、");
            }
            builder.append("《").append(nullToEmpty(citation.getDocTitle())).append("》")
                    .append(nullToEmpty(citation.getArticleNo()));
        }
        if (hits.size() > limit)
        {
            builder.append(" 等");
        }
        return builder.toString();
    }

    private String buildModelContent(String summary, List<CitationVO> hits, List<CitationVO> added, String category)
    {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("status", "success");
        content.put("summary", summary);
        content.put("category", category);
        List<Map<String, Object>> items = new ArrayList<>();
        for (CitationVO citation : hits)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chunkId", citation.getChunkId());
            item.put("docTitle", citation.getDocTitle());
            item.put("articleNo", citation.getArticleNo());
            item.put("snippet", citation.getSnippet());
            item.put("score", citation.getScore());
            items.add(item);
        }
        content.put("citations", items);
        content.put("newlyMerged", added == null ? 0 : added.size());
        try
        {
            return objectMapper.writeValueAsString(content);
        }
        catch (Exception e)
        {
            return summary;
        }
    }

    private String stringValue(Object value)
    {
        return value == null ? null : value.toString();
    }

    private Integer intValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.parseInt(value.toString().trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private String safeMessage(Exception e)
    {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }
}
