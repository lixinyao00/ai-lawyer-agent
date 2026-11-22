package com.ruoyi.lawyer.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 确定性法律金额/时效计算器单元测试
 *
 * @author ruoyi
 */
class LegalAmountCalculatorTest
{
    private final LegalAmountCalculator calculator = new LegalAmountCalculator();

    @Test
    void laborCompensation2nForThreeAndHalfYears()
    {
        Map<String, Object> params = laborParams("10000", "2n", "3.5", null, null, null);

        Map<String, Object> detail = calculator.calculateLaborCompensation(params);

        assertEquals(0, amount(detail, "amount").compareTo(new BigDecimal("80000.00")));
        assertEquals(0, amount(detail, "countedMonths").compareTo(new BigDecimal("4.0")));
        assertEquals(0, amount(detail, "baseSalary").compareTo(new BigDecimal("10000")));
        assertEquals(Boolean.FALSE, detail.get("capped"));
        assertEquals("2N 违法解除赔偿金：¥80,000.00（工作3.5年，折算4个月×2）", detail.get("summary"));
        assertTrue(((java.util.List<?>) detail.get("steps")).size() >= 3);
        assertTrue(detail.get("formula").toString().contains("2N"));
    }

    @Test
    void laborCompensationUsesDatesAndRoundsRemainderMonths()
    {
        Map<String, Object> params = laborParams("10000", "n", null, "2020-01-01", "2022-08-15", null);

        Map<String, Object> detail = calculator.calculateLaborCompensation(params);

        // 2 年 7 个月，满半年不满一年按 1 个月计 -> 3 个月
        assertEquals(0, amount(detail, "countedMonths").compareTo(new BigDecimal("3.0")));
        assertEquals(0, amount(detail, "amount").compareTo(new BigDecimal("30000.00")));
    }

    @Test
    void laborCompensationRoundsLessThanHalfYearToHalfMonth()
    {
        Map<String, Object> detail = calculator.calculateLaborCompensation(
                laborParams("10000", "n", "2.2", null, null, null));

        assertEquals(0, amount(detail, "countedMonths").compareTo(new BigDecimal("2.5")));
        assertEquals(0, amount(detail, "amount").compareTo(new BigDecimal("25000.00")));
    }

    @Test
    void laborCompensationAppliesThreeTimesCapAndTwelveMonthsLimit()
    {
        Map<String, Object> detail = calculator.calculateLaborCompensation(
                laborParams("20000", "n", "13", null, null, "15000"));

        assertEquals(Boolean.TRUE, detail.get("capped"));
        assertEquals(0, amount(detail, "baseSalary").compareTo(new BigDecimal("15000")));
        assertEquals(0, amount(detail, "countedMonths").compareTo(new BigDecimal("12.0")));
        assertEquals(0, amount(detail, "amount").compareTo(new BigDecimal("180000.00")));
    }

    @Test
    void laborCompensationNPlusOneAddsNoticePay()
    {
        Map<String, Object> params = laborParams("10000", "n_plus_1", "3", null, null, null);
        params.put("lastMonthSalary", "8000");

        Map<String, Object> detail = calculator.calculateLaborCompensation(params);

        assertEquals(0, amount(detail, "noticePay").compareTo(new BigDecimal("8000.00")));
        assertEquals(0, amount(detail, "amount").compareTo(new BigDecimal("38000.00")));
    }

