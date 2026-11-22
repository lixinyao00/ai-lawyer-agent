package com.ruoyi.lawyer.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiChatClient.ChatMessage;
import com.ruoyi.lawyer.client.AiClientException;
import com.ruoyi.lawyer.config.LawyerAiProperties;
import com.ruoyi.lawyer.docx.DocxDocumentExporter;
import com.ruoyi.lawyer.domain.LawDocumentDraft;
import com.ruoyi.lawyer.dto.CreateDraftRequest;
import com.ruoyi.lawyer.dto.DraftQuery;
import com.ruoyi.lawyer.dto.UpdateDraftRequest;
import com.ruoyi.lawyer.mapper.LawDocumentDraftMapper;
import com.ruoyi.lawyer.service.ILawDocumentService;
import com.ruoyi.lawyer.support.FormDataValidator;
import com.ruoyi.lawyer.vo.DocumentDraftListVO;
import com.ruoyi.lawyer.vo.DocumentDraftVO;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import com.ruoyi.lawyer.vo.FieldDef;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 法律文书草稿 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawDocumentServiceImpl implements ILawDocumentService
{
    private static final Logger log = LoggerFactory.getLogger(LawDocumentServiceImpl.class);

    /** 状态：生成成功 */
    private static final String STATUS_SUCCESS = "success";

    /** 状态：生成失败 */
    private static final String STATUS_FAILED = "failed";

    /** 标题最大长度 */
    private static final int MAX_TITLE_CHARS = 200;

    /** 正文最大长度 */
    private static final int MAX_CONTENT_CHARS = 50000;

    /** 未填写要素的占位符 */
    private static final String BLANK_PLACEHOLDER = "______";

    /** 默认标题取值的候选主体名字段（按优先级） */
    private static final List<String> SUBJECT_NAME_KEYS = List.of(
            "applicantName", "clientName", "lenderName", "landlordName",
            "borrowerName", "debtorName", "tenantName");

    /** 系统提示词文件路径，启动后首次使用时加载并缓存 */
    @Value("classpath:prompts/document-draft.txt")
    private Resource promptResource;

    private volatile String cachedPrompt;

    @Autowired
    private DocumentTemplateRegistry templateRegistry;

    @Autowired
    private FormDataValidator formDataValidator;

    @Autowired
    private LawDocumentDraftMapper draftMapper;

    @Autowired
    private DocxDocumentExporter docxExporter;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private LawyerAiProperties aiProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<DocumentTemplateVO> listTemplates()
    {
        return templateRegistry.listTemplates();
    }

    @Override
    public DocumentDraftVO createDraft(Long userId, CreateDraftRequest request)
    {
        if (request == null)
        {
            throw new ServiceException("请求内容不能为空", 422);
        }
        if (StringUtils.isBlank(request.getTemplateCode()))
        {
            throw new ServiceException("请选择文书模板", 422);
        }
        DocumentTemplateVO template = templateRegistry.getByCode(request.getTemplateCode());
        if (template == null)
        {
            throw new ServiceException("文书模板不存在", 422);
        }
        Map<String, Object> formData = formDataValidator.validate(template, request.getFormData());
        String title = resolveTitle(request.getTitle(), template, formData);

        String content = "";
        String status = STATUS_SUCCESS;
        String errorMessage = null;
        try
        {
            String answer = aiChatClient.chat(buildMessages(template, formData),
                    Duration.ofMillis(aiProperties.getLongRequestTimeout()));
            String generated = normalizeGeneratedContent(answer);
            if (generated.isEmpty())
            {
                status = STATUS_FAILED;
                errorMessage = "模型未返回文书内容，请稍后重试";
            }
            else
            {
                content = truncate(generated, MAX_CONTENT_CHARS);
            }
        }
        catch (AiClientException e)
        {
            log.warn("文书生成模型调用失败, templateCode={}, type={}", template.getCode(), e.getType());
            status = STATUS_FAILED;
            errorMessage = e.getType() == AiClientException.Type.TIMEOUT
                    ? "模型响应超时，请稍后重试" : "模型服务暂时不可用，请稍后重试";
        }
        catch (RuntimeException e)
        {
            log.warn("文书生成失败, templateCode={}, 原因={}", template.getCode(), e.getMessage());
            status = STATUS_FAILED;
            errorMessage = "文书生成失败，请稍后重试";
        }

        Date now = new Date();
        LawDocumentDraft draft = new LawDocumentDraft();
        draft.setUserId(userId);
        draft.setTemplateCode(template.getCode());
        draft.setTitle(title);
        draft.setFormDataJson(writeFormData(formData));
        draft.setContent(content);
        draft.setStatus(status);
        draft.setModelName(aiProperties.getModel());
        draft.setCreateTime(now);
        draft.setUpdateTime(now);
        draftMapper.insertDraft(draft);

        DocumentDraftVO vo = toVO(draft, formData);
        vo.setErrorMessage(errorMessage);
        return vo;
    }

    @Override
    public DocumentDraftVO updateDraft(Long id, Long userId, UpdateDraftRequest request)
    {
        LawDocumentDraft draft = getOwnedDraft(id, userId);
        if (request == null)
        {
            throw new ServiceException("请求内容不能为空", 422);
        }
        String title = request.getTitle();
        if (StringUtils.isBlank(title))
        {
            throw new ServiceException("标题不能为空", 422);
        }
        title = title.trim();
        if (title.length() > MAX_TITLE_CHARS)
        {
            throw new ServiceException("标题不能超过" + MAX_TITLE_CHARS + "个字符", 422);
        }
        String content = request.getContent();
        if (content == null || content.trim().isEmpty())
        {
            throw new ServiceException("文书正文不能为空", 422);
        }
        if (content.length() > MAX_CONTENT_CHARS)
        {
            throw new ServiceException("文书正文不能超过" + MAX_CONTENT_CHARS + "个字符", 422);
        }

        draft.setTitle(title);
        draft.setContent(content);
        draft.setStatus(STATUS_SUCCESS);
        draft.setUpdateTime(new Date());
        draftMapper.updateDraftContent(draft);
        return toVO(draft, null);
    }

    @Override
    public List<DocumentDraftListVO> selectDraftList(Long userId, DraftQuery query, Integer pageNum,
            Integer pageSize)
    {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(num, size);
        List<DocumentDraftListVO> rows = draftMapper.selectDraftList(userId, query);
        if (rows == null)
        {
            return new ArrayList<>();
        }
        for (DocumentDraftListVO row : rows)
        {
            row.setTemplateName(templateName(row.getTemplateCode()));
        }
        return rows;
    }

    @Override
    public DocumentDraftVO selectDraftById(Long id, Long userId)
    {
        return toVO(getOwnedDraft(id, userId), null);
    }

    @Override
    public void deleteDraft(Long id, Long userId)
    {
        getOwnedDraft(id, userId);
        draftMapper.deleteByIdAndUserId(id, userId);
    }

    @Override
    public DocumentDraftVO selectExportableDraft(Long id, Long userId)
    {
        DocumentDraftVO draft = selectDraftById(id, userId);
        if (!STATUS_SUCCESS.equals(draft.getStatus()) || StringUtils.isBlank(draft.getContent()))
        {
            throw new ServiceException("文书尚未生成完成，无法导出", 422);
        }
        return draft;
    }

    @Override
    public byte[] exportDocx(DocumentDraftVO draft)
    {
        if (draft == null)
        {
            throw new ServiceException("文书尚未生成完成，无法导出", 422);
        }
        return docxExporter.export(draft.getTitle(), draft.getContent());
    }

    private LawDocumentDraft getOwnedDraft(Long id, Long userId)
    {
        LawDocumentDraft draft = draftMapper.selectByIdAndUserId(id, userId);
        if (draft == null)
        {
            throw new ServiceException("文书草稿不存在或无权访问");
        }
        return draft;
    }

    /**
     * 默认标题：{模板名}-{首个主体名字段}
     */
    private String resolveTitle(String title, DocumentTemplateVO template, Map<String, Object> formData)
    {
        if (StringUtils.isNotBlank(title))
        {
            String trimmed = title.trim();
            if (trimmed.length() > MAX_TITLE_CHARS)
            {
                throw new ServiceException("标题不能超过" + MAX_TITLE_CHARS + "个字符", 422);
            }
            return trimmed;
        }
        String subject = firstSubjectName(formData);
        String result = StringUtils.isBlank(subject)
                ? template.getName() : template.getName() + "-" + subject;
        return truncate(result, MAX_TITLE_CHARS);
    }

    private String firstSubjectName(Map<String, Object> formData)
    {
        for (String key : SUBJECT_NAME_KEYS)
        {
            Object value = formData.get(key);
            if (value != null && !value.toString().trim().isEmpty())
            {
                return value.toString().trim();
            }
        }
        return null;
    }

    private List<ChatMessage> buildMessages(DocumentTemplateVO template, Map<String, Object> formData)
    {
        List<ChatMessage> messages = new ArrayList<>(2);
        messages.add(new ChatMessage("system", systemPrompt()));
        messages.add(new ChatMessage("user", userPrompt(template, formData)));
        return messages;
    }

    private String userPrompt(DocumentTemplateVO template, Map<String, Object> formData)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("文书类型：").append(template.getName()).append("\n");
        if (StringUtils.isNotBlank(template.getDescription()))
        {
            builder.append("文书说明：").append(template.getDescription()).append("\n");
        }
        builder.append("\n【当事人要素】\n");
        String currentGroup = null;
        for (FieldDef field : template.getFields())
        {
            if (!Objects.equals(currentGroup, field.getGroup()))
            {
                currentGroup = field.getGroup();
                if (StringUtils.isNotBlank(currentGroup))
                {
                    builder.append(currentGroup).append("：\n");
                }
            }
            Object value = formData.get(field.getKey());
            String display = value == null || value.toString().trim().isEmpty()
                    ? BLANK_PLACEHOLDER : displayValue(value);
            builder.append(field.getLabel()).append("：").append(display).append("\n");
        }
        builder.append("\n请直接输出纯文本文书正文，不要重复标题，不要输出任何解释性文字。");
        return builder.toString();
    }

    private String displayValue(Object value)
    {
        if (value instanceof BigDecimal)
        {
            return ((BigDecimal) value).toPlainString();
        }
        return value.toString();
    }

    private String normalizeGeneratedContent(String answer)
    {
        if (answer == null)
        {
            return "";
        }
        String text = answer.replace("\r\n", "\n").replace("\uFEFF", "").trim();
        // 容错：模型偶尔仍返回代码围栏，去掉围栏行
        if (text.startsWith("```"))
        {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd > 0)
            {
                text = text.substring(firstLineEnd + 1);
            }
            if (text.endsWith("```"))
            {
                text = text.substring(0, text.length() - 3);
            }
            text = text.trim();
        }
        return text;
    }

    private String writeFormData(Map<String, Object> formData)
    {
        try
        {
            return objectMapper.writeValueAsString(formData == null ? new LinkedHashMap<>() : formData);
        }
        catch (RuntimeException e)
        {
            throw new ServiceException("文书要素保存失败，请稍后重试");
        }
    }

    private Map<String, Object> parseFormData(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return new LinkedHashMap<>();
        }
        try
        {
            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>()
                    {
                    });
            return parsed == null ? new LinkedHashMap<>() : parsed;
        }
        catch (RuntimeException e)
        {
            log.warn("文书要素 JSON 解析失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private DocumentDraftVO toVO(LawDocumentDraft draft, Map<String, Object> formData)
    {
        DocumentDraftVO vo = new DocumentDraftVO();
        vo.setId(draft.getId());
        vo.setTemplateCode(draft.getTemplateCode());
        vo.setTemplateName(templateName(draft.getTemplateCode()));
        vo.setTitle(draft.getTitle());
        vo.setFormData(formData == null ? parseFormData(draft.getFormDataJson()) : formData);
        vo.setContent(draft.getContent());
        vo.setStatus(draft.getStatus());
        vo.setModelName(draft.getModelName());
        vo.setCreateTime(draft.getCreateTime());
        vo.setUpdateTime(draft.getUpdateTime());
        return vo;
    }

    private String templateName(String templateCode)
    {
        DocumentTemplateVO template = templateRegistry.getByCode(templateCode);
        return template == null ? null : template.getName();
    }

    private String truncate(String text, int maxChars)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    private String systemPrompt()
    {
        String cached = cachedPrompt;
        if (cached == null)
        {
            synchronized (this)
            {
                cached = cachedPrompt;
                if (cached == null)
                {
                    try
                    {
                        cached = promptResource.getContentAsString(StandardCharsets.UTF_8);
                    }
                    catch (IOException e)
                    {
                        throw new ServiceException("文书提示词加载失败，请稍后重试");
                    }
                    cachedPrompt = cached;
                }
            }
        }
        return cached;
    }
}
