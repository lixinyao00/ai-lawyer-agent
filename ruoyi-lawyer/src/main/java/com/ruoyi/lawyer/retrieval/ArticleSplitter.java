package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法规条文切分器（纯函数，可单测）
 *
 * <p>按“第X条”切分法规文本，识别编/章/节标题作为切片关键词前缀；
 * 序言、超长条文与无条号文本按约定规则降级处理。</p>
 *
 * @author ruoyi
 */
public final class ArticleSplitter
{
    /** 单条最大长度 */
    public static final int MAX_ARTICLE_CHARS = 4000;

    /** 无条号分段最大长度 */
    public static final int MAX_PARAGRAPH_CHARS = 1000;

    /** 序言最小长度，低于该长度视为标题/目录丢弃 */
    public static final int MIN_PREAMBLE_CHARS = 20;

    /** 条文起始行 */
    private static final Pattern ARTICLE_LINE = Pattern.compile(
            "^\\s*第([零〇一二三四五六七八九十百千两\\d]+)条[\\s ]*");

    /** 章节标题行：条号数字后必须紧跟编/章/节，避免把“第一千一百六十四条 本编……”误判为章节 */
    private static final Pattern CHAPTER_LINE = Pattern.compile(
            "^\\s*第[零〇一二三四五六七八九十百千两\\d]+分?[编章节]");

