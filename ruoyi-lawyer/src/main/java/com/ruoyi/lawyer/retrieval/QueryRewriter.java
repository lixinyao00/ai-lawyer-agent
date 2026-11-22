package com.ruoyi.lawyer.retrieval;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.client.AiChatClient;
import com.ruoyi.lawyer.config.LawyerKnowledgeProperties;

/**
 * 多轮指代 Query 改写器
 *
 * <p>三阶段策略：</p>
 * <ol>
 *   <li>规则预判 {@link #needsRewrite(String, boolean)}：短问题或含指代/追问触发词才改写；</li>
 *   <li>轻量 LLM 改写（默认 8s 超时）：把追问改写为一句独立完整、可直接检索的查询句；</li>
 *   <li>规则实体提取补全兜底 {@code rule_fallback}：从历史对话抽取法规名、条号、法律主题词前置补全，
 *       保证离线单测/评测在没有 LLM 或 LLM 超时、输出非法时仍能高准确改写多轮指代追问。</li>
 * </ol>
 *
 * @author ruoyi
 */
@Component
public class QueryRewriter
{
    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    /** 触发改写的指代/追问词 */
    private static final String[] ANAPHORA_TRIGGERS = {
            "那如果", "那", "这", "它", "他", "她", "对方", "公司", "房东", "上面", "刚才",
            "如果", "要是", "呢", "这种", "这个", "那个", "上述", "另外",
            "怎么办", "怎么算", "赔多少", "能要回"
    };

    /** 短问题长度阈值 */
    private static final int SHORT_QUERY_LENGTH = 25;

    /** LLM 改写最多带入的历史消息条数（最近 2 轮） */
    private static final int MAX_HISTORY_MESSAGES = 4;

    /** 单条历史消息截断长度 */
    private static final int MAX_HISTORY_MESSAGE_CHARS = 300;

    /** 改写结果最大长度 */
    private static final int MAX_REWRITTEN_CHARS = 150;

    /** 法规名称实体 */
    private static final String[] DOC_ENTITIES = {
            "中华人民共和国劳动合同法", "劳动合同法", "中华人民共和国民法典", "民法典",
            "劳动争议调解仲裁法", "民法典合同编", "民法典侵权责任编", "民法典婚姻家庭编",
            "最高人民法院关于审理民间借贷案件适用法律若干问题的规定", "民间借贷司法解释"
    };

    /** 法律主题词实体（长词优先，避免被短词截断） */
    private static final String[] TOPIC_ENTITIES = {
            "违法解除劳动合同", "违法解除", "解除劳动合同", "经济补偿", "赔偿金", "二倍工资",
            "劳动合同", "劳动关系", "试用期", "加班费", "延长工作时间", "年休假", "工伤",
            "房屋租赁", "租赁合同", "承租人优先购买权", "优先购买权", "押金", "保证金", "定金",
            "租金", "转租", "返还租赁物", "民间借贷", "借款合同", "借款利息", "逾期还款",
            "违约金", "违约责任", "格式条款", "免除责任", "交通事故", "人身损害",
            "精神损害赔偿", "侵权责任", "诉讼时效", "夫妻共同财产", "子女抚养", "遗产继承"
    };

    /** 口语词到法律主题词的补全映射 */
    private static final Map<String, String[]> COLLOQUIAL_ENTITIES = Map.ofEntries(
            Map.entry("辞退", new String[] { "解除劳动合同", "经济补偿", "赔偿金" }),
            Map.entry("开除", new String[] { "解除劳动合同", "经济补偿" }),
            Map.entry("解雇", new String[] { "解除劳动合同", "经济补偿" }),
            Map.entry("裁员", new String[] { "解除劳动合同", "经济补偿" }),
            Map.entry("押金", new String[] { "押金", "返还租赁物" }),
            Map.entry("房东", new String[] { "房屋租赁", "出租人" }),
            Map.entry("租客", new String[] { "房屋租赁", "承租人" }),
            Map.entry("借钱", new String[] { "借款合同", "借款利息" }),
            Map.entry("欠钱", new String[] { "借款合同", "逾期还款" }),
            Map.entry("不还", new String[] { "逾期还款", "违约责任" }),
            Map.entry("利息", new String[] { "借款利息" }),
            Map.entry("霸王条款", new String[] { "格式条款", "免除责任" }),
            Map.entry("撞人", new String[] { "交通事故", "人身损害" }),
            Map.entry("打人", new String[] { "侵权责任", "人身损害" }));

    /** LLM 不可用时也可注入的问答客户端 */
    @Autowired(required = false)
    private AiChatClient aiChatClient;

