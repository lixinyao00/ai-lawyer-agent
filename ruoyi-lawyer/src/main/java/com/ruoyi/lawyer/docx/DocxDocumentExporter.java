package com.ruoyi.lawyer.docx;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/**
 * 标准公文排版 DOCX 导出器
 *
 * <p>页面 A4；标题居中、宋体加粗 22pt；正文仿宋 16pt、固定行距 28pt、首行缩进 2 字符。
 * 支持“此致”顶格与落款右对齐规则。</p>
 *
 * @author ruoyi
 */
@Component
public class DocxDocumentExporter
{
    /** A4 纸张宽度（twips） */
    public static final int PAGE_WIDTH = 11906;

    /** A4 纸张高度（twips） */
    public static final int PAGE_HEIGHT = 16838;

    /** 上下页边距 2.54cm（twips） */
    public static final int MARGIN_TOP_BOTTOM = 1440;

    /** 左右页边距 3.17cm（twips） */
    public static final int MARGIN_LEFT_RIGHT = 1800;

    /** 正文固定行距（磅） */
    public static final double BODY_LINE_SPACING = 28D;

    /** 正文首行缩进 2 字符（twips，约 2 × 16pt × 20） */
    public static final int FIRST_LINE_INDENT = 640;

    /** 标题字号（磅，二号） */
    public static final int TITLE_FONT_SIZE = 22;

    /** 正文字号（磅，三号） */
    public static final int BODY_FONT_SIZE = 16;

    /** 标题段后间距（twips） */
    private static final int TITLE_SPACING_AFTER = 360;

    /** 落款识别范围：全文最后 N 个非空行 */
    public static final int SIGNATURE_SCAN_LINES = 8;

    /** 落款署名行：申请人：xxx / 律师：xxx / XX律师事务所：xxx */
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile(
            "^(申请人|具状人|出借人|借款人|出租人|承租人|委托人|律师|.{0,40}律师事务所)[：:].*");

    /** 落款日期行：2026年11月21日 */
    private static final Pattern DATE_LINE_PATTERN = Pattern.compile(
            "^\\s*[0-9〇零一二三四五六七八九十]{4}年.{1,3}月.{1,3}日\\s*$");

    private static final String EAST_ASIA_TITLE_FONT = "宋体";

    private static final String TITLE_FONT = "SimSun";

    private static final String EAST_ASIA_BODY_FONT = "仿宋_GB2312";

    private static final String BODY_FONT = "FangSong";

    /**
     * 导出 DOCX 字节流
     *
     * @param title 文书标题
     * @param content 文书正文纯文本（按 \n 分段）
     * @return .docx 文件字节
     */
    public byte[] export(String title, String content)
    {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            configurePage(document);
            appendTitle(document, title);
            appendBody(document, content);
            document.write(out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            throw new ServiceException("文书导出失败，请稍后重试");
        }
    }

    private void configurePage(XWPFDocument document)
    {
        CTSectPr sectPr = document.getDocument().getBody().isSetSectPr()
                ? document.getDocument().getBody().getSectPr()
                : document.getDocument().getBody().addNewSectPr();
        CTPageSz pageSize = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(PAGE_WIDTH));
        pageSize.setH(BigInteger.valueOf(PAGE_HEIGHT));
        CTPageMar margin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margin.setTop(BigInteger.valueOf(MARGIN_TOP_BOTTOM));
        margin.setBottom(BigInteger.valueOf(MARGIN_TOP_BOTTOM));
        margin.setLeft(BigInteger.valueOf(MARGIN_LEFT_RIGHT));
        margin.setRight(BigInteger.valueOf(MARGIN_LEFT_RIGHT));
    }

    private void appendTitle(XWPFDocument document, String title)
    {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        paragraph.setSpacingAfter(TITLE_SPACING_AFTER);
        XWPFRun run = paragraph.createRun();
        run.setText(title == null ? "" : title.trim());
        run.setBold(true);
        run.setFontSize(TITLE_FONT_SIZE);
        run.setFontFamily(TITLE_FONT);
        run.setFontFamily(EAST_ASIA_TITLE_FONT, FontCharRange.eastAsia);
    }

    private void appendBody(XWPFDocument document, String content)
    {
        String normalized = content == null ? ""
                : content.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        Set<Integer> signatureLines = resolveSignatureLines(lines);
        boolean alignNextLineLeft = false;
        for (int i = 0; i < lines.length; i++)
        {
            String line = lines[i];
            String trimmed = line.trim();
            boolean rightAligned = signatureLines.contains(i);
            boolean ciZhi = trimmed.startsWith("此致");
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.setSpacingBefore(0);
            paragraph.setSpacingAfter(0);
            paragraph.setSpacingBetween(BODY_LINE_SPACING, LineSpacingRule.EXACT);
            if (rightAligned)
            {
                paragraph.setAlignment(ParagraphAlignment.RIGHT);
                paragraph.setIndentationFirstLine(0);
            }
            else if (ciZhi || alignNextLineLeft)
            {
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                paragraph.setIndentationFirstLine(0);
            }
            else
            {
                paragraph.setAlignment(ParagraphAlignment.LEFT);
                paragraph.setIndentationFirstLine(FIRST_LINE_INDENT);
            }
            if (!line.isEmpty())
            {
                appendBodyRun(paragraph, line);
            }
            // “此致”的紧邻下一行顶格，仅作用于下一行
            alignNextLineLeft = ciZhi;
        }
    }

    private void appendBodyRun(XWPFParagraph paragraph, String text)
    {
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontSize(BODY_FONT_SIZE);
        run.setFontFamily(BODY_FONT);
        run.setFontFamily(EAST_ASIA_BODY_FONT, FontCharRange.eastAsia);
    }

    /**
     * 找出需要右对齐的落款行：仅扫描最后 8 个非空行
     */
    private Set<Integer> resolveSignatureLines(String[] lines)
    {
        List<Integer> nonEmpty = new ArrayList<>();
        for (int i = 0; i < lines.length; i++)
        {
            if (!lines[i].trim().isEmpty())
            {
                nonEmpty.add(i);
            }
        }
        Set<Integer> result = new HashSet<>();
        int from = Math.max(0, nonEmpty.size() - SIGNATURE_SCAN_LINES);
        for (int i = from; i < nonEmpty.size(); i++)
        {
            int index = nonEmpty.get(i);
            String line = lines[index].trim();
            if (SIGNATURE_PATTERN.matcher(line).matches() || DATE_LINE_PATTERN.matcher(line).matches())
            {
                result.add(index);
            }
        }
        return result;
    }
}
