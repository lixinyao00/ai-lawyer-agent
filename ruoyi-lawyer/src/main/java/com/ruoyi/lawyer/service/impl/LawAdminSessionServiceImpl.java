package com.ruoyi.lawyer.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.config.LawyerChatProperties;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.dto.AdminSessionQuery;
import com.ruoyi.lawyer.mapper.LawChatMessageMapper;
import com.ruoyi.lawyer.mapper.LawChatSessionMapper;
import com.ruoyi.lawyer.service.ILawAdminSessionService;
import com.ruoyi.lawyer.support.ChatMessageConverter;
import com.ruoyi.lawyer.support.LawSessionLockService;
import com.ruoyi.lawyer.vo.AdminObservabilityVO;
import com.ruoyi.lawyer.vo.AdminObservabilityVO.ToolBreakdownVO;
import com.ruoyi.lawyer.vo.AdminSessionVO;
import com.ruoyi.lawyer.vo.ChatMessageVO;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 后台会话审计 业务层实现
 *
 * @author ruoyi
 */
@Service
public class LawAdminSessionServiceImpl implements ILawAdminSessionService
{
    private static final String DELETE_BUSY_MESSAGE = "会话正在生成回答，暂不能删除";

    @Autowired
    private LawChatSessionMapper sessionMapper;

    @Autowired
    private LawChatMessageMapper messageMapper;

    @Autowired
    private LawSessionLockService lockService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LawyerChatProperties chatProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public List<AdminSessionVO> selectSessionList(AdminSessionQuery query)
    {
        return sessionMapper.selectAdminSessionList(query == null ? new AdminSessionQuery() : query);
    }

    @Override
    public List<ChatMessageVO> selectMessageList(Long sessionId, Integer pageNum, Integer pageSize)
    {
        if (sessionMapper.selectSessionById(sessionId) == null)
        {
            throw new ServiceException("会话不存在");
        }
        // 与前台一致：未持锁时收敛遗留 pending
        if (!lockService.isLocked(sessionId))
        {
            Date now = new Date();
            Date deadline = new Date(now.getTime() - chatProperties.getPendingTimeoutSeconds() * 1000L);
            messageMapper.failStalePending(sessionId, deadline, now);
        }
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 50 : pageSize;
        PageHelper.startPage(num, size);
        List<LawChatMessage> messages = messageMapper.selectMessagesBySessionIdAsc(sessionId);
        return ChatMessageConverter.toPageVOList(messages, objectMapper, num, size);
    }

    @Override
    public AdminObservabilityVO getObservability(String beginTime, String endTime)
    {
        List<LawChatMessage> messages = messageMapper.selectAssistantMessagesForObservability(beginTime, endTime);
        AdminObservabilityVO vo = new AdminObservabilityVO();
        if (messages == null || messages.isEmpty())
        {
            vo.setToolBreakdown(Collections.emptyList());
            return vo;
        }

        long totalRequests = messages.size();
        long successCount = 0L;
        long failedCount = 0L;
        long totalPromptTokens = 0L;
        long totalCompletionTokens = 0L;
        long totalTokens = 0L;
        long ragHitCount = 0L;
        long queryRewriteCount = 0L;
        List<Long> ttftValues = new ArrayList<>();
        List<Long> latencyValues = new ArrayList<>();
        Map<String, ToolAggregate> toolAggregates = new LinkedHashMap<>();

        for (LawChatMessage message : messages)
        {
            if (message == null)
            {
                continue;
            }
            if ("success".equals(message.getStatus()))
            {
                successCount++;
            }
            else if ("failed".equals(message.getStatus()))
            {
                failedCount++;
            }
            totalPromptTokens += nullSafe(message.getPromptTokens());
            totalCompletionTokens += nullSafe(message.getCompletionTokens());
            if (message.getTotalTokens() != null)
            {
                totalTokens += message.getTotalTokens();
            }
            else
            {
                totalTokens += nullSafe(message.getPromptTokens()) + nullSafe(message.getCompletionTokens());
            }
            if (message.getTtftMs() != null)
            {
                ttftValues.add(message.getTtftMs());
            }
            if (message.getTotalLatencyMs() != null)
            {
                latencyValues.add(message.getTotalLatencyMs());
            }
            if (hasText(message.getCitationsJson()) && !"[]".equals(message.getCitationsJson().trim()))
            {
                ragHitCount++;
            }
            if (hasText(message.getRewrittenQuery()))
            {
                queryRewriteCount++;
            }
            accumulateToolCalls(message.getToolCallsJson(), toolAggregates);
        }

        long toolCallTotal = 0L;
        long toolCallSuccessTotal = 0L;
        List<ToolBreakdownVO> breakdown = new ArrayList<>();
        for (ToolAggregate aggregate : toolAggregates.values())
        {
            toolCallTotal += aggregate.count;
            toolCallSuccessTotal += aggregate.successCount;
            breakdown.add(new ToolBreakdownVO(aggregate.toolName, aggregate.count, aggregate.successCount,
                    round(aggregate.count == 0 ? 0.0 : aggregate.durationTotal / aggregate.count, 1)));
        }
        breakdown.sort((left, right) -> {
            int compare = Long.compare(right.getCount(), left.getCount());
            if (compare != 0)
            {
                return compare;
            }
            return left.getToolName().compareTo(right.getToolName());
        });

        vo.setTotalRequests(totalRequests);
        vo.setSuccessCount(successCount);
        vo.setFailedCount(failedCount);
        vo.setSuccessRate(percentage(successCount, totalRequests));
        vo.setTotalPromptTokens(totalPromptTokens);
        vo.setTotalCompletionTokens(totalCompletionTokens);
        vo.setTotalTokens(totalTokens);
        vo.setAvgTtftMs(round(average(ttftValues), 1));
        vo.setP95TtftMs(percentile95(ttftValues));
        vo.setAvgLatencyMs(round(average(latencyValues), 1));
        vo.setRagHitRate(percentage(ragHitCount, totalRequests));
        vo.setQueryRewriteCount(queryRewriteCount);
        vo.setToolCallTotal(toolCallTotal);
        vo.setToolCallSuccessRate(percentage(toolCallSuccessTotal, toolCallTotal));
        vo.setToolBreakdown(breakdown);
        return vo;
    }