    @Autowired
    private LawyerKnowledgeProperties knowledgeProperties;

    public QueryRewriter()
    {
    }

    /**
     * 测试与显式装配用构造器
     *
     * @param aiChatClient 问答客户端，可为 null（离线规则兜底）
     * @param knowledgeProperties 知识库配置
     */
    public QueryRewriter(AiChatClient aiChatClient, LawyerKnowledgeProperties knowledgeProperties)
    {
        this.aiChatClient = aiChatClient;
        this.knowledgeProperties = knowledgeProperties;
    }

    /**
     * 基于历史消息改写当前追问
     *
     * @param currentQuery 当前问题
     * @param history 历史消息（时间正序）
     * @return 改写结果
     */
    public QueryRewriteResult rewrite(String currentQuery, List<AiChatClient.ChatMessage> history)
    {
        long start = System.currentTimeMillis();
        String original = currentQuery == null ? "" : currentQuery.trim();
        boolean hasHistory = history != null && !history.isEmpty();
        if (!needsRewrite(original, hasHistory))
        {
            return QueryRewriteResult.passthrough(original, elapsed(start));
        }

        if (aiChatClient != null)
        {
            try
            {
                String candidate = callLlm(original, history);
                String normalized = normalizeLlmResult(candidate);
                if (normalized != null)
                {
                    boolean rewritten = !normalized.equals(original);
                    return new QueryRewriteResult(original, normalized, rewritten, "llm", elapsed(start));
                }
            }
            catch (Exception e)
            {
                log.warn("LLM Query 改写失败，降级规则实体补全: {}", e.getMessage());
            }
        }
        return ruleFallback(original, history, start);
    }

    /**
     * 基于纯文本历史上下文改写（检索调试接口使用）
     *
     * @param currentQuery 当前问题
     * @param historyContext 历史对话文本
     * @return 改写结果
     */
    public QueryRewriteResult rewriteWithContext(String currentQuery, String historyContext)
    {
        List<AiChatClient.ChatMessage> history = new ArrayList<>();
        if (StringUtils.isNotBlank(historyContext))
        {
            history.add(new AiChatClient.ChatMessage("user", historyContext.trim()));
        }
        return rewrite(currentQuery, history);
    }

    /**
     * 规则预判是否需要改写
     *
     * @param currentQuery 当前问题
     * @param hasHistory 是否存在历史消息
     * @return true 表示需要改写
     */
    public boolean needsRewrite(String currentQuery, boolean hasHistory)
    {
        if (knowledgeProperties == null || !knowledgeProperties.isRewriteEnabled())
        {
            return false;
        }
        if (currentQuery == null || currentQuery.isBlank() || !hasHistory)
        {
            return false;
        }
        String query = currentQuery.trim();
        if (query.length() < SHORT_QUERY_LENGTH)
        {
            return true;
        }
        for (String trigger : ANAPHORA_TRIGGERS)
        {
            if (query.contains(trigger))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * 调用轻量 LLM 改写
     */
    private String callLlm(String currentQuery, List<AiChatClient.ChatMessage> history)
    {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatClient.ChatMessage("system",
                "你是中国法律检索查询改写助手。结合历史对话，把用户当前追问改写为一句独立、完整、"
                        + "可直接用于中国法律法规检索的中文查询句（≤80字）。只输出改写后的句子本身，"
                        + "不要解释、不要加引号、不要回答问题。"));
        List<AiChatClient.ChatMessage> recent = recentHistory(history);
        messages.addAll(recent);
        messages.add(new AiChatClient.ChatMessage("user", "当前追问：" + currentQuery));
        long timeout = knowledgeProperties.getRewriteTimeout() > 0
                ? knowledgeProperties.getRewriteTimeout() : 8000L;
        String response = aiChatClient.chat(messages, Duration.ofMillis(timeout));
        return response;
    }

    private List<AiChatClient.ChatMessage> recentHistory(List<AiChatClient.ChatMessage> history)
    {
        List<AiChatClient.ChatMessage> result = new ArrayList<>();
        if (history == null || history.isEmpty())
        {
            return result;
        }
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++)
        {
            AiChatClient.ChatMessage message = history.get(i);
            if (message == null || message.getContent() == null || message.getContent().isBlank())
            {
                continue;
            }
            String content = message.getContent().trim();
            if (content.length() > MAX_HISTORY_MESSAGE_CHARS)
            {
                content = content.substring(0, MAX_HISTORY_MESSAGE_CHARS);
            }
            result.add(new AiChatClient.ChatMessage(message.getRole(), content));
        }
        return result;
    }

