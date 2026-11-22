package com.ruoyi.lawyer.retrieval;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文数字工具
 *
 * <p>提供阿拉伯数字与中文数字的双向转换（1~9999），以及法条条号的规范化。</p>
 *
 * @author ruoyi
 */
public final class ChineseNumberUtils
{
    /** 中文数字字符 */
    private static final String DIGITS = "零一二三四五六七八九";

    /** 条号正则：匹配 第X条，X 可为中文数字或阿拉伯数字 */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "第\\s*([零〇一二三四五六七八九十百千两\\d]+)\\s*条");

    private ChineseNumberUtils()
    {
    }

    /**
     * 阿拉伯数字转中文数字，支持 1~9999
     *
     * @param number 阿拉伯数字
     * @return 中文数字，越界时原样返回
     */
    public static String toChinese(int number)
    {
        if (number < 0 || number > 9999)
        {
            return String.valueOf(number);
        }
        if (number == 0)
        {
            return "零";
        }
        if (number < 10)
        {
            return String.valueOf(DIGITS.charAt(number));
        }
        if (number < 20)
        {
            return number == 10 ? "十" : "十" + DIGITS.charAt(number % 10);
        }
        if (number < 100)
        {
            int tens = number / 10;
            int ones = number % 10;
            return DIGITS.charAt(tens) + "十" + (ones == 0 ? "" : String.valueOf(DIGITS.charAt(ones)));
        }
        if (number < 1000)
        {
            int hundreds = number / 100;
            int rest = number % 100;
            if (rest == 0)
            {
                return DIGITS.charAt(hundreds) + "百";
            }
            if (rest < 10)
            {
                return DIGITS.charAt(hundreds) + "百零" + DIGITS.charAt(rest);
            }
            if (rest < 20)
            {
                // 110 应为“一百一十”，而不是“一百十”
                return DIGITS.charAt(hundreds) + "百一" + toChinese(rest);
            }
            return DIGITS.charAt(hundreds) + "百" + toChinese(rest);
        }
        int thousands = number / 1000;
        int rest = number % 1000;
        if (rest == 0)
        {
            return DIGITS.charAt(thousands) + "千";
        }
        if (rest < 100)
        {
            if (rest < 10)
            {
                return DIGITS.charAt(thousands) + "千零" + DIGITS.charAt(rest);
            }
            if (rest < 20)
            {
                return DIGITS.charAt(thousands) + "千零一" + toChinese(rest);
            }
            return DIGITS.charAt(thousands) + "千零" + toChinese(rest);
        }
        return DIGITS.charAt(thousands) + "千" + toChinese(rest);
    }

    /**
     * 中文数字转阿拉伯数字，支持 1~9999
     *
     * @param chinese 中文数字（支持混用阿拉伯数字，如“47”）
     * @return 阿拉伯数字；无法识别时返回 null
     */
    public static Integer toArabic(String chinese)
    {
        if (chinese == null || chinese.isBlank())
        {
            return null;
        }
        String text = chinese.trim();
        if (text.chars().allMatch(Character::isDigit))
        {
            try
            {
                return Integer.valueOf(text);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }

        int result = 0;
        int section = 0;
        int pendingDigit = -1;
        boolean hasAny = false;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '零' || c == '〇')
            {
                pendingDigit = 0;
                hasAny = true;
                continue;
            }
            int digit = digitValue(c);
            if (digit >= 0)
            {
                pendingDigit = digit;
                hasAny = true;
                continue;
            }
            int unit = unitValue(c);
            if (unit > 0)
            {
                hasAny = true;
                if (pendingDigit < 0)
                {
                    // “十二”这类省略了前导一
                    pendingDigit = 1;
                }
                section += pendingDigit * unit;
                pendingDigit = -1;
                continue;
            }
            // 其它字符直接忽略，避免脏数据导致整体解析失败
        }
        if (!hasAny)
        {
            return null;
        }
        if (pendingDigit > 0)
        {
            section += pendingDigit;
        }
        result += section;
        return result;
    }

    /**
     * 规范化条号：无论输入是阿拉伯数字还是中文数字，统一返回“第X条”中文格式
     *
     * @param raw 条号原文，如“47”“四十七”“第一百零三条”“第47条”
     * @return 规范化条号；无法识别时返回 null
     */
    public static String normalizeArticleNo(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return null;
        }
        String text = raw.trim();
        Matcher matcher = ARTICLE_PATTERN.matcher(text);
        if (matcher.find())
        {
            text = matcher.group(1);
        }
        Integer number = toArabic(text);
        if (number == null || number <= 0)
        {
            return null;
        }
        return "第" + toChinese(number) + "条";
    }

    /**
     * 从文本中抽取全部条号，返回规范化中文条号（去重、保持出现顺序）
     *
     * @param text 文本
     * @return 规范化条号集合
     */
    public static List<String> extractArticleNos(String text)
    {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank())
        {
            return result;
        }
        Matcher matcher = ARTICLE_PATTERN.matcher(text);
        while (matcher.find())
        {
            String normalized = normalizeArticleNo(matcher.group(1));
            if (normalized != null && !result.contains(normalized))
            {
                result.add(normalized);
            }
        }
        return result;
    }

    private static int digitValue(char c)
    {
        if (c == '两')
        {
            return 2;
        }
        int index = DIGITS.indexOf(c);
        return index;
    }

    private static int unitValue(char c)
    {
        return switch (c)
        {
            case '十' -> 10;
            case '百' -> 100;
            case '千' -> 1000;
            default -> 0;
        };
    }
}
