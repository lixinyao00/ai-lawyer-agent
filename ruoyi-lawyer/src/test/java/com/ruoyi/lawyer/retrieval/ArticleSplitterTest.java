package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ArticleSplitter 单元测试
 *
 * @author ruoyi
 */
class ArticleSplitterTest
{
    @Test
    void splitsArticlesAndKeepsChapterKeywords()
    {
        String text = "\uFEFF第一章 总则\n\n第一条 为了完善劳动合同制度。\n\n第二章 劳动合同的订立\n\n"
                + "第七条 用人单位自用工之日起即与劳动者建立劳动关系。\n"
                + "用人单位应当建立职工名册备查。\n\n第四十七条 经济补偿按劳动者在本单位工作的年限计算。\n";

        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(text);

        assertEquals(3, chunks.size());
        assertEquals("第一条", chunks.get(0).getArticleNo());
        assertTrue(chunks.get(0).getKeywords().contains("第一章 总则"));
        assertEquals("第七条", chunks.get(1).getArticleNo());
        assertTrue(chunks.get(1).getKeywords().contains("第二章 劳动合同的订立"));
        // 章节标题不进正文
        assertFalse(chunks.get(1).getContent().contains("第二章"));
        assertTrue(chunks.get(1).getContent().contains("职工名册"));
        assertEquals("第四十七条", chunks.get(2).getArticleNo());
    }

    @Test
    void articleContentStartingWithBenBianIsNotChapter()
    {
        String text = "第七编 侵权责任\n第一章 一般规定\n\n"
                + "第一千一百六十四条 本编调整因侵害民事权益产生的民事关系。\n\n"
                + "第一千一百六十五条 行为人因过错侵害他人民事权益造成损害的，应当承担侵权责任。";
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(text);
        assertEquals(2, chunks.size());
        assertEquals("第一千一百六十四条", chunks.get(0).getArticleNo());
        assertTrue(chunks.get(0).getContent().startsWith("本编调整"));
        assertEquals("第一千一百六十五条", chunks.get(1).getArticleNo());
    }

    @Test
    void normalizesArabicArticleNumber()
    {
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split("第47条 经济补偿按劳动者在本单位工作的年限计算。");
        assertEquals(1, chunks.size());
        assertEquals("第四十七条", chunks.get(0).getArticleNo());
        assertEquals("经济补偿按劳动者在本单位工作的年限计算。", chunks.get(0).getContent());
    }

    @Test
    void preambleOverTwentyCharsBecomesChunk()
    {
        String text = "这是本法规的序言内容，用于说明立法目的与适用范围。\n\n第一章 总则\n\n第一条 条文内容。";
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(text);
        assertEquals(2, chunks.size());
        assertNull(chunks.get(0).getArticleNo());
        assertTrue(chunks.get(0).getContent().contains("立法目的"));
        assertEquals("第一条", chunks.get(1).getArticleNo());
    }

    @Test
    void longArticleIsSplitBySentence()
    {
        String sentence = "甲乙双方应当按照约定全面履行各自的义务。";
        StringBuilder builder = new StringBuilder("第一条 ");
        for (int i = 0; i < 300; i++)
        {
            builder.append(sentence);
        }
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(builder.toString());
        assertTrue(chunks.size() > 1);
        for (ArticleSplitter.Chunk chunk : chunks)
        {
            assertEquals("第一条", chunk.getArticleNo());
            assertTrue(chunk.getContent().length() <= ArticleSplitter.MAX_ARTICLE_CHARS);
        }
    }

    @Test
    void textWithoutArticleSplitsByBlankLine()
    {
        String paragraph = "这是没有条号的法律说明文字，".repeat(60);
        String text = paragraph + "\n\n" + paragraph;
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(text);
        assertFalse(chunks.isEmpty());
        for (ArticleSplitter.Chunk chunk : chunks)
        {
            assertNull(chunk.getArticleNo());
            assertTrue(chunk.getContent().length() <= ArticleSplitter.MAX_PARAGRAPH_CHARS);
        }
    }

    @Test
    void normalizesCrLfAndFullWidthSpace()
    {
        String text = "第一条\u3000内容一。\r\n第二条  内容二。";
        List<ArticleSplitter.Chunk> chunks = ArticleSplitter.split(text);
        assertEquals(2, chunks.size());
        assertEquals("第一条", chunks.get(0).getArticleNo());
        assertEquals("内容一。", chunks.get(0).getContent());
        assertEquals("内容二。", chunks.get(1).getContent());
    }
}
