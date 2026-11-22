package com.ruoyi.lawyer.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具 2：法律金额与时效精确计算（100% 确定性，禁止大模型心算）
 *
 * @author ruoyi
 */
@Component
public class CalcLegalAmountTool implements LawAgentTool
{
    /** 工具名 */
    public static final String TOOL_NAME = "calc_legal_amount";

    private static final List<String> CALC_TYPES = List.of(LegalAmountCalculator.TYPE_LABOR_COMPENSATION,
            LegalAmountCalculator.TYPE_LOAN_INTEREST, LegalAmountCalculator.TYPE_LIMITATION_PERIOD);

    private final LegalAmountCalculator calculator;

    private final ObjectMapper objectMapper;

    @Autowired
    public CalcLegalAmountTool(LegalAmountCalculator calculator, ObjectMapper objectMapper)
    {
        this.calculator = calculator;
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
        return "精确计算劳动解除经济补偿/赔偿金（N、N+1、2N）、民间借贷利息与 LPR 四倍上限、诉讼或劳动仲裁时效；"
                + "凡涉及金额、天数、截止日期必须调用本工具，不得心算。";
    }

    @Override
    public String displayTitle(Map<String, Object> arguments)
    {
        Object calcType = arguments == null ? null : arguments.get("calcType");
        if (LegalAmountCalculator.TYPE_LABOR_COMPENSATION.equals(calcType))
        {
            return "正在精确计算劳动解除赔偿金...";
        }
        if (LegalAmountCalculator.TYPE_LOAN_INTEREST.equals(calcType))
        {
            return "正在计算借款利息与 LPR 四倍上限...";
        }
        if (LegalAmountCalculator.TYPE_LIMITATION_PERIOD.equals(calcType))
        {
            return "正在推算诉讼/仲裁时效...";
        }
        return "正在精确计算法律金额/时效...";
    }

    @Override
    public Map<String, Object> parametersSchema()
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("calcType", Map.of("type", "string",
                "enum", CALC_TYPES,
                "description", "计算类型：labor_compensation | loan_interest | limitation_period"));
        properties.put("params", Map.of("type", "object", "description",
                "按 calcType 区分的参数对象（金额为数字，日期为 yyyy-MM-dd）"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("calcType", "params"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments)
    {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Object calcTypeValue = args.get("calcType");
        String calcType = calcTypeValue == null ? null : calcTypeValue.toString().trim();
        if (calcType == null || calcType.isEmpty())
        {
            return ToolExecutionResult.error("计算类型缺失",
                    "参数 calcType 为必填项，仅支持 labor_compensation | loan_interest | limitation_period。");
        }
        if (!CALC_TYPES.contains(calcType))
        {
            return ToolExecutionResult.error("计算类型非法",
                    "参数 calcType 非法：" + calcType + "，仅支持 labor_compensation | loan_interest | "
                            + "limitation_period，请修正后重试。");
        }
        Object paramsValue = args.get("params");
        if (!(paramsValue instanceof Map<?, ?> rawParams))
        {
            return ToolExecutionResult.error("计算参数缺失",
                    "参数 params 为必填对象，请按 calcType 提供对应字段（金额为数字，日期格式 yyyy-MM-dd）。");
        }
        Map<String, Object> params = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawParams.entrySet())
        {
            params.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        Map<String, Object> detail;
        try
        {
            detail = calculator.calculate(calcType, params);
        }
        catch (IllegalArgumentException e)
        {
            String message = e.getMessage() == null ? "参数格式非法" : e.getMessage();
            return ToolExecutionResult.error("法律计算参数校验失败", message
                    + "。示例：{\"calcType\":\"labor_compensation\",\"params\":{\"monthlySalary\":10000,"
                    + "\"terminationType\":\"2n\",\"yearsOfService\":3.5}}。请修正 params 后重新调用本工具。");
        }
        catch (Exception e)
        {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ToolExecutionResult.error("法律计算执行失败",
                    "计算过程中发生异常（" + message + "），请核对参数格式后重试。");
        }

        Object summaryValue = detail.remove("summary");
        String summary = summaryValue == null ? "计算完成" : summaryValue.toString();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("status", "success");
        content.put("calcType", calcType);
        content.put("summary", summary);
        content.put("detail", detail);
        String modelContent;
        try
        {
            modelContent = objectMapper.writeValueAsString(content);
        }
        catch (Exception e)
        {
            modelContent = summary;
        }
        return ToolExecutionResult.success(summary, detail, modelContent, null, List.of());
    }
}
