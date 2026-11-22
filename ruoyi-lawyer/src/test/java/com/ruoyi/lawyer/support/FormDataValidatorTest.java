package com.ruoyi.lawyer.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.service.impl.DocumentTemplateRegistry;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import tools.jackson.databind.ObjectMapper;

/**
 * FormDataValidator 单元测试
 *
 * @author ruoyi
 */
class FormDataValidatorTest
{
    private final DocumentTemplateRegistry registry = new DocumentTemplateRegistry(new ObjectMapper());

    private final FormDataValidator validator = new FormDataValidator();

    private final DocumentTemplateVO template = registry.getByCode("labor_arbitration");

    @Test
    void acceptsAndNormalizesValidFormData()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantName", "  张三  ");
        formData.put("monthlySalary", "8000.50");
        formData.put("applicantBirth", "1990-01-01");

        Map<String, Object> cleaned = validator.validate(template, formData);

        assertEquals("张三", cleaned.get("applicantName"));
        assertEquals("1990-01-01", cleaned.get("applicantBirth"));
        assertEquals(new BigDecimal("8000.50"), cleaned.get("monthlySalary"));
        assertEquals("男", cleaned.get("applicantGender"));
        // 未提供的非必填字段不进入清洗结果
        assertFalse(cleaned.containsKey("leaveDate"));
    }

    @Test
    void rejectsMissingRequiredField()
    {
        Map<String, Object> formData = validLaborForm();
        formData.remove("applicantName");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertEquals(422, error.getCode());
        assertTrue(error.getMessage().contains("【姓名】"));
        assertTrue(error.getMessage().contains("不能为空"));
    }

    @Test
    void rejectsInvalidDate()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantBirth", "2026-02-30");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertEquals(422, error.getCode());
        assertTrue(error.getMessage().contains("【出生日期】"));
    }

    @Test
    void rejectsIllegalDatePattern()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantBirth", "1990/01/01");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【出生日期】"));
    }

    @Test
    void rejectsNegativeMoney()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("monthlySalary", "-1.00");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【月工资(元)】"));
        assertTrue(error.getMessage().contains("不能小于 0"));
    }

    @Test
    void rejectsMoneyWithMoreThanTwoDecimals()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("monthlySalary", "8000.123");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【月工资(元)】"));
        assertTrue(error.getMessage().contains("2 位小数"));
    }

    @Test
    void rejectsNonNumericMoney()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("monthlySalary", "八千元");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【月工资(元)】"));
    }

    @Test
    void rejectsInvalidPhone()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantPhone", "12345");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【联系电话】"));
        assertTrue(error.getMessage().contains("手机号"));
    }

    @Test
    void acceptsLandlinePhone()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantPhone", "010-12345678");

        Map<String, Object> cleaned = validator.validate(template, formData);

        assertEquals("010-12345678", cleaned.get("applicantPhone"));
    }

    @Test
    void rejectsInvalidIdCard()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantIdCard", "11010119900101123");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【身份证号】"));
    }

    @Test
    void acceptsIdCardEndingWithX()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantIdCard", "11010119900101123X");

        Map<String, Object> cleaned = validator.validate(template, formData);

        assertEquals("11010119900101123X", cleaned.get("applicantIdCard"));
    }

    @Test
    void rejectsSelectValueOutsideOptions()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantGender", "未知");

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【性别】"));
        assertTrue(error.getMessage().contains("可选范围"));
    }

    @Test
    void rejectsTextLongerThanDefaultMaxLength()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("applicantAddress", "住址".repeat(120));

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【住址】"));
        assertTrue(error.getMessage().contains("200"));
    }

    @Test
    void rejectsTextareaLongerThanMaxLength()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("factsAndReasons", "事实".repeat(1100));

        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(template, formData));

        assertTrue(error.getMessage().contains("【事实与理由】"));
        assertTrue(error.getMessage().contains("2000"));
    }

    @Test
    void dropsUnknownKeys()
    {
        Map<String, Object> formData = validLaborForm();
        formData.put("notAField", "should be dropped");
        formData.put("__proto__", "x");

        Map<String, Object> cleaned = validator.validate(template, formData);

        assertFalse(cleaned.containsKey("notAField"));
        assertFalse(cleaned.containsKey("__proto__"));
    }

    @Test
    void validatesNumberField()
    {
        DocumentTemplateVO payment = registry.getByCode("payment_demand");
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("lawFirmName", "XX律师事务所");
        formData.put("lawyerName", "王律师");
        formData.put("clientName", "张三");
        formData.put("debtorName", "李四");
        formData.put("debtBasis", "借款到期未还。");
        formData.put("principalAmount", "50000");
        formData.put("deadlineDays", 7);
        formData.put("issueDate", "2026-11-21");

        Map<String, Object> cleaned = validator.validate(payment, formData);
        assertEquals(new BigDecimal("7"), cleaned.get("deadlineDays"));

        formData.put("deadlineDays", "七天");
        ServiceException error = assertThrows(ServiceException.class,
                () -> validator.validate(payment, formData));
        assertTrue(error.getMessage().contains("【履行期限(天)】"));
    }

    private Map<String, Object> validLaborForm()
    {
        Map<String, Object> formData = new LinkedHashMap<>();
        formData.put("applicantName", "张三");
        formData.put("applicantGender", "男");
        formData.put("applicantIdCard", "110101199001011234");
        formData.put("applicantPhone", "13800138000");
        formData.put("applicantAddress", "北京市朝阳区XX路1号");
        formData.put("employerName", "XX有限公司");
        formData.put("employerAddress", "北京市海淀区XX路2号");
        formData.put("entryDate", "2020-03-01");
        formData.put("monthlySalary", "8000.00");
        formData.put("arbitrationCommission", "北京市劳动人事争议仲裁委员会");
        formData.put("claims", "请求裁决被申请人支付工资及经济补偿金。");
        formData.put("factsAndReasons", "申请人于2020年3月入职，用人单位违法解除劳动合同。");
        return formData;
    }
}
