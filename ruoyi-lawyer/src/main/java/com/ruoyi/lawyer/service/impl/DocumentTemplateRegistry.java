package com.ruoyi.lawyer.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;
import com.ruoyi.lawyer.vo.FieldDef;
import tools.jackson.databind.ObjectMapper;

/**
 * 文书模板注册中心
 *
 * <p>启动时从 classpath:templates/documents.json 加载全部模板元数据并缓存，
 * 提供模板列表与按编码查询。模板编码必须唯一。</p>
 *
 * @author ruoyi
 */
@Component
public class DocumentTemplateRegistry
{
    private static final Logger log = LoggerFactory.getLogger(DocumentTemplateRegistry.class);

    /** 模板元数据资源路径 */
    private static final String TEMPLATE_RESOURCE = "templates/documents.json";

    private final ObjectMapper objectMapper;

    /** 编码 -> 模板 */
    private final Map<String, DocumentTemplateVO> templateMap;

    /** 模板有序列表 */
    private final List<DocumentTemplateVO> templates;

    public DocumentTemplateRegistry(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
        this.templates = Collections.unmodifiableList(loadTemplates());
        Map<String, DocumentTemplateVO> map = new LinkedHashMap<>();
        for (DocumentTemplateVO template : this.templates)
        {
            map.put(template.getCode(), template);
        }
        this.templateMap = Collections.unmodifiableMap(map);
        log.info("文书模板加载完成，共 {} 个: {}", this.templates.size(), map.keySet());
    }

    /**
     * 全部模板（按 documents.json 顺序）
     *
     * @return 模板列表
     */
    public List<DocumentTemplateVO> listTemplates()
    {
        return templates;
    }

    /**
     * 按编码查询模板
     *
     * @param code 模板编码
     * @return 模板，不存在返回 null
     */
    public DocumentTemplateVO getByCode(String code)
    {
        if (code == null)
        {
            return null;
        }
        return templateMap.get(code.trim());
    }

    private List<DocumentTemplateVO> loadTemplates()
    {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(TEMPLATE_RESOURCE))
        {
            if (in == null)
            {
                throw new IllegalStateException("文书模板资源不存在: " + TEMPLATE_RESOURCE);
            }
            DocumentTemplateVO[] loaded = objectMapper.readValue(in, DocumentTemplateVO[].class);
            if (loaded == null || loaded.length == 0)
            {
                throw new IllegalStateException("文书模板资源为空: " + TEMPLATE_RESOURCE);
            }
            List<DocumentTemplateVO> result = new ArrayList<>(loaded.length);
            Map<String, Boolean> seen = new LinkedHashMap<>();
            for (DocumentTemplateVO template : loaded)
            {
                validateTemplate(template, seen);
                result.add(template);
            }
            return result;
        }
        catch (IOException e)
        {
            throw new IllegalStateException("文书模板资源读取失败: " + TEMPLATE_RESOURCE, e);
        }
    }

    private void validateTemplate(DocumentTemplateVO template, Map<String, Boolean> seen)
    {
        if (template == null || template.getCode() == null || template.getCode().isBlank())
        {
            throw new IllegalStateException("文书模板缺少 code: " + TEMPLATE_RESOURCE);
        }
        if (template.getName() == null || template.getName().isBlank())
        {
            throw new IllegalStateException("文书模板缺少 name: " + template.getCode());
        }
        if (seen.put(template.getCode(), Boolean.TRUE) != null)
        {
            throw new IllegalStateException("文书模板 code 重复: " + template.getCode());
        }
        if (template.getFields().isEmpty())
        {
            throw new IllegalStateException("文书模板缺少字段定义: " + template.getCode());
        }
        Map<String, Boolean> fieldKeys = new LinkedHashMap<>();
        for (FieldDef field : template.getFields())
        {
            if (field == null || field.getKey() == null || field.getKey().isBlank())
            {
                throw new IllegalStateException("文书模板字段缺少 key: " + template.getCode());
            }
            if (field.getLabel() == null || field.getLabel().isBlank())
            {
                throw new IllegalStateException("文书模板字段缺少 label: " + template.getCode()
                        + "." + field.getKey());
            }
            if (fieldKeys.put(field.getKey(), Boolean.TRUE) != null)
            {
                throw new IllegalStateException("文书模板字段 key 重复: " + template.getCode()
                        + "." + field.getKey());
            }
            if ("select".equals(field.getType()) && field.getOptions().isEmpty())
            {
                throw new IllegalStateException("select 字段缺少 options: " + template.getCode()
                        + "." + field.getKey());
            }
        }
    }
}
