package com.ruoyi.lawyer.retrieval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ChineseNumberUtils 单元测试
 *
 * @author ruoyi
 */
class ChineseNumberUtilsTest
{
    @Test
    void arabicToChinese()
    {
        assertEquals("四十七", ChineseNumberUtils.toChinese(47));
        assertEquals("一百零三", ChineseNumberUtils.toChinese(103));
        assertEquals("一千一百六十五", ChineseNumberUtils.toChinese(1165));
        assertEquals("十", ChineseNumberUtils.toChinese(10));
        assertEquals("一百一十", ChineseNumberUtils.toChinese(110));
        assertEquals("一千零一十", ChineseNumberUtils.toChinese(1010));
    }

    @Test
    void chineseToArabic()
    {
        assertEquals(47, ChineseNumberUtils.toArabic("四十七"));
        assertEquals(103, ChineseNumberUtils.toArabic("一百零三"));
        assertEquals(1165, ChineseNumberUtils.toArabic("一千一百六十五"));
        assertEquals(110, ChineseNumberUtils.toArabic("一百一十"));
        assertEquals(2, ChineseNumberUtils.toArabic("两"));
        assertEquals(47, ChineseNumberUtils.toArabic("47"));
    }

    @Test
    void normalizeArticleNo()
    {
        assertEquals("第四十七条", ChineseNumberUtils.normalizeArticleNo("47"));
        assertEquals("第四十七条", ChineseNumberUtils.normalizeArticleNo("第四十七条"));
        assertEquals("第一百零三条", ChineseNumberUtils.normalizeArticleNo("第103条"));
        assertEquals("第一千一百六十五条", ChineseNumberUtils.normalizeArticleNo("1165"));
        assertEquals("第一千一百六十五条", ChineseNumberUtils.normalizeArticleNo("第一千一百六十五条"));
        assertNull(ChineseNumberUtils.normalizeArticleNo("附则"));
    }

    @Test
    void extractArticleNos()
    {
        List<String> articles = ChineseNumberUtils.extractArticleNos("依据劳动合同法第四十七条、第87条处理");
        assertEquals(List.of("第四十七条", "第八十七条"), articles);
        assertTrue(ChineseNumberUtils.extractArticleNos("没有条号").isEmpty());
    }
}
