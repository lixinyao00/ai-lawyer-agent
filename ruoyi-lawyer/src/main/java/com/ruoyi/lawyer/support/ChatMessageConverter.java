package com.ruoyi.lawyer.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.pagehelper.Page;
import com.ruoyi.lawyer.domain.LawChatMessage;
import com.ruoyi.lawyer.vo.ChatMessageVO;
import com.ruoyi.lawyer.vo.CitationVO;
import com.ruoyi.lawyer.vo.ToolCallTraceVO;
import tools.jackson.databind.ObjectMapper;

/**
 * 消息实体转视图对象
 *
 * @author ruoyi
 */
public final class ChatMessageConverter
{
    private static final Logger log = LoggerFactory.getLogger(ChatMessageConverter.class);

    private ChatMessageConverter()
    {
    }

    /**
     * 单条消息转换
     *
     * @param message 消息实体
     * @param objectMapper Jackson 序列化器
     * @return 视图对象
     */
    public static ChatMessageVO toVO(LawChatMessage message, ObjectMapper objectMapper)
    {
        if (message == null)
        {
            return null;
        }
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setSessionId(message.getSessionId());
        vo.setRequestId(message.getRequestId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setStatus(message.getStatus());
        vo.setErrorCode(message.getErrorCode());
        vo.setModelName(message.getModelName());
        vo.setCreateTime(message.getCreateTime());
        vo.setUpdateTime(message.getUpdateTime());
        vo.setCitations(parseCitations(message.getCitationsJson(), objectMapper));
        vo.setRewrittenQuery(message.getRewrittenQuery());
        vo.setRetrievalMode(message.getRetrievalMode());
        vo.setToolCalls(parseToolCalls(message.getToolCallsJson(), objectMapper));
        vo.setPromptTokens(message.getPromptTokens());
        vo.setCompletionTokens(message.getCompletionTokens());
        vo.setTotalTokens(message.getTotalTokens());
        vo.setTtftMs(message.getTtftMs());
        vo.setTotalLatencyMs(message.getTotalLatencyMs());
        return vo;
    }

    /**
     * 批量转换
     *
     * @param messages 消息实体
     * @param objectMapper Jackson 序列化器
     * @return 视图对象集合
     */
    public static List<ChatMessageVO> toVOList(List<LawChatMessage> messages, ObjectMapper objectMapper)
    {
        List<ChatMessageVO> list = new ArrayList<>();
        if (messages == null)
        {
            return list;
        }
        for (LawChatMessage message : messages)
        {
            list.add(toVO(message, objectMapper));
        }
        return list;
    }

    /**
     * 批量转换并保留 PageHelper 分页总数
     *
     * @param messages 消息实体（PageHelper 返回的 Page）
     * @param objectMapper Jackson 序列化器
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 带分页信息的视图对象集合
     */
    public static List<ChatMessageVO> toPageVOList(List<LawChatMessage> messages, ObjectMapper objectMapper,
            int pageNum, int pageSize)
    {
        long total = messages instanceof Page<?> page ? page.getTotal() : (messages == null ? 0 : messages.size());
        Page<ChatMessageVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(total);
        result.addAll(toVOList(messages, objectMapper));
        return result;
    }

    /**
     * 解析 citations_json，异常或空值返回空列表
     */
    private static List<CitationVO> parseCitations(String json, ObjectMapper objectMapper)
    {
        if (json == null || json.isBlank())
        {
            return new ArrayList<>();
        }
        try
        {
            CitationVO[] citations = objectMapper.readValue(json, CitationVO[].class);
            return citations == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(citations));
        }
        catch (Exception e)
        {
            log.warn("法条引用 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 解析 tool_calls_json，异常或空值返回空列表
     *
     * @param json 轨迹 JSON
     * @param objectMapper Jackson 序列化器
     * @return 工具调用轨迹
     */
    public static List<ToolCallTraceVO> parseToolCalls(String json, ObjectMapper objectMapper)
    {
        if (json == null || json.isBlank())
        {
            return new ArrayList<>();
        }
        try
        {
            ToolCallTraceVO[] traces = objectMapper.readValue(json, ToolCallTraceVO[].class);
            return traces == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(traces));
        }
        catch (Exception e)
        {
            log.warn("工具调用轨迹 JSON 解析失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