    private void accumulateToolCalls(String toolCallsJson, Map<String, ToolAggregate> aggregates)
    {
        if (!hasText(toolCallsJson))
        {
            return;
        }
        List<Map<String, Object>> traces;
        try
        {
            traces = objectMapper.readValue(toolCallsJson, new TypeReference<List<Map<String, Object>>>()
            {
            });
        }
        catch (Exception e)
        {
            // 单条历史脏数据不应导致整个看板 500，跳过并继续聚合
            return;
        }
        if (traces == null)
        {
            return;
        }
        for (Map<String, Object> trace : traces)
        {
            if (trace == null)
            {
                continue;
            }
            Object rawName = trace.get("toolName");
            String toolName = rawName == null ? "" : rawName.toString().trim();
            if (toolName.isEmpty())
            {
                continue;
            }
            ToolAggregate aggregate = aggregates.computeIfAbsent(toolName, ToolAggregate::new);
            aggregate.count++;
            if ("success".equals(trace.get("status")))
            {
                aggregate.successCount++;
            }
            aggregate.durationTotal += numberValue(trace.get("durationMs"));
        }
    }

    /**
     * 从升序非空 ttft_ms 列表按 index = ceil(0.95 * n) - 1 精确取 P95
     */
    private long percentile95(List<Long> values)
    {
        if (values == null || values.isEmpty())
        {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }

    private double average(List<Long> values)
    {
        if (values == null || values.isEmpty())
        {
            return 0.0D;
        }
        long total = 0L;
        for (Long value : values)
        {
            total += value == null ? 0L : value;
        }
        return (double) total / values.size();
    }

    private double percentage(long numerator, long denominator)
    {
        if (denominator <= 0L)
        {
            return 0.0D;
        }
        return round(numerator * 100.0D / denominator, 2);
    }

    private double round(double value, int scale)
    {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private long nullSafe(Integer value)
    {
        return value == null ? 0L : value.longValue();
    }

    private double numberValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.doubleValue();
        }
        if (value == null)
        {
            return 0.0D;
        }
        try
        {
            return Double.parseDouble(value.toString().trim());
        }
        catch (NumberFormatException e)
        {
            return 0.0D;
        }
    }

    private boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    /**
     * 单工具调用聚合累加器
     */
    private static final class ToolAggregate
    {
        private final String toolName;

        private long count;

        private long successCount;

        private double durationTotal;

        private ToolAggregate(String toolName)
        {
            this.toolName = toolName;
        }
    }

    @Override
    public void deleteSession(Long sessionId)
    {
        if (sessionMapper.selectSessionById(sessionId) == null)
        {
            throw new ServiceException("会话不存在");
        }
        String token = lockService.tryLock(sessionId);
        if (token == null)
        {
            throw new ServiceException(DELETE_BUSY_MESSAGE, 409);
        }
        try
        {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                messageMapper.deleteMessagesBySessionId(sessionId);
                sessionMapper.deleteSessionById(sessionId);
            });
        }
        finally
        {
            lockService.unlock(sessionId, token);
        }
    }
}
