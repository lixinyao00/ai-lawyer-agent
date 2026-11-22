package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律文本分词器
 *
 * <p>中文按字符二元组（bigram）切分，英文/数字按整词切分，并抽取法条号与法规名核心词。
 * 停用字在生成 bigram 前剔除。</p>
 *
 * @author ruoyi
 */
public final class LegalTokenizer
{
    /** 中文停用字/词 */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "和", "与", "及", "或", "等", "我", "你", "他", "吗", "呢",
            "怎么", "如何", "什么");

    /** 中文连续片段 */
    private static final Pattern CHINESE_RUN = Pattern.compile("[\u4e00-\u9fa5]+");

    /** 英文/数字整词 */
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+");

    /** 条号片段（生成 bigram 前剔除，避免条号数字干扰 BM25） */
    private static final Pattern ARTICLE_FRAGMENT = Pattern.compile(
            "第\\s*[零〇一二三四五六七八九十百千两\\d]+\\s*条");

    /** 括号内容 */
    private static final Pattern BRACKETS = Pattern.compile("[（(][^）)]*[）)]");

    private LegalTokenizer()
    {
    }

    /**
     * 分词（不含法规名 token）
     *
     * @param text 文本
     * @return token 列表（保留重复，便于计算词频）
     */
    public static List<String> tokenize(String text)
    {
        return tokenize(text, null);
    }

    /**
     * 分词并追加法规名核心词 token
     *
     * @param text 文本
     * @param docTitle 所属法规名称，可为空
     * @return token 列表
     */
    public static List<String> tokenize(String text, String docTitle)
    {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank())
        {
            appendLegalNameTokens(tokens, docTitle);
            return tokens;
        }

        String normalized = text;
        // 先抽取条号，避免条号中的中文被 bigram 拆散
        tokens.addAll(ChineseNumberUtils.extractArticleNos(normalized));

        // 条号本身由独立 token 承担，生成 bigram 前先剔除，避免“第四十七条”污染词频
        String bigramText = ARTICLE_FRAGMENT.matcher(normalized).replaceAll(" ");

        // 抽取英文/数字整词
        Matcher wordMatcher = WORD.matcher(bigramText);
        while (wordMatcher.find())
        {
            tokens.add(wordMatcher.group().toLowerCase());
        }

        // 中文连续片段去停用字后生成 bigram
        Matcher runMatcher = CHINESE_RUN.matcher(bigramText);
        while (runMatcher.find())
        {
            String run = removeStopWords(runMatcher.group());
            appendBigrams(tokens, run);
        }

        appendLegalNameTokens(tokens, docTitle);
        return tokens;
    }

    /**
     * 法规名称核心词：去掉“中华人民共和国”、书名号与括号内容
     *
     * <p>如“中华人民共和国劳动合同法” → “劳动合同法”，“中华人民共和国民法典” → “民法典”。</p>
     *
     * @param title 法规名称
     * @return 核心名称，无法提取时返回空串
     */
    public static String coreName(String title)
    {
        if (title == null || title.isBlank())
        {
            return "";
        }
        String text = title.replace("《", "").replace("》", "");
        text = BRACKETS.matcher(text).replaceAll("");
        text = text.replace("中华人民共和国", "");
        return text.trim();
    }

    /**
     * 法规名 token：核心名整体 + 其 bigram
     *
     * @param title 法规名称
     * @return token 列表
     */
    public static List<String> legalNameTokens(String title)
    {
        List<String> tokens = new ArrayList<>();
        appendLegalNameTokens(tokens, title);
        return tokens;
    }

    private static void appendLegalNameTokens(List<String> tokens, String title)
    {
        String core = coreName(title);
        if (core.isEmpty())
        {
            return;
        }
        tokens.add(core);
        if (core.length() > 2)
        {
            appendBigrams(tokens, core);
        }
    }

    private static String removeStopWords(String run)
    {
        String result = run;
        for (String stop : STOP_WORDS)
        {
            if (result.contains(stop))
            {
                result = result.replace(stop, "");
            }
        }
        return result;
    }

    private static void appendBigrams(List<String> tokens, String run)
    {
        if (run.isEmpty())
        {
            return;
        }
        if (run.length() == 1)
        {
            tokens.add(run);
            return;
        }
        for (int i = 0; i < run.length() - 1; i++)
        {
            tokens.add(run.substring(i, i + 2));
        }
    }

    /**
     * 文本中出现的法规名核心词（去重，保持顺序）
     *
     * @param text 查询文本
     * @param titles 候选法规名称
     * @return 命中的核心名称
     */
    public static List<String> matchedCoreNames(String text, Iterable<String> titles)
    {
        Set<String> matched = new LinkedHashSet<>();
        if (text == null || text.isBlank())
        {
            return new ArrayList<>(matched);
        }
        for (String title : titles)
        {
            String core = coreName(title);
            if (core.length() >= 2 && text.contains(core))
            {
                matched.add(core);
            }
        }
        return new ArrayList<>(matched);
    }
}