    /**
     * 校验并规整 LLM 输出；非法返回 null 触发规则兜底
     */
    private String normalizeLlmResult(String candidate)
    {
        if (candidate == null)
        {
            return null;
        }
        String value = candidate.trim();
        if (value.isEmpty() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
        {
            return null;
        }
        value = stripQuotes(value);
        value = value.replaceAll("^[：:]+", "").trim();
        if (value.length() < 2 || value.length() > MAX_REWRITTEN_CHARS)
        {
            return null;
        }
        if (value.contains("抱歉") || value.contains("无法回答") || value.contains("作为AI"))
        {
            return null;
        }
        return value;
    }

    /**
     * 规则实体提取补全兜底
     */
    private QueryRewriteResult ruleFallback(String original, List<AiChatClient.ChatMessage> history, long start)
    {
        Set<String> entities = new LinkedHashSet<>();
        for (String userText : recentUserTexts(history))
        {
            collectEntities(userText, entities);
        }
        if (entities.isEmpty())
        {
            String summary = previousUserSummary(history);
            if (StringUtils.isBlank(summary))
            {
                return QueryRewriteResult.passthrough(original, elapsed(start));
            }
            entities.add(summary);
        }

        StringBuilder prefix = new StringBuilder();
        for (String entity : entities)
        {
            if (StringUtils.isBlank(entity) || original.contains(entity))
            {
                continue;
            }
            if (prefix.length() > 0)
            {
                prefix.append(' ');
            }
            prefix.append(entity);
        }
        if (prefix.length() == 0)
        {
            return QueryRewriteResult.passthrough(original, elapsed(start));
        }
        String rewritten = prefix + " " + original;
        if (rewritten.length() > MAX_REWRITTEN_CHARS)
        {
            rewritten = rewritten.substring(0, MAX_REWRITTEN_CHARS);
        }
        return new QueryRewriteResult(original, rewritten, true, "rule_fallback", elapsed(start));
    }

    private void collectEntities(String text, Set<String> entities)
    {
        if (text == null || text.isBlank())
        {
            return;
        }
        // 条号
        entities.addAll(ChineseNumberUtils.extractArticleNos(text));
        // 法规名（长名优先）
        for (String doc : DOC_ENTITIES)
        {
            if (text.contains(doc))
            {
                entities.add(shortDocName(doc));
            }
        }
        // 法律主题词
        for (String topic : TOPIC_ENTITIES)
        {
            if (text.contains(topic))
            {
                entities.add(topic);
            }
        }
        // 口语词补全
        for (Map.Entry<String, String[]> entry : COLLOQUIAL_ENTITIES.entrySet())
        {
            if (text.contains(entry.getKey()))
            {
                for (String canonical : entry.getValue())
                {
                    entities.add(canonical);
                }
            }
        }
    }

    private String shortDocName(String doc)
    {
        if (doc.startsWith("中华人民共和国"))
        {
            return doc.substring("中华人民共和国".length());
        }
        return doc;
    }

    private List<String> recentUserTexts(List<AiChatClient.ChatMessage> history)
    {
        List<String> texts = new ArrayList<>();
        if (history == null)
        {
            return texts;
        }
        int start = Math.max(0, history.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < history.size(); i++)
        {
            AiChatClient.ChatMessage message = history.get(i);
            if (message != null && "user".equals(message.getRole())
                    && StringUtils.isNotBlank(message.getContent()))
            {
                texts.add(message.getContent());
            }
        }
        return texts;
    }

    private String previousUserSummary(List<AiChatClient.ChatMessage> history)
    {
        List<String> texts = recentUserTexts(history);
        if (texts.isEmpty())
        {
            return null;
        }
        String previous = texts.get(texts.size() - 1);
        String summary = previous.replaceAll("[\\p{Punct}，。？！；：、“”‘’（）【】《》…\\s]+", " ").trim();
        if (summary.length() > 40)
        {
            summary = summary.substring(0, 40);
        }
        return summary;
    }

    private String stripQuotes(String value)
    {
        String result = value.trim();
        boolean changed = true;
        while (changed && result.length() >= 2)
        {
            changed = false;
            char first = result.charAt(0);
            char last = result.charAt(result.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”') || (first == '‘' && last == '’')
                    || (first == '「' && last == '」') || (first == '『' && last == '』'))
            {
                result = result.substring(1, result.length() - 1).trim();
                changed = true;
            }
        }
        return result;
    }

    private long elapsed(long start)
    {
        return Math.max(0L, System.currentTimeMillis() - start);
    }
}
