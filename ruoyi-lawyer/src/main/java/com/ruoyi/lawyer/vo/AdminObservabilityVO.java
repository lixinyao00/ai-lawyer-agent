package com.ruoyi.lawyer.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 后台 Token / TTFT / RAG / Tool 可观测性聚合统计视图
 *
 * <p>统计口径见 PLAN_V2.md §5.1：时间范围内 {@code role='assistant'} 消息为统计基数，
 * 比率字段统一以百分比表示并保留 2 位小数。</p>
 *
 * @author ruoyi
 */
public class AdminObservabilityVO
{
    /** 时间范围内的助手消息总数 */
    private Long totalRequests = 0L;

    /** 成功消息数 */
    private Long successCount = 0L;

    /** 失败消息数 */
    private Long failedCount = 0L;

    /** 成功率（百分比，保留 2 位小数） */
    private Double successRate = 0.0D;

    /** 输入 Token 总数 */
    private Long totalPromptTokens = 0L;

    /** 输出 Token 总数 */
    private Long totalCompletionTokens = 0L;

    /** 总 Token 数 */
    private Long totalTokens = 0L;

    /** 平均首字延迟（毫秒，保留 1 位小数） */
    private Double avgTtftMs = 0.0D;

    /** P95 首字延迟（毫秒） */
    private Long p95TtftMs = 0L;

    /** 平均端到端总耗时（毫秒，保留 1 位小数） */
    private Double avgLatencyMs = 0.0D;

    /** RAG 法条命中率（百分比，保留 2 位小数） */
    private Double ragHitRate = 0.0D;

    /** 多轮指代 Query 改写次数 */
    private Long queryRewriteCount = 0L;

    /** Agent 工具调用总次数 */
    private Long toolCallTotal = 0L;

    /** Agent 工具调用成功率（百分比，保留 2 位小数） */
    private Double toolCallSuccessRate = 0.0D;

    /** 分工具调用明细 */
    private List<ToolBreakdownVO> toolBreakdown = new ArrayList<>();

    public Long getTotalRequests()
    {
        return totalRequests;
    }

    public void setTotalRequests(Long totalRequests)
    {
        this.totalRequests = totalRequests == null ? 0L : totalRequests;
    }

    public Long getSuccessCount()
    {
        return successCount;
    }

    public void setSuccessCount(Long successCount)
    {
        this.successCount = successCount == null ? 0L : successCount;
    }

    public Long getFailedCount()
    {
        return failedCount;
    }

    public void setFailedCount(Long failedCount)
    {
        this.failedCount = failedCount == null ? 0L : failedCount;
    }

    public Double getSuccessRate()
    {
        return successRate;
    }

    public void setSuccessRate(Double successRate)
    {
        this.successRate = successRate == null ? 0.0D : successRate;
    }

    public Long getTotalPromptTokens()
    {
        return totalPromptTokens;
    }

    public void setTotalPromptTokens(Long totalPromptTokens)
    {
        this.totalPromptTokens = totalPromptTokens == null ? 0L : totalPromptTokens;
    }

    public Long getTotalCompletionTokens()
    {
        return totalCompletionTokens;
    }

    public void setTotalCompletionTokens(Long totalCompletionTokens)
    {
        this.totalCompletionTokens = totalCompletionTokens == null ? 0L : totalCompletionTokens;
    }

    public Long getTotalTokens()
    {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens)
    {
        this.totalTokens = totalTokens == null ? 0L : totalTokens;
    }

    public Double getAvgTtftMs()
    {
        return avgTtftMs;
    }

    public void setAvgTtftMs(Double avgTtftMs)
    {
        this.avgTtftMs = avgTtftMs == null ? 0.0D : avgTtftMs;
    }

    public Long getP95TtftMs()
    {
        return p95TtftMs;
    }

    public void setP95TtftMs(Long p95TtftMs)
    {
        this.p95TtftMs = p95TtftMs == null ? 0L : p95TtftMs;
    }

    public Double getAvgLatencyMs()
    {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Double avgLatencyMs)
    {
        this.avgLatencyMs = avgLatencyMs == null ? 0.0D : avgLatencyMs;
    }

    public Double getRagHitRate()
    {
        return ragHitRate;
    }

    public void setRagHitRate(Double ragHitRate)
    {
        this.ragHitRate = ragHitRate == null ? 0.0D : ragHitRate;
    }

    public Long getQueryRewriteCount()
    {
        return queryRewriteCount;
    }

    public void setQueryRewriteCount(Long queryRewriteCount)
    {
        this.queryRewriteCount = queryRewriteCount == null ? 0L : queryRewriteCount;
    }

    public Long getToolCallTotal()
    {
        return toolCallTotal;
    }

    public void setToolCallTotal(Long toolCallTotal)
    {
        this.toolCallTotal = toolCallTotal == null ? 0L : toolCallTotal;
    }

    public Double getToolCallSuccessRate()
    {
        return toolCallSuccessRate;
    }

    public void setToolCallSuccessRate(Double toolCallSuccessRate)
    {
        this.toolCallSuccessRate = toolCallSuccessRate == null ? 0.0D : toolCallSuccessRate;
    }

    public List<ToolBreakdownVO> getToolBreakdown()
    {
        return toolBreakdown;
    }

    public void setToolBreakdown(List<ToolBreakdownVO> toolBreakdown)
    {
        this.toolBreakdown = toolBreakdown == null ? new ArrayList<>() : toolBreakdown;
    }

    /**
     * 分工具调用统计
     */
    public static class ToolBreakdownVO
    {
        /** 工具名 */
        private String toolName;

        /** 调用次数 */
        private Long count = 0L;

        /** 成功次数 */
        private Long successCount = 0L;

        /** 平均耗时（毫秒，保留 1 位小数） */
        private Double avgDurationMs = 0.0D;

        public ToolBreakdownVO()
        {
        }

        public ToolBreakdownVO(String toolName, Long count, Long successCount, Double avgDurationMs)
        {
            this.toolName = toolName;
            this.count = count == null ? 0L : count;
            this.successCount = successCount == null ? 0L : successCount;
            this.avgDurationMs = avgDurationMs == null ? 0.0D : avgDurationMs;
        }

        public String getToolName()
        {
            return toolName;
        }

        public void setToolName(String toolName)
        {
            this.toolName = toolName;
        }

        public Long getCount()
        {
            return count;
        }

        public void setCount(Long count)
        {
            this.count = count == null ? 0L : count;
        }

        public Long getSuccessCount()
        {
            return successCount;
        }

        public void setSuccessCount(Long successCount)
        {
            this.successCount = successCount == null ? 0L : successCount;
        }

        public Double getAvgDurationMs()
        {
            return avgDurationMs;
        }

        public void setAvgDurationMs(Double avgDurationMs)
        {
            this.avgDurationMs = avgDurationMs == null ? 0.0D : avgDurationMs;
        }
    }
}