    @Test
    void laborCompensationRejectsInvalidDatesAndTerminationType()
    {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLaborCompensation(
                laborParams("10000", "n", null, "2022-01-01", "2021-01-01", null)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLaborCompensation(
                laborParams("10000", "bad", "3", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLaborCompensation(
                laborParams("-1", "n", "3", null, null, null)));
    }

    @Test
    void loanInterestCapsAtLprFourTimes()
    {
        Map<String, Object> params = loanParams("100000", "18", 365, null, null, null, null);

        Map<String, Object> detail = calculator.calculateLoanInterest(params);

        assertEquals(0, amount(detail, "agreedInterest").compareTo(new BigDecimal("18000.00")));
        assertEquals(0, amount(detail, "lpr4xPercent").compareTo(new BigDecimal("12.40")));
        assertEquals(0, amount(detail, "protectedInterest").compareTo(new BigDecimal("12400.00")));
        assertEquals(0, amount(detail, "excessAmount").compareTo(new BigDecimal("5600.00")));
        assertEquals(0, amount(detail, "totalPayable").compareTo(new BigDecimal("112400.00")));
        assertEquals(Boolean.TRUE, detail.get("exceedsLpr4x"));
    }

    @Test
    void loanInterestCalculatesDaysFromDatesWith360Basis()
    {
        Map<String, Object> params = loanParams("10000", "12", null, "2024-01-01", "2024-01-31", "3.1",
                "360");

        Map<String, Object> detail = calculator.calculateLoanInterest(params);

        assertEquals(30, detail.get("days"));
        assertEquals(0, amount(detail, "agreedInterest").compareTo(new BigDecimal("100.00")));
        assertEquals(0, amount(detail, "protectedInterest").compareTo(new BigDecimal("100.00")));
        assertEquals(0, amount(detail, "excessAmount").compareTo(BigDecimal.ZERO));
        assertEquals(0, amount(detail, "totalPayable").compareTo(new BigDecimal("10100.00")));
        assertEquals(Boolean.FALSE, detail.get("exceedsLpr4x"));
    }

    @Test
    void loanInterestRejectsInvalidDatesAndBasis()
    {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLoanInterest(
                loanParams("10000", "10", null, "2024-02-01", "2024-02-01", null, null)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLoanInterest(
                loanParams("10000", "10", 30, null, null, null, "300")));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLoanInterest(
                loanParams("10000", "-1", 30, null, null, null, null)));
    }

    @Test
    void limitationPeriodCivilExpired()
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseType", "civil");
        params.put("knowledgeDate", "2020-01-01");
        params.put("referenceDate", "2024-01-01");

        Map<String, Object> detail = calculator.calculateLimitationPeriod(params);

        assertEquals("2023-01-01", detail.get("deadlineDate"));
        assertEquals(-365L, detail.get("remainingDays"));
        assertEquals(Boolean.TRUE, detail.get("expired"));
        assertEquals(3, detail.get("periodYears"));
        assertTrue(detail.get("summary").toString().contains("已超过"));
    }

    @Test
    void limitationPeriodLaborArbitrationNotExpired()
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseType", "labor_arbitration");
        params.put("knowledgeDate", "2025-01-01");
        params.put("referenceDate", "2025-06-01");

        Map<String, Object> detail = calculator.calculateLimitationPeriod(params);

        assertEquals("2026-01-01", detail.get("deadlineDate"));
        assertEquals(214L, detail.get("remainingDays"));
        assertEquals(Boolean.FALSE, detail.get("expired"));
        assertEquals(1, detail.get("periodYears"));
    }

    @Test
    void limitationPeriodDefaultsReferenceDateToToday()
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseType", "civil");
        params.put("knowledgeDate", LocalDate.now().minusYears(1).toString());

        Map<String, Object> detail = calculator.calculateLimitationPeriod(params);

        assertEquals(LocalDate.now().toString(), detail.get("referenceDate"));
        assertTrue((Long) detail.get("remainingDays") > 0);
        assertFalse((Boolean) detail.get("expired"));
    }

    @Test
    void calculateRejectsUnknownTypeAndBadDateFormat()
    {
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate("unknown", Map.of()));
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("caseType", "civil");
        params.put("knowledgeDate", "2020/01/01");
        assertThrows(IllegalArgumentException.class, () -> calculator.calculateLimitationPeriod(params));
        assertEquals(ChronoUnit.DAYS.between(LocalDate.parse("2025-01-01"),
                LocalDate.parse("2026-01-01")), 365L);
    }

    @Test
    void calculateDispatchesByType()
    {
        Map<String, Object> detail = calculator.calculate("labor_compensation",
                laborParams("10000", "2n", "3.5", null, null, null));

        assertEquals("labor_compensation", detail.get("calcType"));
        assertNull(detail.get("unknownField"));
    }

    private Map<String, Object> laborParams(String monthlySalary, String terminationType, String yearsOfService,
            String entryDate, String leaveDate, String localAvgSalary3x)
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("monthlySalary", monthlySalary);
        params.put("terminationType", terminationType);
        if (yearsOfService != null)
        {
            params.put("yearsOfService", yearsOfService);
        }
        if (entryDate != null)
        {
            params.put("entryDate", entryDate);
        }
        if (leaveDate != null)
        {
            params.put("leaveDate", leaveDate);
        }
        if (localAvgSalary3x != null)
        {
            params.put("localAvgSalary3x", localAvgSalary3x);
        }
        return params;
    }

    private Map<String, Object> loanParams(String principal, String annualRatePercent, Integer days, String startDate,
            String endDate, String lprAnnualPercent, String dayCountBasis)
    {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("principal", principal);
        params.put("annualRatePercent", annualRatePercent);
        if (days != null)
        {
            params.put("days", days);
        }
        if (startDate != null)
        {
            params.put("startDate", startDate);
        }
        if (endDate != null)
        {
            params.put("endDate", endDate);
        }
        if (lprAnnualPercent != null)
        {
            params.put("lprAnnualPercent", lprAnnualPercent);
        }
        if (dayCountBasis != null)
        {
            params.put("dayCountBasis", dayCountBasis);
        }
        return params;
    }

    private BigDecimal amount(Map<String, Object> detail, String key)
    {
        return (BigDecimal) detail.get(key);
    }
}
