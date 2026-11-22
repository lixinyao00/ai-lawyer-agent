package com.ruoyi.lawyer.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.vo.RiskItem;
import tools.jackson.databind.ObjectMapper;

/**
 * ModelJsonParser 单元测试
 *
 * @author ruoyi
 */
class ModelJsonParserTest
{
    private final ModelJsonParser parser = new ModelJsonParser(new ObjectMapper());

    @Test
    void parsesFencedJsonWithSurroundingText()
    {
        String raw = "以下是审查结果：\n```json\n"
                + "{\"riskLevel\":\"high\",\"summary\":\"整体风险较高\","
                + "\"risks\":[{\"clauseTitle\":\"第五条 违约责任\","
                + "\"originalSnippet\":\"违约金为合同总额的50%\",\"riskLevel\":\"high\","
                + "\"riskReason\":\"违约金过高\",\"suggestion\":\"调整为30%以内\","
                + "\"legalBasis\":\"《民法典》第五百八十五条\"}]}\n```\n请核验。";

        ModelJsonParser.ParseResult result = parser.parse(raw);

        assertEquals("high", result.getRiskLevel());
        assertEquals("整体风险较高", result.getSummary());
        assertEquals(1, result.getRisks().size());
        RiskItem item = result.getRisks().get(0);
        assertEquals("第五条 违约责任", item.getClauseTitle());
        assertEquals("违约金为合同总额的50%", item.getOriginalSnippet());
        assertEquals("high", item.getRiskLevel());
        assertEquals("违约金过高", item.getRiskReason());
        assertEquals("调整为30%以内", item.getSuggestion());
        assertEquals("《民法典》第五百八十五条", item.getLegalBasis());
    }

    @Test
    void normalizesIllegalRiskLevelToMedium()
    {
        String raw = "{\"riskLevel\":\"urgent\",\"summary\":\"s\","
                + "\"risks\":[{\"clauseTitle\":\"c\",\"riskLevel\":\"SEVERE\"}]}";

        ModelJsonParser.ParseResult result = parser.parse(raw);

        assertEquals("medium", result.getRiskLevel());
        assertEquals("medium", result.getRisks().get(0).getRiskLevel());
    }

    @Test
    void sortsRisksBySeverityAndKeepsStableOrderWithinLevel()
    {
        String raw = "{\"summary\":\"s\",\"risks\":["
                + risk("低1", "low") + "," + risk("中1", "medium") + ","
                + risk("高1", "high") + "," + risk("中2", "medium") + ","
                + risk("高2", "high") + "," + risk("低2", "low") + "]}";

        ModelJsonParser.ParseResult result = parser.parse(raw);

        List<RiskItem> risks = result.getRisks();
        assertEquals(6, risks.size());
        assertEquals("高1", risks.get(0).getClauseTitle());
        assertEquals("高2", risks.get(1).getClauseTitle());
        assertEquals("中1", risks.get(2).getClauseTitle());
        assertEquals("中2", risks.get(3).getClauseTitle());
        assertEquals("低1", risks.get(4).getClauseTitle());
        assertEquals("低2", risks.get(5).getClauseTitle());
        // 顶层缺失时取最高级
        assertEquals("high", result.getRiskLevel());
    }

    @Test
    void limitsRisksToThirty()
    {
        StringBuilder risks = new StringBuilder();
        for (int i = 0; i < 35; i++)
        {
            if (i > 0)
            {
                risks.append(',');
            }
            risks.append(risk("条款" + i, "medium"));
        }
        String raw = "{\"summary\":\"s\",\"risks\":[" + risks + "]}";

        ModelJsonParser.ParseResult result = parser.parse(raw);

        assertEquals(30, result.getRisks().size());
    }

    @Test
    void fillsMissingFieldsWithEmptyStrings()
    {
        String raw = "{\"risks\":[{\"clauseTitle\":\"仅有标题\"}]}";

        ModelJsonParser.ParseResult result = parser.parse(raw);

        RiskItem item = result.getRisks().get(0);
        assertEquals("仅有标题", item.getClauseTitle());
        assertEquals("", item.getOriginalSnippet());
        assertEquals("", item.getRiskReason());
        assertEquals("", item.getSuggestion());
        assertEquals("", item.getLegalBasis());
        assertEquals("medium", item.getRiskLevel());
        assertEquals("medium", result.getRiskLevel());
    }

    @Test
    void defaultsToLowWhenNoRisks()
    {
        ModelJsonParser.ParseResult result = parser.parse("{\"summary\":\"未发现明显风险\",\"risks\":[]}");

        assertEquals("low", result.getRiskLevel());
        assertEquals(0, result.getRisks().size());
    }

    @Test
    void throwsParseExceptionOnMalformedJson()
    {
        assertThrows(ModelJsonParser.ParseException.class, () -> parser.parse("模型没有返回 JSON"));
        assertThrows(ModelJsonParser.ParseException.class, () -> parser.parse("{not-json}"));
    }

    private String risk(String title, String level)
    {
        return "{\"clauseTitle\":\"" + title + "\",\"riskLevel\":\"" + level + "\"}";
    }
}
