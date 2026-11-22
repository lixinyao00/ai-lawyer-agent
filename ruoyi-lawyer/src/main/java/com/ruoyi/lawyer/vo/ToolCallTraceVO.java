package com.ruoyi.lawyer.vo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具调用轨迹（落库 tool_calls_json 的元素结构）
 *
 * @author ruoyi
 */
public class ToolCallTraceVO
{
    /** 模型生成的工具调用ID */
    private String callId;

    /** 第几轮迭代（1-based） */
    private Integer step;

    /** 工具名 */
    private String toolName;

    /** 前端展示标题 */
    private String displayTitle;

    /** 调用参数 */
    private Map<String, Object> arguments = new LinkedHashMap<>();

    /** 执行状态：success / error */
    private String status;

    /** 结果摘要 */
    private String resultSummary;

    /** 结构化详情（如计算公式、分步过程、法律依据等） */
    private Map<String, Object> detail;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 生成文书草稿时的草稿ID */
    private Long draftId;

    public String getCallId()
    {
        return callId;
    }

    public void setCallId(String callId)
    {
        this.callId = callId;
    }

    public Integer getStep()
    {
        return step;
    }

    public void setStep(Integer step)
    {
        this.step = step;
    }

    public String getToolName()
    {
        return toolName;
    }

    public void setToolName(String toolName)
    {
        this.toolName = toolName;
    }

    public String getDisplayTitle()
    {
        return displayTitle;
    }

    public void setDisplayTitle(String displayTitle)
    {
        this.displayTitle = displayTitle;
    }

    public Map<String, Object> getArguments()
    {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments)
    {
        this.arguments = arguments == null ? new LinkedHashMap<>() : arguments;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getResultSummary()
    {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary)
    {
        this.resultSummary = resultSummary;
    }

    public Map<String, Object> getDetail()
    {
        return detail;
    }

    public void setDetail(Map<String, Object> detail)
    {
        this.detail = detail;
    }

    public Long getDurationMs()
    {
        return durationMs;
    }

    public void setDurationMs(Long durationMs)
    {
        this.durationMs = durationMs;
    }

    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }
}
