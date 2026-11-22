package com.ruoyi.lawyer.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import com.ruoyi.lawyer.vo.FieldDef;

/**
 * 文书动态表单要素校验器
 *
 * <p>按模板字段定义校验 formData：必填、日期、金额、数字、手机号、身份证号、
 * select 选项与文本长度；未知 key 一律丢弃，返回清洗后的规范化 Map。
 * 校验失败统一抛出 422 业务异常。</p>
 *
 * @author ruoyi
 */
@Component
public class FormDataValidator
{
    /** text 默认最大长度 */
    public static final int DEFAULT_TEXT_MAX_LENGTH = 200;

    /** textarea 默认最大长度 */
    public static final int DEFAULT_TEXTAREA_MAX_LENGTH = 2000;

    /** 金额最大小数位 */
    private static final int MONEY_MAX_SCALE = 2;

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("^[+-]?\\d+(\\.\\d+)?$");

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$|^0\\d{2,3}-?\\d{7,8}$");

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");

    /**
     * 校验并规范化表单数据
     *
     * @param template 模板元数据
     * @param formData 用户提交的要素
     * @return 规范化后的要素（仅包含模板已定义的字段）
     */
    public Map<String, Object> validate(DocumentTemplateVO template, Map<String, Object> formData)
    {
        if (template == null)
        {
            throw new ServiceException("文书模板不存在", 422);
        }
        Map<String, Object> input = formData == null ? Map.of() : formData;
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (FieldDef field : template.getFields())
        {
            Object raw = input.get(field.getKey());
            if (isEmptyValue(raw))
            {
                if (Boolean.TRUE.equals(field.getRequired()))
                {
                    throw invalid(field, "不能为空");
                }
                continue;
            }
            cleaned.put(field.getKey(), normalize(field, raw));
        }
        return cleaned;
    }

    private Object normalize(FieldDef field, Object raw)
    {
        String type = field.getType() == null ? "text" : field.getType();
        switch (type)
        {
            case "date":
                return normalizeDate(field, raw);
            case "number":
                return normalizeNumber(field, raw);
            case "money":
                return normalizeMoney(field, raw);
            case "phone":
                return normalizePhone(field, raw);
            case "idcard":
                return normalizeIdCard(field, raw);
            case "select":
                return normalizeSelect(field, raw);
            case "textarea":
                return normalizeText(field, raw, DEFAULT_TEXTAREA_MAX_LENGTH);
            case "text":
            default:
                return normalizeText(field, raw, DEFAULT_TEXT_MAX_LENGTH);
        }
    }

    private String normalizeDate(FieldDef field, Object raw)
    {
        String value = raw.toString().trim();
        if (!DATE_PATTERN.matcher(value).matches())
        {
            throw invalid(field, "日期格式应为 yyyy-MM-dd");
        }
        try
        {
            LocalDate.parse(value, DATE_FORMATTER);
        }
        catch (DateTimeParseException e)
        {
            throw invalid(field, "日期不合法，请填写有效的年月日");
        }
        return value;
    }

    private BigDecimal normalizeNumber(FieldDef field, Object raw)
    {
        BigDecimal number = toBigDecimal(raw);
        if (number == null)
        {
            throw invalid(field, "必须是数字");
        }
        return number;
    }

    private BigDecimal normalizeMoney(FieldDef field, Object raw)
    {
        BigDecimal amount = toBigDecimal(raw);
        if (amount == null)
        {
            throw invalid(field, "必须为数字金额");
        }
        if (amount.signum() < 0)
        {
            throw invalid(field, "金额不能小于 0");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > MONEY_MAX_SCALE)
        {
            throw invalid(field, "金额最多保留 2 位小数");
        }
        return amount;
    }

    private String normalizePhone(FieldDef field, Object raw)
    {
        String value = raw.toString().trim();
        if (!PHONE_PATTERN.matcher(value).matches())
        {
            throw invalid(field, "必须是合法的手机号或固定电话号码");
        }
        return value;
    }

    private String normalizeIdCard(FieldDef field, Object raw)
    {
        String value = raw.toString().trim();
        if (!ID_CARD_PATTERN.matcher(value).matches())
        {
            throw invalid(field, "必须是 18 位合法身份证号");
        }
        return value;
    }

    private String normalizeSelect(FieldDef field, Object raw)
    {
        String value = raw.toString().trim();
        if (field.getOptions() == null || !field.getOptions().contains(value))
        {
            throw invalid(field, "取值不在可选范围内");
        }
        return value;
    }

    private String normalizeText(FieldDef field, Object raw, int defaultMaxLength)
    {
        String value = raw.toString().trim();
        if (value.isEmpty())
        {
            throw invalid(field, "不能为空");
        }
        int maxLength = field.getMaxLength() == null || field.getMaxLength() <= 0
                ? defaultMaxLength : field.getMaxLength();
        if (value.length() > maxLength)
        {
            throw invalid(field, "长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private BigDecimal toBigDecimal(Object raw)
    {
        if (raw instanceof BigDecimal)
        {
            return (BigDecimal) raw;
        }
        if (raw instanceof Number)
        {
            try
            {
                return new BigDecimal(((Number) raw).toString());
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        String value = raw.toString().trim();
        if (!NUMBER_PATTERN.matcher(value).matches())
        {
            return null;
        }
        try
        {
            return new BigDecimal(value);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private boolean isEmptyValue(Object value)
    {
        if (value == null)
        {
            return true;
        }
        if (value instanceof CharSequence)
        {
            return value.toString().trim().isEmpty();
        }
        if (value instanceof Collection)
        {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map)
        {
            return ((Map<?, ?>) value).isEmpty();
        }
        return false;
    }

    private ServiceException invalid(FieldDef field, String reason)
    {
        return new ServiceException("【" + field.getLabel() + "】" + reason, 422);
    }
}