    /** 句号切分（保留句末标点） */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；])");

    private ArticleSplitter()
    {
    }

    /**
     * 切分法规文本
     *
     * @param rawText 原文
     * @return 切片集合，按原文顺序
     */
    public static List<Chunk> split(String rawText)
    {
        List<Chunk> chunks = new ArrayList<>();
        String text = normalize(rawText);
        if (text.isEmpty())
        {
            return chunks;
        }

        String[] lines = text.split("\n", -1);
        String[] chapterTitles = new String[] { "", "", "" };
        StringBuilder preamble = new StringBuilder();
        StringBuilder articleContent = new StringBuilder();
        String articleNo = null;
        boolean sawArticle = false;

        for (String line : lines)
        {
            if (isChapterLine(line))
            {
                if (articleNo != null)
                {
                    flushArticle(chunks, articleNo, articleContent, chapterTitles);
                    articleNo = null;
                    articleContent.setLength(0);
                }
                updateChapterTitles(chapterTitles, line.trim());
                continue;
            }

            Matcher matcher = ARTICLE_LINE.matcher(line);
            if (matcher.find())
            {
                if (!sawArticle)
                {
                    flushPreamble(chunks, preamble, chapterTitles);
                    preamble.setLength(0);
                    sawArticle = true;
                }
                if (articleNo != null)
                {
                    flushArticle(chunks, articleNo, articleContent, chapterTitles);
                    articleContent.setLength(0);
                }
                String normalized = ChineseNumberUtils.normalizeArticleNo(matcher.group(1));
                articleNo = normalized;
                articleContent.append(line.substring(matcher.end()));
                continue;
            }

            if (articleNo != null)
            {
                articleContent.append('\n').append(line);
            }
            else if (!sawArticle)
            {
                preamble.append(line).append('\n');
            }
        }

        if (articleNo != null)
        {
            flushArticle(chunks, articleNo, articleContent, chapterTitles);
        }
        else if (!sawArticle)
        {
            // 全文没有任何“第X条”，按空行分段
            appendParagraphChunks(chunks, preamble.toString(), joinTitles(chapterTitles));
        }
        return chunks;
    }

    /**
     * 预处理：统一换行、去 BOM、全角空格转半角
     *
     * @param rawText 原文
     * @return 规范化文本
     */
    public static String normalize(String rawText)
    {
        if (rawText == null)
        {
            return "";
        }
        String text = rawText;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF')
        {
            text = text.substring(1);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        text = text.replace('\u3000', ' ');
        return text;
    }

    private static boolean isChapterLine(String line)
    {
        return CHAPTER_LINE.matcher(line).find();
    }

    private static void updateChapterTitles(String[] chapterTitles, String line)
    {
        if (line.contains("编"))
        {
            chapterTitles[0] = line;
            chapterTitles[1] = "";
            chapterTitles[2] = "";
        }
        else if (line.contains("章"))
        {
            chapterTitles[1] = line;
            chapterTitles[2] = "";
        }
        else
        {
            chapterTitles[2] = line;
        }
    }

    private static String joinTitles(String[] chapterTitles)
    {
        StringBuilder builder = new StringBuilder();
        for (String title : chapterTitles)
        {
            if (title != null && !title.isBlank())
            {
                if (builder.length() > 0)
                {
                    builder.append(' ');
                }
                builder.append(title.trim());
            }
        }
        return builder.toString();
    }

    private static void flushPreamble(List<Chunk> chunks, StringBuilder preamble, String[] chapterTitles)
    {
        String content = preamble.toString().trim();
        if (content.length() < MIN_PREAMBLE_CHARS)
        {
            return;
        }
        chunks.add(new Chunk(null, content, joinTitles(chapterTitles)));
    }

    private static void flushArticle(List<Chunk> chunks, String articleNo, StringBuilder content,
            String[] chapterTitles)
    {
        String text = content.toString().trim();
        if (text.isEmpty())
        {
            return;
        }
        String keywords = joinTitles(chapterTitles);
        if (text.length() <= MAX_ARTICLE_CHARS)
        {
            chunks.add(new Chunk(articleNo, text, keywords));
            return;
        }
        for (String piece : splitLongText(text, MAX_ARTICLE_CHARS))
        {
            chunks.add(new Chunk(articleNo, piece, keywords));
        }
    }

    private static void appendParagraphChunks(List<Chunk> chunks, String text, String keywords)
    {
        String content = text.trim();
        if (content.isEmpty())
        {
            return;
        }
        String[] paragraphs = content.split("\\n\\s*\\n");
        for (String paragraph : paragraphs)
        {
            String item = paragraph.trim();
            if (item.isEmpty())
            {
                continue;
            }
            for (String piece : splitLongText(item, MAX_PARAGRAPH_CHARS))
            {
                chunks.add(new Chunk(null, piece, keywords));
            }
        }
    }

    /**
     * 按句子切分超长文本，每片不超过 maxChars
     */
    private static List<String> splitLongText(String text, int maxChars)
    {
        List<String> pieces = new ArrayList<>();
        if (text.length() <= maxChars)
        {
            pieces.add(text);
            return pieces;
        }
        String[] sentences = SENTENCE_END.split(text);
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences)
        {
            if (sentence.isEmpty())
            {
                continue;
            }
            if (sentence.length() > maxChars)
            {
                if (current.length() > 0)
                {
                    pieces.add(current.toString().trim());
                    current.setLength(0);
                }
                for (int start = 0; start < sentence.length(); start += maxChars)
                {
                    pieces.add(sentence.substring(start, Math.min(start + maxChars, sentence.length())).trim());
                }
                continue;
            }
            if (current.length() + sentence.length() > maxChars)
            {
                pieces.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0)
        {
            pieces.add(current.toString().trim());
        }
        return pieces;
    }

    /**
     * 切片结果
     */
    public static final class Chunk
    {
        /** 规范化条号，序言/无条号段为 null */
        private final String articleNo;

        /** 条文内容（不含章节标题） */
        private final String content;

        /** 章节标题关键词前缀 */
        private final String keywords;

        public Chunk(String articleNo, String content, String keywords)
        {
            this.articleNo = articleNo;
            this.content = content;
            this.keywords = keywords;
        }

        public String getArticleNo()
        {
            return articleNo;
        }

        public String getContent()
        {
            return content;
        }

        public String getKeywords()
        {
            return keywords;
        }
    }
}
