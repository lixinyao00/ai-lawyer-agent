package com.ruoyi.lawyer.service.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.client.AiChatClient.ChatMessage;
import com.ruoyi.lawyer.client.AiClientException;
import com.ruoyi.lawyer.config.LawyerAiProperties;
import com.ruoyi.lawyer.config.LawyerContractProperties;
import com.ruoyi.lawyer.domain.LawContractReview;
import com.ruoyi.lawyer.domain.LawPrivateFile;
import com.ruoyi.lawyer.dto.ContractReviewQuery;
import com.ruoyi.lawyer.extract.ContractTextExtractor;
import com.ruoyi.lawyer.mapper.LawContractReviewMapper;
import com.ruoyi.lawyer.mapper.LawPrivateFileMapper;
import com.ruoyi.lawyer.service.ILawContractReviewService;
import com.ruoyi.lawyer.service.ILawPrivateFileService;
import com.ruoyi.lawyer.support.ModelJsonParser;
import com.ruoyi.lawyer.vo.ContractReviewListVO;
import com.ruoyi.lawyer.vo.ContractReviewVO;
import com.ruoyi.lawyer.vo.PrivateFileVO;
import com.ruoyi.lawyer.vo.RiskItem;
import tools.jackson.databind.ObjectMapper;

/**
 * 合同风险审查 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawContractReviewServiceImpl implements ILawContractReviewService
{
    private static final Logger log = LoggerFactory.getLogger(LawContractReviewServiceImpl.class);

    /** 允许的审查立场 */
    private static final Set<String> STANCES = Set.of("party_a", "party_b", "neutral");

    /** 入库合同全文最大字数 */
    private static final int MAX_CONTRACT_TEXT_CHARS = 50000;

    /** 标题最大长度 */
    private static final int MAX_TITLE_CHARS = 100;

    private static final String STATUS_PENDING = "pending";

    private static final String STATUS_SUCCESS = "success";

    private static final String STATUS_FAILED = "failed";

    private static final String PARSE_ERROR_MESSAGE = "模型返回格式异常，请重试";

    private static final DateTimeFormatter TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 系统提示词文件路径，启动后首次使用时加载并缓存 */
    @Value("classpath:prompts/contract-review.txt")
    private Resource promptResource;

    private volatile String cachedPrompt;

    @Autowired
    private LawContractReviewMapper reviewMapper;

    @Autowired
    private LawPrivateFileMapper fileMapper;

    @Autowired
    private ILawPrivateFileService privateFileService;

    @Autowired
    private ContractTextExtractor textExtractor;

    @Autowired
    private ModelJsonParser modelJsonParser;

    @Autowired
    private AiChatClient aiChatClient;

    @Autowired
    private LawyerAiProperties aiProperties;

    @Autowired
    private LawyerContractProperties contractProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("lawyerTaskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public ContractReviewVO createReview(Long userId, MultipartFile file, Long fileId, String contractText,
            String stance, String title)
    {
        String normalizedStance = normalizeStance(stance);
        Long resolvedFileId = null;
        String defaultTitle = null;
        String extractedText;

        if (file != null && !file.isEmpty())
        {
            // 优先级 1：现场上传（落盘 + 抽取）
            PrivateFileVO uploaded = privateFileService.uploadFile(userId, file);
            LawPrivateFile entity = privateFileService.getOwnedFile(uploaded.getId(), userId);
            extractedText = textExtractor.extract(privateFileService.resolvePhysicalPath(entity),
                    uploaded.getFileExt());
            resolvedFileId = uploaded.getId();
            defaultTitle = stripExtension(uploaded.getOriginalName());
        }
        else if (fileId != null)
        {
            // 优先级 2：已上传私有文件
            LawPrivateFile entity = privateFileService.getOwnedFile(fileId, userId);
            extractedText = textExtractor.extract(privateFileService.resolvePhysicalPath(entity),
                    entity.getFileExt());
            resolvedFileId = fileId;
            defaultTitle = stripExtension(entity.getOriginalName());
        }
        else if (StringUtils.isNotBlank(contractText))
        {
            // 优先级 3：直接粘贴文本
            extractedText = contractText.trim();
        }
        else
        {
            throw new ServiceException("请上传合同文件或粘贴合同文本", 422);
        }

        String storedText = truncate(extractedText, MAX_CONTRACT_TEXT_CHARS);
        if (storedText.isEmpty())
        {
            throw new ServiceException("未能从文件中提取到文字，可能是扫描件或加密文件", 422);
        }

        String finalTitle = resolveTitle(title, defaultTitle);
        Date now = new Date();
        LawContractReview review = new LawContractReview();
        review.setUserId(userId);
        review.setFileId(resolvedFileId);
        review.setTitle(finalTitle);
        review.setStance(normalizedStance);
        review.setContractText(storedText);
        review.setStatus(STATUS_PENDING);
        review.setCreateTime(now);
        review.setUpdateTime(now);
        reviewMapper.insertReview(review);

        Long reviewId = review.getId();
        try
        {
            taskExecutor.execute(() -> runReview(reviewId, storedText, normalizedStance));
        }
        catch (TaskRejectedException e)
        {
            log.warn("合同审查线程池已满, reviewId={}", reviewId);
            markFailed(reviewId, "服务繁忙，请稍后重试");
            review.setStatus(STATUS_FAILED);
            review.setErrorMsg("服务繁忙，请稍后重试");
        }

        return toVO(review, defaultTitle);
    }

    @Override
    public List<ContractReviewListVO> selectReviewList(Long userId, ContractReviewQuery query, Integer pageNum,
            Integer pageSize)
    {
        convergeStalePending(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        PageHelper.startPage(num, size);
        List<ContractReviewListVO> rows = reviewMapper.selectReviewList(userId, query);
        return rows == null ? new ArrayList<>() : rows;
    }

    @Override
    public ContractReviewVO selectReviewById(Long id, Long userId)
    {
        convergeStalePending(userId);
        LawContractReview review = reviewMapper.selectByIdAndUserId(id, userId);
        if (review == null)
        {
            throw new ServiceException("审查记录不存在或无权访问");
        }
        return toVO(review, resolveFileName(review.getFileId(), userId));
    }

    @Override
    public void deleteReview(Long id, Long userId)
    {
        LawContractReview review = reviewMapper.selectByIdAndUserId(id, userId);
        if (review == null)
        {
            throw new ServiceException("审查记录不存在或无权访问");
        }
        reviewMapper.deleteByIdAndUserId(id, userId);
        Long fileId = review.getFileId();
        if (fileId != null && reviewMapper.countByFileId(fileId) == 0)
        {
            try
            {
                privateFileService.deleteFile(fileId, userId);
            }
            catch (RuntimeException e)
            {
                log.warn("删除审查关联文件失败, reviewId={}, fileId={}, 原因={}", id, fileId, e.getMessage());
            }
        }
    }

    /**
     * 异步审查：调用模型 -> 解析 JSON -> 写回 success/failed
     */
    private void runReview(Long reviewId, String contractText, String stance)
    {
        try
        {
            int maxChars = contractProperties.getMaxChars();
            boolean truncated = contractText.length() > maxChars;
            String modelText = truncated ? contractText.substring(0, maxChars) : contractText;

            List<ChatMessage> messages = Arrays.asList(
                    new ChatMessage("system", systemPrompt()),
                    new ChatMessage("user", userPrompt(stance, modelText)));

            String answer = aiChatClient.chat(messages,
                    Duration.ofMillis(aiProperties.getLongRequestTimeout()));
            ModelJsonParser.ParseResult result = modelJsonParser.parse(answer);

            String summary = result.getSummary() == null ? "" : result.getSummary();
            if (truncated)
            {
                summary = "【合同较长，仅审查前 " + maxChars + " 字】" + summary;
            }

            LawContractReview update = new LawContractReview();
            update.setId(reviewId);
            update.setStatus(STATUS_SUCCESS);
            update.setRiskLevel(result.getRiskLevel());
            update.setSummary(summary);
            update.setRisksJson(objectMapper.writeValueAsString(result.getRisks()));
            update.setModelName(aiProperties.getModel());
            update.setUpdateTime(new Date());
            int rows = reviewMapper.updateReviewResult(update);
            if (rows == 0)
            {
                log.warn("合同审查结果写回失败（记录可能已被收敛）, reviewId={}", reviewId);
            }
        }
        catch (ModelJsonParser.ParseException e)
        {
            log.warn("合同审查模型输出解析失败, reviewId={}: {}", reviewId, e.getMessage());
            markFailed(reviewId, PARSE_ERROR_MESSAGE);
        }
        catch (AiClientException e)
        {
            log.warn("合同审查模型调用失败, reviewId={}, type={}", reviewId, e.getType());
            if (e.getType() == AiClientException.Type.TIMEOUT)
            {
                markFailed(reviewId, "模型响应超时，请稍后重试");
            }
            else
            {
                markFailed(reviewId, "模型服务暂时不可用，请稍后重试");
            }
        }
        catch (Exception e)
        {
            log.warn("合同审查异步任务失败, reviewId={}, 原因={}", reviewId, e.getMessage());
            markFailed(reviewId, "审查失败，请稍后重试");
        }
    }

    private void markFailed(Long reviewId, String errorMessage)
    {
        LawContractReview update = new LawContractReview();
        update.setId(reviewId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMsg(errorMessage);
        update.setModelName(aiProperties.getModel());
        update.setUpdateTime(new Date());
        try
        {
            reviewMapper.updateReviewResult(update);
        }
        catch (RuntimeException e)
        {
            log.warn("合同审查失败状态写回异常, reviewId={}: {}", reviewId, e.getMessage());
        }
    }

    /**
     * 收敛本人超时 pending 记录
     */
    private void convergeStalePending(Long userId)
    {
        Date now = new Date();
        Date deadline = new Date(now.getTime() - contractProperties.getPendingTimeoutSeconds() * 1000L);
        reviewMapper.failStalePending(userId, deadline, now);
    }

    private String normalizeStance(String stance)
    {
        if (StringUtils.isBlank(stance))
        {
            throw new ServiceException("请选择审查立场", 422);
        }
        String normalized = stance.trim().toLowerCase();
        if (!STANCES.contains(normalized))
        {
            throw new ServiceException("审查立场不合法，仅支持 party_a、party_b、neutral", 422);
        }
        return normalized;
    }

    private String resolveTitle(String title, String defaultTitle)
    {
        String result;
        if (StringUtils.isNotBlank(title))
        {
            result = title.trim();
            if (result.length() > MAX_TITLE_CHARS)
            {
                throw new ServiceException("标题不能超过" + MAX_TITLE_CHARS + "个字符", 422);
            }
            return result;
        }
        if (StringUtils.isNotBlank(defaultTitle))
        {
            result = defaultTitle.trim();
        }
        else
        {
            result = "合同审查-" + LocalDateTime.now().format(TITLE_FORMATTER);
        }
        return truncate(result, MAX_TITLE_CHARS);
    }

    private String stripExtension(String fileName)
    {
        if (StringUtils.isBlank(fileName))
        {
            return null;
        }
        String name = fileName.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0)
        {
            name = name.substring(0, dot);
        }
        return name.isBlank() ? null : name;
    }

    private String truncate(String text, int maxChars)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    private String resolveFileName(Long fileId, Long userId)
    {
        if (fileId == null)
        {
            return null;
        }
        LawPrivateFile file = fileMapper.selectByIdAndUserId(fileId, userId);
        return file == null ? null : file.getOriginalName();
    }

    private List<RiskItem> parseRisks(String json)
    {
        if (StringUtils.isBlank(json))
        {
            return new ArrayList<>();
        }
        try
        {
            RiskItem[] items = objectMapper.readValue(json, RiskItem[].class);
            return items == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(items));
        }
        catch (RuntimeException e)
        {
            log.warn("合同审查风险项 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private ContractReviewVO toVO(LawContractReview review, String fileName)
    {
        ContractReviewVO vo = new ContractReviewVO();
        vo.setId(review.getId());
        vo.setFileId(review.getFileId());
        vo.setFileName(fileName);
        vo.setTitle(review.getTitle());
        vo.setStance(review.getStance());
        vo.setStatus(review.getStatus());
        vo.setRiskLevel(review.getRiskLevel());
        vo.setSummary(review.getSummary());
        vo.setRisks(parseRisks(review.getRisksJson()));
        vo.setModelName(review.getModelName());
        vo.setErrorMessage(review.getErrorMsg());
        vo.setContractText(review.getContractText());
        vo.setContractLength(review.getContractText() == null ? 0 : review.getContractText().length());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        return vo;
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
                        throw new ServiceException("审查提示词加载失败，请稍后重试");
                    }
                    cachedPrompt = cached;
                }
            }
        }
        return cached;
    }

    private String userPrompt(String stance, String contractText)
    {
        String stanceLabel;
        switch (stance)
        {
            case "party_a":
                stanceLabel = "甲方（party_a）";
                break;
            case "party_b":
                stanceLabel = "乙方（party_b）";
                break;
            default:
                stanceLabel = "中立审查（neutral）";
                break;
        }
        return "审查立场：" + stanceLabel + "\n"
                + "请依据系统要求逐条审查以下合同，只输出约定的 JSON，不要输出任何多余文字。\n\n"
                + "【合同全文开始】\n" + contractText + "\n【合同全文结束】";
    }
}
