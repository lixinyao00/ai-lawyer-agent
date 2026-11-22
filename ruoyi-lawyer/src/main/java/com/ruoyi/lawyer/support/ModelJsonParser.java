package com.ruoyi.lawyer.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ruoyi.lawyer.vo.RiskItem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 合同审查模型 JSON 解析器
 *
 * <p>兼容模型输出中的 ```json 围栏与前后说明文字，只截取首个 { 至最后一个 }。
 * 对 riskLevel 做归一化，风险项按高→中→低稳定排序并最多保留 30 条。</p>
 *
 * @author ruoyi
 */
@Component
public class ModelJsonParser
{
    /** 风险项最多保留条数 */
    public static final int MAX_RISKS = 30;

    /** 默认风险等级 */
    public static final String DEFAULT_RISK_LEVEL = "medium";

    /** 无风险项时的兜底等级 */
    public static final String LOW_RISK_LEVEL = "low";

    private static final String HIGH = "high";

    private static final String MEDIUM = "medium";

    private static final String LOW = "low";

    private final ObjectMapper objectMapper;

    public ModelJsonParser(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析模型返回的合同审查 JSON
     *
     * @param raw 模型原始输出
     * @return 解析结果
     * @throws ParseException 模型输出不是合法 JSON 对象时抛出
     */
    public ParseResult parse(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            throw new ParseException("模型返回内容为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start)
        {
            throw new ParseException("模型返回内容缺少 JSON 对象");
        }
        JsonNode root;
        try
        {
            root = objectMapper.readTree(raw.substring(start, end + 1));
        }
        catch (RuntimeException e)
        {
            throw new ParseException("模型返回内容不是合法 JSON", e);
        }
        if (root == null || !root.isObject())
        {
            throw new ParseException("模型返回内容不是 JSON 对象");
        }

        String summary = text(root.path("summary"));

        List<RiskItem> risks = new ArrayList<>();
        JsonNode risksNode = root.path("risks");
        if (risksNode != null && risksNode.isArray())
        {
            for (JsonNode item : risksNode.values())
            {
                if (item != null && item.isObject())
                {
                    risks.add(parseRisk(item));
                }
            }
        }
        // List.sort 为稳定排序，同级风险保持模型输出顺序
        risks.sort(Comparator.comparingInt(item -> rank(item.getRiskLevel())));
        if (risks.size() > MAX_RISKS)
        {
            risks = new ArrayList<>(risks.subList(0, MAX_RISKS));
        }

        String riskLevel = topLevelRiskLevel(text(root.path("riskLevel")), risks);
        return new ParseResult(riskLevel, summary, risks);
    }

    private RiskItem parseRisk(JsonNode item)
    {
        RiskItem risk = new RiskItem();
        risk.setClauseTitle(text(item.path("clauseTitle")));
        risk.setOriginalSnippet(text(item.path("originalSnippet")));
        risk.setRiskReason(text(item.path("riskReason")));
        risk.setSuggestion(text(item.path("suggestion")));
        risk.setLegalBasis(text(item.path("legalBasis")));
        // 缺失或非法等级统一归一为 medium，保证前端徽章与排序可用
        risk.setRiskLevel(normalizeRiskLevel(text(item.path("riskLevel"))));
        return risk;
    }

    /**
     * 顶层等级：缺失时取风险项最高级，无风险项为 low；非法值归一为 medium
     */
    private String topLevelRiskLevel(String raw, List<RiskItem> risks)
    {
        if (raw == null || raw.isBlank())
        {
            return risks.isEmpty() ? LOW_RISK_LEVEL : risks.get(0).getRiskLevel();
        }
        return normalizeRiskLevel(raw);
    }

    /**
     * 归一化风险等级：high / medium / low，非法值一律 medium
     */
    public static String normalizeRiskLevel(String raw)
    {
        if (raw == null)
        {
            return DEFAULT_RISK_LEVEL;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (HIGH.equals(value) || MEDIUM.equals(value) || LOW.equals(value))
        {
            return value;
        }
        return DEFAULT_RISK_LEVEL;
    }

    /**
     * 排序权重：high 0 / medium 1 / low 2
     */
    public static int rank(String riskLevel)
    {
        if (HIGH.equals(riskLevel))
        {
            return 0;
        }
        if (LOW.equals(riskLevel))
        {
            return 2;
        }
        return 1;
    }

    private String text(JsonNode node)
    {
        if (node == null || node.isNull() || node.isMissingNode())
        {
            return "";
        }
        if (node.isString())
        {
            return node.stringValue();
        }
        if (node.isValueNode())
        {
            return node.asString("");
        }
        return "";
    }

    /**
     * 解析结果
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParseResult
    {
        private final String riskLevel;

        private final String summary;

        private final List<RiskItem> risks;

        public ParseResult(String riskLevel, String summary, List<RiskItem> risks)
        {
            this.riskLevel = riskLevel;
            this.summary = summary == null ? "" : summary;
            this.risks = risks == null ? new ArrayList<>() : risks;
        }

        public String getRiskLevel()
        {
            return riskLevel;
        }

        public String getSummary()
        {
            return summary;
        }

        public List<RiskItem> getRisks()
        {
            return risks;
        }
    }

    /**
     * 模型输出格式异常
     */
    public static class ParseException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        public ParseException(String message)
        {
            super(message);
        }

        public ParseException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }
}
