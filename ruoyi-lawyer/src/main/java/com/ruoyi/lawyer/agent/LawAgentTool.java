package com.ruoyi.lawyer.agent;

import java.util.List;
import java.util.Map;

/**
 * 法律 Agent 工具统一接口
 *
 * @author ruoyi
 */
public interface LawAgentTool
{
    /**
     * 工具名（OpenAI function name）
     *
     * @return 工具名
     */
    String name();

    /**
     * 工具说明
     *
     * @return 说明
     */
    String description();

    /**
     * 前端工具时间线展示标题
     *
     * @param arguments 工具参数
     * @return 展示标题
     */
    String displayTitle(Map<String, Object> arguments);

    /**
     * JSON Schema 参数定义
     *
     * @return 参数 Schema
     */
    Map<String, Object> parametersSchema();

    /**
     * 执行工具
     *
     * @param context 执行上下文
     * @param arguments 已解析的参数
     * @return 执行结果
     */
    ToolExecutionResult execute(ToolExecutionContext context, Map<String, Object> arguments);
}
