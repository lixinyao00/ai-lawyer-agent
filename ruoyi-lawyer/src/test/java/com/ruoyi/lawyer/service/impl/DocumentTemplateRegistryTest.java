package com.ruoyi.lawyer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import com.ruoyi.lawyer.vo.FieldDef;
import tools.jackson.databind.ObjectMapper;

/**
 * DocumentTemplateRegistry 单元测试
 *
 * @author ruoyi
 */
class DocumentTemplateRegistryTest
{
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "text", "textarea", "date", "number", "money", "select", "phone", "idcard");

    private final DocumentTemplateRegistry registry = new DocumentTemplateRegistry(new ObjectMapper());

    @Test
    void loadsFourTemplatesWithUniqueCodes()
    {
        List<DocumentTemplateVO> templates = registry.listTemplates();

        assertEquals(4, templates.size());
        Set<String> codes = new HashSet<>();
        for (DocumentTemplateVO template : templates)
        {
            assertNotNull(template.getCode());
            assertNotNull(template.getName());
            assertNotNull(template.getDescription());
            assertTrue(codes.add(template.getCode()), "模板 code 必须唯一");
        }
        assertEquals(Set.of("labor_arbitration", "payment_demand", "loan_agreement", "lease_termination"),
                codes);
    }

    @Test
    void exposesTemplatesInConfiguredOrder()
    {
        List<DocumentTemplateVO> templates = registry.listTemplates();

        assertEquals("labor_arbitration", templates.get(0).getCode());
        assertEquals("劳动仲裁申请书", templates.get(0).getName());
        assertEquals("payment_demand", templates.get(1).getCode());
        assertEquals("loan_agreement", templates.get(2).getCode());
        assertEquals("lease_termination", templates.get(3).getCode());
    }

    @Test
    void loadsFieldDefinitionsWithRequiredMetadata()
    {
        for (DocumentTemplateVO template : registry.listTemplates())
        {
            Set<String> keys = new HashSet<>();
            for (FieldDef field : template.getFields())
            {
                assertTrue(keys.add(field.getKey()), "字段 key 必须唯一: " + template.getCode());
                assertNotNull(field.getLabel());
                assertTrue(ALLOWED_TYPES.contains(field.getType()),
                        "未知字段类型: " + field.getType());
                assertNotNull(field.getRequired());
                assertNotNull(field.getPlaceholder());
                assertNotNull(field.getMaxLength());
                assertNotNull(field.getGroup());
                if ("select".equals(field.getType()))
                {
                    assertTrue(!field.getOptions().isEmpty(), "select 必须有 options");
                }
            }
        }
    }

    @Test
    void paymentDemandDeadlineDefaultsToSeven()
    {
        DocumentTemplateVO template = registry.getByCode("payment_demand");
        FieldDef deadline = findField(template, "deadlineDays");

        assertEquals("number", deadline.getType());
        assertEquals(Boolean.TRUE, deadline.getRequired());
        assertEquals(7, ((Number) deadline.getDefaultValue()).intValue());
    }

    @Test
    void loanTemplateHasSelectAndIdCardFields()
    {
        DocumentTemplateVO template = registry.getByCode("loan_agreement");

        assertEquals(4, findField(template, "paymentMethod").getOptions().size());
        assertEquals("idcard", findField(template, "lenderIdCard").getType());
        assertEquals("idcard", findField(template, "borrowerIdCard").getType());
        assertEquals("number", findField(template, "annualInterestRate").getType());
        assertEquals(2, findField(template, "disputeCourt").getOptions().size());
    }

    @Test
    void looksUpTemplateByCode()
    {
        DocumentTemplateVO template = registry.getByCode("lease_termination");

        assertNotNull(template);
        assertEquals("房屋租赁解约通知书", template.getName());
        assertNull(registry.getByCode("not_exists"));
        assertNull(registry.getByCode(null));
    }

    private FieldDef findField(DocumentTemplateVO template, String key)
    {
        return template.getFields().stream()
                .filter(field -> key.equals(field.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少字段: " + key));
    }
}
