package com.ruoyi.lawyer.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.lawyer.vo.CitationVO;

/**
 * 工具执行上下文
 *
 * <p>异步流式线程禁止调用 {@code SecurityUtils.getUserId()}，归属信息统一由本上下文显式透传。</p>
 *
 * @author ruoyi
 */
public class ToolExecutionContext
{
    /** 当前用户ID */
    private final Long userId;

    /** 当前会话ID */
    private final Long sessionId;

    /** 本次请求ID */
    private final String requestId;

    /** 本次回答已收集的法条引用，工具补充时按 chunkId 去重合入 */
    private final List<CitationVO> collectedCitations;

    public ToolExecutionContext(Long userId, Long sessionId, String requestId, List<CitationVO> collectedCitations)
    {
        this.userId = userId;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.collectedCitations = collectedCitations == null ? new ArrayList<>() : collectedCitations;
    }

    public Long getUserId()
    {
        return userId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public String getRequestId()
    {
        return requestId;
    }

    public List<CitationVO> getCollectedCitations()
    {
        return collectedCitations;
    }

    /**
     * 按 chunkId 去重合并法条引用
     *
     * @param citations 新增引用
     * @return 实际新增的引用（已存在的不会重复加入）
     */
    public List<CitationVO> mergeCitations(List<CitationVO> citations)
    {
        List<CitationVO> added = new ArrayList<>();
        if (citations == null || citations.isEmpty())
        {
            return added;
        }
        Map<Long, CitationVO> existing = new LinkedHashMap<>();
        for (CitationVO citation : collectedCitations)
        {
            if (citation != null && citation.getChunkId() != null)
            {
                existing.putIfAbsent(citation.getChunkId(), citation);
            }
        }
        for (CitationVO citation : citations)
        {
            if (citation == null)
            {
                continue;
            }
            if (citation.getChunkId() != null && existing.containsKey(citation.getChunkId()))
            {
                continue;
            }
            collectedCitations.add(citation);
            if (citation.getChunkId() != null)
            {
                existing.put(citation.getChunkId(), citation);
            }
            added.add(citation);
        }
        return added;
    }
}
