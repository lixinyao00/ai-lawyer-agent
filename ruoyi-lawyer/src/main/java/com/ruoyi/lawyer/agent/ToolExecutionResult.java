package com.ruoyi.lawyer.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * 工具执行结果
 *
 * @author ruoyi
 */
public class ToolExecutionResult
{
    /** 成功标记 */
    public static final String STATUS_SUCCESS = "success";

    /** 失败标记，用于驱动模型下一轮自愈重试 */
    public static final String STATUS_ERROR = "error";

    private boolean success;

    private String status;

    private String summary;

    private Map<String, Object> detail = new LinkedHashMap<>();

    private String modelContent;

    private Long draftId;

    private List<CitationVO> newCitations = new ArrayList<>();

    public static ToolExecutionResult success(String summary, Map<String, Object> detail, String modelContent,
            Long draftId, List<CitationVO> newCitations)
    {
        ToolExecutionResult result = new ToolExecutionResult();
        result.success = true;
        result.status = STATUS_SUCCESS;
        result.summary = summary;
        result.detail = detail == null ? new LinkedHashMap<>() : detail;
        result.modelContent = modelContent;
        result.draftId = draftId;
        result.newCitations = newCitations == null ? new ArrayList<>() : newCitations;
        return result;
    }

    /**
     * 构造错误结果：不抛异常中断 SSE，而是回传模型引导自愈
     *
     * @param summary 面向用户的错误摘要
     * @param modelContent 面向模型的详细错误与修正指引
     * @return 错误结果
     */
    public static ToolExecutionResult error(String summary, String modelContent)
    {
        ToolExecutionResult result = new ToolExecutionResult();
        result.success = false;
        result.status = STATUS_ERROR;
        result.summary = summary;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("error", summary);
        detail.put("hint", modelContent);
        result.detail = detail;
        result.modelContent = modelContent;
        result.newCitations = Collections.emptyList();
        return result;
    }

    public boolean isSuccess()
    {
        return success;
    }

    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public Map<String, Object> getDetail()
    {
        return detail;
    }

    public void setDetail(Map<String, Object> detail)
    {
        this.detail = detail == null ? new LinkedHashMap<>() : detail;
    }

    public String getModelContent()
    {
        return modelContent;
    }

    public void setModelContent(String modelContent)
    {
        this.modelContent = modelContent;
    }

    public Long getDraftId()
    {
        return draftId;
    }

    public void setDraftId(Long draftId)
    {
        this.draftId = draftId;
    }

    public List<CitationVO> getNewCitations()
    {
        return newCitations;
    }

    public void setNewCitations(List<CitationVO> newCitations)
    {
        this.newCitations = newCitations == null ? new ArrayList<>() : newCitations;
    }
}
