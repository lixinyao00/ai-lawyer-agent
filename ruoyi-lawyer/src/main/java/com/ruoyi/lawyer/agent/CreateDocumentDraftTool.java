package com.ruoyi.lawyer.agent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.dto.CreateDraftRequest;
import com.ruoyi.lawyer.service.ILawDocumentService;
import com.ruoyi.lawyer.service.impl.DocumentTemplateRegistry;
import com.ruoyi.lawyer.support.FormDataValidator;
import com.ruoyi.lawyer.vo.DocumentDraftVO;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import com.ruoyi.lawyer.vo.FieldDef;
import tools.jackson.databind.ObjectMapper;

/**
 * 工具 3：在对话中校验要素并生成可编辑/导出的法律文书草稿
 *
 * @author ruoyi
 */
@Component
public class CreateDocumentDraftTool implements LawAgentTool
{
    /** 工具名 */
    public static final String TOOL_NAME = "create_document_draft";

    private static final int MAX_TITLE_LENGTH = 200;

    /** 合法模板编码及中文名（保持稳定顺序用于错误提示） */
    private static final Map<String, String> TEMPLATE_NAMES;

    static
    {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("labor_arbitration", "劳动仲裁申请书");
        names.put("payment_demand", "律师催款函");
        names.put("loan_agreement", "规范借条");
        names.put("lease_termination", "房屋租赁解约通知书");
        TEMPLATE_NAMES = java.util.Collections.unmodifiableMap(names);
    }

    private final DocumentTemplateRegistry templateRegistry;

    private final FormDataValidator formDataValidator;

    private final ILawDocumentService lawDocumentService;

    private final ObjectMapper objectMapper;

    @Autowired
    public CreateDocumentDraftTool(DocumentTemplateRegistry templateRegistry, FormDataValidator formDataValidator,
            ILawDocumentService lawDocumentService, ObjectMapper objectMapper)
    {
        this.templateRegistry = templateRegistry;
        this.formDataValidator = formDataValidator;
        this.lawDocumentService = lawDocumentService;
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
        return "当用户明确要求起草法律文书且关键要素齐备时，校验模板要素并生成可在线编辑、导出 DOCX 的文书草稿。";
    }

    @Override
    public String displayTitle(Map<String, Object> arguments)
    {
        Object code = arguments == null ? null : arguments.get("templateCode");
        String name = code == null ? null : TEMPLATE_NAMES.get(code.toString().trim());
        return name == null ? "正在起草法律文书..." : "正在起草《" + name + "》...";
    }

