package com.ruoyi.lawyer.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * OpenAI 兼容 SSE 流式响应解析器
 *
 * <p>负责把单行 SSE 文本解析成增量内容；非 data 行、空 delta、[DONE] 与异常 JSON 均返回 null。</p>
 *
 * @author ruoyi
 */
public class OpenAiStreamParser
{
    private static final Logger log = LoggerFactory.getLogger(OpenAiStreamParser.class);

    /** 流结束标记 */
    public static final String DONE_MARKER = "[DONE]";

    private static final String DATA_PREFIX = "data:";

    /**
     * 是否为 data 行
     *
     * @param line 原始行
     * @return 是否 data 行
     */
    public boolean isDataLine(String line)
    {
        return line != null && line.trim().startsWith(DATA_PREFIX);
    }

    /**
     * 是否为结束标记行
     *
     * @param line 原始行
     * @return 是否 [DONE]
     */
    public boolean isDone(String line)
    {
        if (!isDataLine(line))
        {
            return false;
        }
        return DONE_MARKER.equals(payload(line));
    }

    /**
     * 解析单行 SSE，返回增量文本
     *
     * @param line 原始行
     * @return 增量文本；非 data 行、空 delta、[DONE]、异常 JSON 返回 null
     */
    public String parseLine(String line)
    {
        if (!isDataLine(line))
        {
            return null;
        }
        String payload = payload(line);
        if (payload.isEmpty() || DONE_MARKER.equals(payload))
        {
            return null;
        }
        try
        {
            JSONObject root = JSONObject.parseObject(payload);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                return null;
            }
            JSONObject choice = choices.getJSONObject(0);
            if (choice == null)
            {
                return null;
            }
            JSONObject delta = choice.getJSONObject("delta");
            if (delta == null)
            {
                return null;
            }
            String content = delta.getString("content");
            return content == null || content.isEmpty() ? null : content;
        }
        catch (Exception e)
        {
            // 单行异常不应中断整个流，记录后跳过
            log.warn("SSE 数据行解析失败: {}", e.getMessage());
            return null;
        }
    }

    private String payload(String line)
    {
        return line.trim().substring(DATA_PREFIX.length()).trim();
    }
}
