package com.ruoyi.lawyer.agent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 纯 Java 确定性法律金额/期限计算引擎
 *
 * <p>金额统一 {@link BigDecimal}、保留 2 位小数 {@link RoundingMode#HALF_UP}；日期统一
 * {@link LocalDate}。所有公式与法定规则见 {@code PLAN_V2.md} §4.2，彻底杜绝大模型算术幻觉。</p>
 *
 * @author ruoyi
 */
@Component
public class LegalAmountCalculator
{
    /** 计算类型：劳动解除经济补偿/赔偿金 */
    public static final String TYPE_LABOR_COMPENSATION = "labor_compensation";

    /** 计算类型：民间借贷利息 */
    public static final String TYPE_LOAN_INTEREST = "loan_interest";

    /** 计算类型：诉讼/仲裁时效 */
    public static final String TYPE_LIMITATION_PERIOD = "limitation_period";

    /** 默认一年期 LPR（百分比） */
    public static final BigDecimal DEFAULT_LPR_ANNUAL_PERCENT = new BigDecimal("3.1");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final String LABOR_LEGAL_BASIS = "《中华人民共和国劳动合同法》第四十条、第四十七条、第八十七条";

    private static final String LOAN_LEGAL_BASIS = "《中华人民共和国民法典》第六百七十六条、第六百八十条；"
            + "《最高人民法院关于审理民间借贷案件适用法律若干问题的规定》";

    private static final String LIMITATION_LEGAL_BASIS = "《中华人民共和国民法典》第一百八十八条；"
            + "《中华人民共和国劳动争议调解仲裁法》第二十七条";

    /**
     * 按计算类型分发
     *
     * @param calcType 计算类型
     * @param params 参数
     * @return 结构化计算结果
     */
    public Map<String, Object> calculate(String calcType, Map<String, Object> params)
    {
        if (calcType == null || calcType.isBlank())
        {
            throw new IllegalArgumentException("参数 calcType 为必填项，仅支持 labor_compensation | loan_interest | "
                    + "limitation_period");
        }
        Map<String, Object> values = params == null ? Map.of() : params;
        return switch (calcType.trim())
        {
            case TYPE_LABOR_COMPENSATION -> calculateLaborCompensation(values);
            case TYPE_LOAN_INTEREST -> calculateLoanInterest(values);
            case TYPE_LIMITATION_PERIOD -> calculateLimitationPeriod(values);
            default -> throw new IllegalArgumentException("参数 calcType 非法：" + calcType
                    + "，仅支持 labor_compensation | loan_interest | limitation_period");
        };
    }

    // ------------------------------------------------------------------
    // 1. 劳动解除经济补偿 / 赔偿金
    // ------------------------------------------------------------------

    /**
     * 计算劳动解除经济补偿（N）、代通知金（N+1）或违法解除赔偿金（2N）
     *
     * @param params 参数
     * @return 结构化结果
     */
    public Map<String, Object> calculateLaborCompensation(Map<String, Object> params)
    {
        BigDecimal monthlySalary = requiredPositive(params, "monthlySalary", "月工资 monthlySalary");
        String terminationType = requiredString(params, "terminationType", "解除类型 terminationType");
        if (!"n".equals(terminationType) && !"n_plus_1".equals(terminationType) && !"2n".equals(terminationType))
        {
            throw new IllegalArgumentException("参数 params.terminationType 仅支持 n | n_plus_1 | 2n，当前值为 "
                    + terminationType);
        }

        String entryDateValue = optionalString(params, "entryDate");
        String leaveDateValue = optionalString(params, "leaveDate");
        BigDecimal yearsOfService = optionalNumber(params, "yearsOfService");
        BigDecimal serviceYears;
        int fullYears;
        int remainderMonths;

        if (entryDateValue != null && leaveDateValue != null)
        {
            LocalDate entryDate = parseDate(entryDateValue, "entryDate");
            LocalDate leaveDate = parseDate(leaveDateValue, "leaveDate");
            if (leaveDate.isBefore(entryDate))
            {
                throw new IllegalArgumentException("参数 params.leaveDate 不能早于 entryDate，请检查日期后重试");
            }
            Period period = Period.between(entryDate, leaveDate);
            int totalMonths = period.getYears() * 12 + period.getMonths();
            fullYears = totalMonths / 12;
            remainderMonths = totalMonths % 12;
            serviceYears = BigDecimal.valueOf(fullYears)
                    .add(BigDecimal.valueOf(remainderMonths).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP))
                    .add(BigDecimal.valueOf(period.getDays())
                            .divide(BigDecimal.valueOf(365), 4, RoundingMode.HALF_UP));
        }
        else if (yearsOfService != null)
        {
            if (yearsOfService.signum() < 0)
            {
                throw new IllegalArgumentException("参数 params.yearsOfService 不能为负数");
            }
            fullYears = yearsOfService.setScale(0, RoundingMode.DOWN).intValue();
            BigDecimal fraction = yearsOfService.subtract(BigDecimal.valueOf(fullYears));
            remainderMonths = fraction.multiply(BigDecimal.valueOf(12)).setScale(0, RoundingMode.HALF_UP).intValue();
            if (remainderMonths >= 12)
            {
                fullYears++;
                remainderMonths = 0;
            }
            serviceYears = yearsOfService.setScale(2, RoundingMode.HALF_UP);
        }
        else
        {
            throw new IllegalArgumentException("参数 params.yearsOfService 与 entryDate+leaveDate 必须提供其一");
        }

        BigDecimal countedMonths;
        String yearsRule;
        if (remainderMonths == 0)
        {
            countedMonths = BigDecimal.valueOf(fullYears);
            yearsRule = "工作年限折算：整年 " + fullYears + " 年，无剩余月，折算 " + plain(countedMonths) + " 个月";
        }
        else if (remainderMonths < 6)
        {
            countedMonths = BigDecimal.valueOf(fullYears).add(new BigDecimal("0.5"));
            yearsRule = "工作年限折算：满 " + fullYears + " 年、剩余 " + remainderMonths
                    + " 个月（不满半年按 0.5 个月计），折算 " + plain(countedMonths) + " 个月";
        }
        else
        {
            countedMonths = BigDecimal.valueOf(fullYears).add(BigDecimal.ONE);
            yearsRule = "工作年限折算：满 " + fullYears + " 年、剩余 " + remainderMonths
                    + " 个月（满半年不满一年按 1 个月计），折算 " + plain(countedMonths) + " 个月";
        }

        BigDecimal localAvgSalary3x = optionalNumber(params, "localAvgSalary3x");
        boolean capped = false;
        BigDecimal baseSalary = monthlySalary;
        BigDecimal monthsForAmount = countedMonths;
        if (localAvgSalary3x != null)
        {
            if (localAvgSalary3x.signum() <= 0)
            {
                throw new IllegalArgumentException("参数 params.localAvgSalary3x 必须大于 0");
            }
            if (monthlySalary.compareTo(localAvgSalary3x) > 0)
            {
                capped = true;
                baseSalary = localAvgSalary3x;
                monthsForAmount = countedMonths.min(new BigDecimal("12.0"));
            }
        }

        BigDecimal nAmount = money(baseSalary.multiply(monthsForAmount));
        BigDecimal noticeSalary = optionalNumber(params, "lastMonthSalary");
        if (noticeSalary != null && noticeSalary.signum() <= 0)
        {
            throw new IllegalArgumentException("参数 params.lastMonthSalary 必须大于 0");
        }
        BigDecimal noticeBase = noticeSalary == null ? monthlySalary : noticeSalary;
        BigDecimal noticePay = "n_plus_1".equals(terminationType) ? money(noticeBase) : money(BigDecimal.ZERO);

        BigDecimal amount;
        String formula;
        String label;
        if ("n".equals(terminationType))
        {
            amount = nAmount;
            label = "经济补偿金 N";
            formula = "N = " + plain(baseSalary) + " × " + plain(monthsForAmount) + " = " + moneyText(nAmount);
        }
        else if ("n_plus_1".equals(terminationType))
        {
            amount = money(nAmount.add(noticePay));
            label = "经济补偿金 N+1";
            formula = "N+1 = " + moneyText(nAmount) + "（N） + " + moneyText(noticePay) + "（代通知金） = "
                    + moneyText(amount);
        }
        else
        {
            amount = money(nAmount.multiply(BigDecimal.valueOf(2)));
            label = "2N 违法解除赔偿金";
            formula = "2N = " + moneyText(nAmount) + " × 2 = " + moneyText(amount);
        }

        List<String> steps = new ArrayList<>();
        steps.add(yearsRule);
        if (capped)
        {
            steps.add("月工资 " + moneyText(monthlySalary) + " 超过当地三倍社平工资 " + moneyText(localAvgSalary3x)
                    + "，按封顶基数 " + moneyText(baseSalary) + " 计算，且折算月数不超过 12 个月（实际 "
                    + plain(monthsForAmount) + " 个月）");
        }
        else
        {
            steps.add("未触发三倍社平封顶：月工资基数 " + moneyText(baseSalary) + "（法定封顶条件：月工资高于当地"
                    + "上年度职工月平均工资三倍时，按三倍数额支付且年限最高 12 年）");
        }
        steps.add("经济补偿 N = " + moneyText(baseSalary) + " × " + plain(monthsForAmount) + " = "
                + moneyText(nAmount));
        if ("n_plus_1".equals(terminationType))
        {
            steps.add("代通知金（未提前 30 日书面通知）= " + moneyText(noticePay));
        }
        if ("2n".equals(terminationType))
        {
            steps.add("违法解除赔偿金 2N = N × 2 = " + moneyText(amount));
        }
        steps.add("最终" + label + " = " + moneyText(amount));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("calcType", TYPE_LABOR_COMPENSATION);
        detail.put("terminationType", terminationType);
        detail.put("baseSalary", baseSalary);
        detail.put("serviceYears", serviceYears.setScale(2, RoundingMode.HALF_UP));
        detail.put("countedMonths", monthsForAmount);
        detail.put("capped", capped);
        detail.put("noticePay", noticePay);
        detail.put("amount", amount);
        detail.put("currency", "CNY");
        detail.put("formula", formula);
        detail.put("steps", steps);
        detail.put("legalBasis", LABOR_LEGAL_BASIS);
        detail.put("summary", buildLaborSummary(label, amount, serviceYears, monthsForAmount, terminationType));
        return detail;
    }

    private String buildLaborSummary(String label, BigDecimal amount, BigDecimal serviceYears,
            BigDecimal countedMonths, String terminationType)
    {
        StringBuilder builder = new StringBuilder(label).append("：¥").append(moneyText(amount))
                .append("（工作").append(plain(serviceYears)).append("年，折算").append(plain(countedMonths))
                .append("个月");
        if ("2n".equals(terminationType))
        {
            builder.append("×2");
        }
        return builder.append("）").toString();
    }

    // ------------------------------------------------------------------
    // 2. 民间借贷利息与 LPR 四倍红线
    // ------------------------------------------------------------------

    /**
     * 计算约定利息、LPR 四倍司法保护上限内利息、超额部分与本息合计
     *
     * @param params 参数
     * @return 结构化结果
     */
    public Map<String, Object> calculateLoanInterest(Map<String, Object> params)
    {
        BigDecimal principal = requiredPositive(params, "principal", "借款本金 principal");
        BigDecimal annualRatePercent = requiredNonNegative(params, "annualRatePercent", "年利率 annualRatePercent");
        Integer daysValue = optionalInt(params, "days");
        String startDateValue = optionalString(params, "startDate");
        String endDateValue = optionalString(params, "endDate");
        int days;
        if (daysValue != null)
        {
            if (daysValue <= 0)
            {
                throw new IllegalArgumentException("参数 params.days 必须大于 0");
            }
            days = daysValue;
        }
        else if (startDateValue != null && endDateValue != null)
        {
            LocalDate startDate = parseDate(startDateValue, "startDate");
            LocalDate endDate = parseDate(endDateValue, "endDate");
            if (!endDate.isAfter(startDate))
            {
                throw new IllegalArgumentException("参数 params.endDate 必须晚于 startDate，请检查日期后重试");
            }
            days = (int) ChronoUnit.DAYS.between(startDate, endDate);
        }
        else
        {
            throw new IllegalArgumentException("参数 params.days 与 startDate+endDate 必须提供其一");
        }

        BigDecimal lprAnnualPercent = optionalNumber(params, "lprAnnualPercent");
        if (lprAnnualPercent == null)
        {
            lprAnnualPercent = DEFAULT_LPR_ANNUAL_PERCENT;
        }
        if (lprAnnualPercent.signum() <= 0)
        {
            throw new IllegalArgumentException("参数 params.lprAnnualPercent 必须大于 0");
        }
        Integer basisValue = optionalInt(params, "dayCountBasis");
        int dayCountBasis = basisValue == null ? 365 : basisValue;
        if (dayCountBasis != 365 && dayCountBasis != 360)
        {
            throw new IllegalArgumentException("参数 params.dayCountBasis 仅支持 365 或 360，当前值为 " + dayCountBasis);
        }

        BigDecimal lpr4xPercent = lprAnnualPercent.multiply(BigDecimal.valueOf(4));
        BigDecimal daysDecimal = BigDecimal.valueOf(days);
        BigDecimal basisDecimal = BigDecimal.valueOf(dayCountBasis);
        BigDecimal agreedInterest = money(principal
                .multiply(annualRatePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .multiply(daysDecimal).divide(basisDecimal, 10, RoundingMode.HALF_UP));
        BigDecimal effectiveRatePercent = annualRatePercent.min(lpr4xPercent);
        BigDecimal protectedInterest = money(principal
                .multiply(effectiveRatePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP)
                .multiply(daysDecimal).divide(basisDecimal, 10, RoundingMode.HALF_UP));
        boolean exceedsLpr4x = annualRatePercent.compareTo(lpr4xPercent) > 0;
        BigDecimal excessAmount = money(agreedInterest.subtract(protectedInterest));
        BigDecimal totalPayable = money(principal.add(protectedInterest));

        String formula = "agreedInterest = " + moneyText(principal) + " × " + plain(annualRatePercent) + "% × "
                + days + " / " + dayCountBasis + " = " + moneyText(agreedInterest)
                + "；protectedInterest = " + moneyText(principal) + " × " + plain(effectiveRatePercent) + "% × "
                + days + " / " + dayCountBasis + " = " + moneyText(protectedInterest)
                + "；totalPayable = 本金 + protectedInterest = " + moneyText(totalPayable);

        List<String> steps = new ArrayList<>();
        steps.add("LPR 四倍红线 = " + plain(lprAnnualPercent) + "% × 4 = " + plain(lpr4xPercent) + "%");
        steps.add("约定利息 = " + moneyText(principal) + " × " + plain(annualRatePercent) + "% × " + days + " / "
                + dayCountBasis + " = " + moneyText(agreedInterest));
        steps.add("司法保护利率 = min(" + plain(annualRatePercent) + "%, " + plain(lpr4xPercent) + "%) = "
                + plain(effectiveRatePercent) + "%");
        steps.add("保护范围内利息 = " + moneyText(principal) + " × " + plain(effectiveRatePercent) + "% × " + days
                + " / " + dayCountBasis + " = " + moneyText(protectedInterest));
        if (exceedsLpr4x)
        {
            steps.add("约定利率超过 LPR 四倍，超出部分 " + moneyText(excessAmount) + " 不受司法保护");
        }
        else
        {
            steps.add("约定利率未超过 LPR 四倍，约定利息均在司法保护范围内");
        }
        steps.add("本息合计（按司法保护上限）= " + moneyText(totalPayable));

        String summary = "约定利息 ¥" + moneyText(agreedInterest) + "；LPR 四倍（" + plain(lpr4xPercent)
                + "%）保护范围内利息 ¥" + moneyText(protectedInterest) + "；超出部分 ¥" + moneyText(excessAmount)
                + "；本息合计 ¥" + moneyText(totalPayable);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("calcType", TYPE_LOAN_INTEREST);
        detail.put("principal", principal.setScale(2, RoundingMode.HALF_UP));
        detail.put("days", days);
        detail.put("annualRatePercent", annualRatePercent.setScale(2, RoundingMode.HALF_UP));
        detail.put("lprAnnualPercent", lprAnnualPercent.setScale(2, RoundingMode.HALF_UP));
        detail.put("lpr4xPercent", lpr4xPercent.setScale(2, RoundingMode.HALF_UP));
        detail.put("exceedsLpr4x", exceedsLpr4x);
        detail.put("agreedInterest", agreedInterest);
        detail.put("protectedInterest", protectedInterest);
        detail.put("excessAmount", excessAmount);
        detail.put("amount", protectedInterest);
        detail.put("totalPayable", totalPayable);
        detail.put("currency", "CNY");
        detail.put("formula", formula);
        detail.put("steps", steps);
        detail.put("legalBasis", LOAN_LEGAL_BASIS);
        detail.put("summary", summary);
        return detail;
    }

    // ------------------------------------------------------------------
    // 3. 诉讼 / 劳动仲裁时效
    // ------------------------------------------------------------------

    /**
     * 推算诉讼/仲裁时效截止日、剩余天数与是否已过期
     *
     * @param params 参数
     * @return 结构化结果
     */
    public Map<String, Object> calculateLimitationPeriod(Map<String, Object> params)
    {
        String caseType = requiredString(params, "caseType", "案件类型 caseType");
        int periodYears;
        if ("civil".equals(caseType))
        {
            periodYears = 3;
        }
        else if ("labor_arbitration".equals(caseType))
        {
            periodYears = 1;
        }
        else
        {
            throw new IllegalArgumentException("参数 params.caseType 仅支持 civil | labor_arbitration，当前值为 "
                    + caseType);
        }
        String knowledgeDateValue = requiredString(params, "knowledgeDate", "知道权利受损之日 knowledgeDate");
        LocalDate knowledgeDate = parseDate(knowledgeDateValue, "knowledgeDate");
        String referenceDateValue = optionalString(params, "referenceDate");
        LocalDate referenceDate = referenceDateValue == null ? LocalDate.now() : parseDate(referenceDateValue,
                "referenceDate");

        LocalDate deadlineDate = knowledgeDate.plusYears(periodYears);
        long remainingDays = ChronoUnit.DAYS.between(referenceDate, deadlineDate);
        boolean expired = remainingDays < 0;

        String caseLabel = "civil".equals(caseType) ? "普通民事诉讼时效" : "劳动仲裁时效";
        String formula = "deadlineDate = " + knowledgeDate + " + " + periodYears + " 年 = " + deadlineDate
                + "；remainingDays = " + deadlineDate + " - " + referenceDate + " = " + remainingDays + " 天";
        List<String> steps = new ArrayList<>();
        steps.add(caseLabel + "期间 = " + periodYears + " 年（自知道或应当知道权利受到损害之日起算）");
        steps.add("起算日 = " + knowledgeDate + "，截止日 = 起算日 + " + periodYears + " 年 = " + deadlineDate);
        steps.add("参考日 = " + referenceDate + "，剩余天数 = " + remainingDays + " 天");
        steps.add(expired ? "已超过时效期间，对方可提出时效抗辩，建议核查是否存在中止、中断事由"
                : "尚在时效期间内，请在截止日前及时主张权利并留存证据");

        String summary = (expired ? "已超过" : "尚在") + caseLabel + "期间：截止日 " + deadlineDate
                + (expired ? "，已逾期 " + Math.abs(remainingDays) + " 天" : "，剩余 " + remainingDays + " 天");

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("calcType", TYPE_LIMITATION_PERIOD);
        detail.put("caseType", caseType);
        detail.put("periodYears", periodYears);
        detail.put("knowledgeDate", knowledgeDate.toString());
        detail.put("referenceDate", referenceDate.toString());
        detail.put("deadlineDate", deadlineDate.toString());
        detail.put("remainingDays", remainingDays);
        detail.put("expired", expired);
        detail.put("formula", formula);
        detail.put("steps", steps);
        detail.put("legalBasis", LIMITATION_LEGAL_BASIS);
        detail.put("summary", summary);
        return detail;
    }

    // ------------------------------------------------------------------
    // 参数读取与格式化
    // ------------------------------------------------------------------

    private BigDecimal requiredPositive(Map<String, Object> params, String key, String label)
    {
        BigDecimal value = optionalNumber(params, key);
        if (value == null)
        {
            throw new IllegalArgumentException("参数 params." + key + "（" + label + "）为必填项");
        }
        if (value.signum() <= 0)
        {
            throw new IllegalArgumentException("参数 params." + key + "（" + label + "）必须大于 0");
        }
        return value;
    }

    private BigDecimal requiredNonNegative(Map<String, Object> params, String key, String label)
    {
        BigDecimal value = optionalNumber(params, key);
        if (value == null)
        {
            throw new IllegalArgumentException("参数 params." + key + "（" + label + "）为必填项");
        }
        if (value.signum() < 0)
        {
            throw new IllegalArgumentException("参数 params." + key + "（" + label + "）不能为负数");
        }
        return value;
    }

    private String requiredString(Map<String, Object> params, String key, String label)
    {
        String value = optionalString(params, key);
        if (value == null)
        {
            throw new IllegalArgumentException("参数 params." + key + "（" + label + "）为必填项");
        }
        return value;
    }

    private String optionalString(Map<String, Object> params, String key)
    {
        Object value = params.get(key);
        if (value == null)
        {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private BigDecimal optionalNumber(Map<String, Object> params, String key)
    {
        Object value = params.get(key);
        if (value == null)
        {
            return null;
        }
        if (value instanceof BigDecimal decimal)
        {
            return decimal;
        }
        try
        {
            String text = value.toString().trim();
            if (text.isEmpty())
            {
                return null;
            }
            BigDecimal number = new BigDecimal(text);
            return number;
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("参数 params." + key + " 必须为数字，当前值为 " + value);
        }
    }

    private Integer optionalInt(Map<String, Object> params, String key)
    {
        Object value = params.get(key);
        if (value == null)
        {
            return null;
        }
        try
        {
            String text = value.toString().trim();
            if (text.isEmpty())
            {
                return null;
            }
            return new BigDecimal(text).intValueExact();
        }
        catch (ArithmeticException | NumberFormatException e)
        {
            throw new IllegalArgumentException("参数 params." + key + " 必须为整数，当前值为 " + value);
        }
    }

    private LocalDate parseDate(String value, String key)
    {
        try
        {
            return LocalDate.parse(value, DATE_FORMATTER);
        }
        catch (DateTimeParseException e)
        {
            throw new IllegalArgumentException("参数 params." + key + " 日期格式应为 yyyy-MM-dd（例如 2024-01-01），"
                    + "当前值为 " + value);
        }
    }

    private BigDecimal money(BigDecimal value)
    {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String moneyText(BigDecimal value)
    {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(java.util.Locale.US));
        return format.format(value.setScale(2, RoundingMode.HALF_UP));
    }

    private String plain(BigDecimal value)
    {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() <= 0 ? stripped.toBigInteger().toString() : stripped.toPlainString();
    }
}