    @Override
    public Map<String, Object> parametersSchema()
    {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("templateCode", Map.of("type", "string",
                "enum", List.of("labor_arbitration", "payment_demand", "loan_agreement", "lease_termination"),
                "description", "文书模板编码"));
        properties.put("title", Map.of("type", "string", "description", "文书标题，可选，不超过 200 字"));
        properties.put("formData", Map.of("type", "object",
                "description", "对应模板的要素键值对，日期 yyyy-MM-dd，金额为数字"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("templateCode", "formData"));
        return schema;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments)
    {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Object codeValue = args.get("templateCode");
        String templateCode = codeValue == null ? null : codeValue.toString().trim();
        if (templateCode == null || templateCode.isEmpty())
        {
            return ToolExecutionResult.error("文书模板缺失", "参数 templateCode 为必填项，可选值："
                    + templateOptions() + "。");
        }
        if (!TEMPLATE_NAMES.containsKey(templateCode))
        {
            return ToolExecutionResult.error("文书模板不存在", "参数 templateCode 非法：" + templateCode
                    + "，可选值：" + templateOptions() + "。请选择正确模板后重试。");
        }
        Object titleValue = args.get("title");
        String title = titleValue == null ? null : titleValue.toString().trim();
        if (title != null && title.length() > MAX_TITLE_LENGTH)
        {
            return ToolExecutionResult.error("文书标题过长",
                    "参数 title 长度不能超过 " + MAX_TITLE_LENGTH + " 字，请精简或省略后重试。");
        }
        Object formDataValue = args.get("formData");
        if (!(formDataValue instanceof Map<?, ?> rawFormData))
        {
            return ToolExecutionResult.error("文书要素缺失", "参数 formData 为必填对象，请提供模板要求的要素键值对。");
        }
        Map<String, Object> formData = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawFormData.entrySet())
        {
            formData.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        DocumentTemplateVO template = templateRegistry.get(templateCode);
        if (template == null)
        {
            return ToolExecutionResult.error("文书模板不存在", "文书模板 " + templateCode + " 不存在，可选值："
                    + templateOptions() + "。");
        }
        List<String> missing = findMissingRequired(template, formData);
        Map<String, Object> validated;
        try
        {
            validated = formDataValidator.validate(template, formData);
        }
        catch (ServiceException e)
        {
            return validationError(template, missing, e.getMessage());
        }
        catch (Exception e)
        {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return validationError(template, missing, message);
        }

        CreateDraftRequest request = new CreateDraftRequest();
        request.setTemplateCode(templateCode);
        request.setTitle(title);
        request.setFormData(validated);
        DocumentDraftVO draft;
        try
        {
            if (context == null || context.getUserId() == null)
            {
                return ToolExecutionResult.error("用户身份缺失", "当前上下文缺少 userId，无法创建文书草稿，请提示用户登录后重试。");
            }
            draft = lawDocumentService.createDraft(context.getUserId(), request);
        }
        catch (ServiceException e)
        {
            return ToolExecutionResult.error("文书生成失败",
                    "文书草稿创建失败：" + e.getMessage() + "。请核对要素后重试，或向用户追问缺失信息。");
        }
        catch (Exception e)
        {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return ToolExecutionResult.error("文书生成失败",
                    "文书草稿创建异常（" + message + "），请稍后重试或向用户说明。");
        }

        Long draftId = draft == null ? null : draft.getId();
        String draftTitle = draft == null || draft.getTitle() == null ? TEMPLATE_NAMES.get(templateCode)
                : draft.getTitle();
        String status = draft == null ? null : draft.getStatus();
        String previewPath = draftId == null ? null : "/lawyer-workspace/document?draftId=" + draftId;
        String summary = "已生成《" + draftTitle + "》文书草稿";

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("draftId", draftId);
        detail.put("title", draftTitle);
        detail.put("templateCode", templateCode);
        detail.put("templateName", template.getName());
        detail.put("status", status);
        detail.put("previewPath", previewPath);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("status", "success");
        content.put("draftId", draftId);
        content.put("title", draftTitle);
        content.put("templateName", template.getName());
        content.put("previewPath", previewPath);
        content.put("message", summary + "，用户可在工作台继续编辑或导出 DOCX。");
        String modelContent;
        try
        {
            modelContent = objectMapper.writeValueAsString(content);
        }
        catch (Exception e)
        {
            modelContent = summary;
        }
        return ToolExecutionResult.success(summary, detail, modelContent, draftId, List.of());
    }

    private ToolExecutionResult validationError(DocumentTemplateVO template, List<String> missing, String reason)
    {
        String summary = "《" + template.getName() + "》要素校验未通过：" + reason;
        StringBuilder message = new StringBuilder("文书要素校验未通过：").append(reason).append("。");
        if (!missing.isEmpty())
        {
            message.append("缺失必填字段：").append(String.join("、", missing)).append("。");
        }
        message.append("字段要求：");
        List<String> hints = new ArrayList<>();
        for (FieldDef field : template.getFields())
        {
            hints.add(field.getKey() + "（" + field.getLabel() + "，" + describeField(field) + "）");
        }
        message.append(String.join("；", hints));
        message.append("。请从上文提取补齐后重新调用本工具，或直接向用户一次性礼貌追问缺失要素。");
        return ToolExecutionResult.error(summary, message.toString());
    }

    private List<String> findMissingRequired(DocumentTemplateVO template, Map<String, Object> formData)
    {
        List<String> missing = new ArrayList<>();
        for (FieldDef field : template.getFields())
        {
            if (!Boolean.TRUE.equals(field.getRequired()))
            {
                continue;
            }
            if (isEmpty(formData.get(field.getKey())))
            {
                missing.add(field.getKey() + "（" + field.getLabel() + "）");
            }
        }
        return missing;
    }

    private String describeField(FieldDef field)
    {
        String type = field.getType() == null ? "text" : field.getType();
        String format = switch (type)
        {
            case "date" -> "日期格式 yyyy-MM-dd";
            case "money" -> "金额数字，最多 2 位小数";
            case "number" -> "数字";
            case "phone" -> "手机号或固定电话";
            case "idcard" -> "18 位身份证号";
            case "select" -> "可选值 " + field.getOptions();
            case "textarea" -> "文本，不超过 " + (field.getMaxLength() == null ? 2000 : field.getMaxLength()) + " 字";
            default -> "文本，不超过 " + (field.getMaxLength() == null ? 200 : field.getMaxLength()) + " 字";
        };
        boolean required = Boolean.TRUE.equals(field.getRequired());
        return (required ? "必填" : "可选") + "，" + format;
    }

    private boolean isEmpty(Object value)
    {
        if (value == null)
        {
            return true;
        }
        if (value instanceof CharSequence text)
        {
            return text.toString().trim().isEmpty();
        }
        if (value instanceof Collection<?> collection)
        {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map)
        {
            return map.isEmpty();
        }
        return false;
    }

    private String templateOptions()
    {
        List<String> options = new ArrayList<>();
        for (Map.Entry<String, String> entry : TEMPLATE_NAMES.entrySet())
        {
            options.add(entry.getKey() + "（" + entry.getValue() + "）");
        }
        return String.join("、", options);
    }
}
