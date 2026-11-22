package com.ruoyi.lawyer.retrieval;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;
import com.ruoyi.lawyer.dto.KnowledgeDocRequest;
import com.ruoyi.lawyer.mapper.LawKnowledgeDocMapper;
import com.ruoyi.lawyer.service.ILawKnowledgeService;

/**
 * 内置官方法规语料初始化器
 *
 * <p>当 {@code lawyer.knowledge.seed-enabled=true} 且知识库文档表为空时，
 * 自动导入 classpath:knowledge/seed 下的内置法规语料。任何异常只记日志，不阻断启动。</p>
 *
 * @author ruoyi
 */
@Component
public class KnowledgeSeedInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(KnowledgeSeedInitializer.class);

    /** 内置语料文件，按语料重要度排序 */
    private static final String[] SEED_FILES = {
            "labor_contract_law.txt",
            "civil_code_contract.txt",
            "civil_code_lease.txt",
            "civil_code_loan.txt",
            "civil_code_tort.txt"
    };

    @Autowired
    private LawyerKnowledgeProperties properties;

    @Autowired
    private LawKnowledgeDocMapper docMapper;

    @Autowired
    private ILawKnowledgeService knowledgeService;

    @Override
    public void run(ApplicationArguments args)
    {
        if (!properties.isSeedEnabled())
        {
            return;
        }
        try
        {
            if (docMapper.countDocs() > 0)
            {
                return;
            }
        }
        catch (Exception e)
        {
            log.warn("知识库内置语料检查跳过（数据表可能尚未初始化）: {}", e.getMessage());
            return;
        }

        int imported = 0;
        for (String fileName : SEED_FILES)
        {
            try
            {
                if (importSeed(fileName))
                {
                    imported++;
                }
            }
            catch (Exception e)
            {
                log.error("内置法规语料导入失败, file={}: {}", fileName, e.getMessage(), e);
            }
        }
        if (imported > 0)
        {
            log.info("知识库内置语料导入完成, files={}", imported);
        }
    }

    private boolean importSeed(String fileName) throws Exception
    {
        ClassPathResource resource = new ClassPathResource("knowledge/seed/" + fileName);
        if (!resource.exists())
        {
            log.warn("内置法规语料不存在, file={}", fileName);
            return false;
        }
        String text;
        try (InputStream inputStream = resource.getInputStream())
        {
            text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF"))
        {
            text = text.substring(1);
        }

        String header = text;
        String body = "";
        int separator = text.indexOf("\n---");
        if (separator >= 0)
        {
            header = text.substring(0, separator);
            body = text.substring(separator + 4).trim();
        }
        Map<String, String> meta = parseHeader(header);

        KnowledgeDocRequest request = new KnowledgeDocRequest();
        request.setTitle(StringUtils.isNotBlank(meta.get("title")) ? meta.get("title") : defaultTitle(fileName));
        request.setCategory(StringUtils.isNotBlank(meta.get("category")) ? meta.get("category")
                : defaultCategory(fileName));
        request.setDocNumber(meta.get("docNumber"));
        if (StringUtils.isNotBlank(meta.get("effectiveDate")))
        {
            request.setEffectiveDate(DateUtils.parseDate(meta.get("effectiveDate")));
        }
        request.setRemark(meta.get("remark"));
        request.setStatus("0");
        request.setContent(body);

        Map<String, Object> result = knowledgeService.addDoc(request, "system");
        log.info("内置法规语料已导入, file={}, docId={}, chunkCount={}", fileName, result.get("docId"),
                result.get("chunkCount"));
        return true;
    }

    private Map<String, String> parseHeader(String header)
    {
        Map<String, String> meta = new HashMap<>();
        for (String line : header.split("\n"))
        {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#"))
            {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0)
            {
                colon = trimmed.indexOf('：');
            }
            if (colon <= 1)
            {
                continue;
            }
            String key = trimmed.substring(1, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            meta.put(key, value);
        }
        return meta;
    }

    private String defaultTitle(String fileName)
    {
        return switch (fileName)
        {
            case "labor_contract_law.txt" -> "中华人民共和国劳动合同法";
            case "civil_code_contract.txt", "civil_code_lease.txt", "civil_code_loan.txt",
                    "civil_code_tort.txt" -> "中华人民共和国民法典";
            default -> fileName;
        };
    }

    private String defaultCategory(String fileName)
    {
        return switch (fileName)
        {
            case "labor_contract_law.txt" -> "labor";
            case "civil_code_contract.txt" -> "civil_contract";
            case "civil_code_lease.txt" -> "civil_lease";
            case "civil_code_loan.txt" -> "civil_loan";
            case "civil_code_tort.txt" -> "civil_tort";
            default -> "other";
        };
    }
}
