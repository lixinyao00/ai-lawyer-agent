package com.ruoyi.lawyer.docx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.LineSpacingRule;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFRun.FontCharRange;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

/**
 * DocxDocumentExporter 单元测试
 *
 * <p>导出后用 XWPFDocument 回读，验证标题居中、正文首行缩进、此致顶格与落款右对齐。</p>
 *
 * @author ruoyi
 */
class DocxDocumentExporterTest
{
    private final DocxDocumentExporter exporter = new DocxDocumentExporter();

    @Test
    void exportsA4PageAndStandardPageMargins() throws Exception
    {
        try (XWPFDocument document = readBack("劳动仲裁申请书", "申请人：张三。"))
        {
            CTSectPr sectPr = document.getDocument().getBody().getSectPr();
            assertNotNull(sectPr);
            CTPageSz pageSize = sectPr.getPgSz();
            assertEquals(DocxDocumentExporter.PAGE_WIDTH, ((BigInteger) pageSize.getW()).intValue());
            assertEquals(DocxDocumentExporter.PAGE_HEIGHT, ((BigInteger) pageSize.getH()).intValue());
            CTPageMar margin = sectPr.getPgMar();
            assertEquals(DocxDocumentExporter.MARGIN_TOP_BOTTOM, ((BigInteger) margin.getTop()).intValue());
            assertEquals(DocxDocumentExporter.MARGIN_TOP_BOTTOM, ((BigInteger) margin.getBottom()).intValue());
            assertEquals(DocxDocumentExporter.MARGIN_LEFT_RIGHT, ((BigInteger) margin.getLeft()).intValue());
            assertEquals(DocxDocumentExporter.MARGIN_LEFT_RIGHT, ((BigInteger) margin.getRight()).intValue());
        }
    }

    @Test
    void titleIsCenteredSimSunBold22pt() throws Exception
    {
        try (XWPFDocument document = readBack("劳动仲裁申请书", "申请人：张三。"))
        {
            XWPFParagraph title = document.getParagraphs().get(0);
            assertEquals(ParagraphAlignment.CENTER, title.getAlignment());
            XWPFRun run = title.getRuns().get(0);
            assertTrue(run.isBold());
            assertEquals(DocxDocumentExporter.TITLE_FONT_SIZE, run.getFontSize());
            assertEquals("SimSun", run.getFontFamily());
            assertEquals("宋体", run.getFontFamily(FontCharRange.eastAsia));
        }
    }

    @Test
    void bodyUsesFangSong16ptFixed28ptAndFirstLineIndent() throws Exception
    {
        String content = String.join("\n",
                "事实与理由：张三于2020年3月1日入职被申请人处，月工资8000.00元。",
                "请求事项：裁决被申请人支付工资人民币8000.00元（捌仟元整）。");

        try (XWPFDocument document = readBack("劳动仲裁申请书", content))
        {
            XWPFParagraph body = document.getParagraphs().get(1);
            assertEquals(ParagraphAlignment.LEFT, body.getAlignment());
            assertEquals(DocxDocumentExporter.FIRST_LINE_INDENT, body.getIndentationFirstLine());
            assertEquals(DocxDocumentExporter.BODY_LINE_SPACING, body.getSpacingBetween(), 0.1D);
            assertEquals(LineSpacingRule.EXACT, body.getSpacingLineRule());
            XWPFRun run = body.getRuns().get(0);
            assertEquals(DocxDocumentExporter.BODY_FONT_SIZE, run.getFontSize());
            assertEquals("FangSong", run.getFontFamily());
            assertEquals("仿宋_GB2312", run.getFontFamily(FontCharRange.eastAsia));
        }
    }

    @Test
    void keepsBlankLinesAndAlignsCiZhiAndNextLineFlushLeft() throws Exception
    {
        String content = String.join("\n",
                "申请人：张三，男，1990年1月1日出生，住北京市朝阳区XX路1号。",
                "请求事项：裁决被申请人支付工资人民币8000.00元（捌仟元整）。",
                "",
                "此致",
                "北京市劳动人事争议仲裁委员会",
                "申请人：张三",
                "2026年11月21日");

        try (XWPFDocument document = readBack("劳动仲裁申请书", content))
        {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            // 0 标题；1、2 正文；3 空行；4 此致；5 仲裁委；6 署名；7 日期
            assertEquals(8, paragraphs.size());
            assertEquals("", paragraphs.get(3).getText());
            assertEquals(DocxDocumentExporter.FIRST_LINE_INDENT, paragraphs.get(3).getIndentationFirstLine());

            XWPFParagraph ciZhi = paragraphs.get(4);
            assertTrue(ciZhi.getText().startsWith("此致"));
            assertEquals(0, ciZhi.getIndentationFirstLine());
            assertEquals(ParagraphAlignment.LEFT, ciZhi.getAlignment());

            XWPFParagraph commission = paragraphs.get(5);
            assertEquals("北京市劳动人事争议仲裁委员会", commission.getText());
            assertEquals(0, commission.getIndentationFirstLine());
            assertEquals(ParagraphAlignment.LEFT, commission.getAlignment());
        }
    }

    @Test
    void alignsSignatureAndDateRightWithoutIndent() throws Exception
    {
        String content = String.join("\n",
                "申请人：张三，男，1990年1月1日出生，住北京市朝阳区XX路1号。",
                "此致",
                "北京市劳动人事争议仲裁委员会",
                "申请人：张三",
                "2026年11月21日");

        try (XWPFDocument document = readBack("劳动仲裁申请书", content))
        {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            XWPFParagraph signature = paragraphs.get(4);
            assertEquals("申请人：张三", signature.getText());
            assertEquals(ParagraphAlignment.RIGHT, signature.getAlignment());
            assertEquals(0, signature.getIndentationFirstLine());

            XWPFParagraph date = paragraphs.get(5);
            assertEquals("2026年11月21日", date.getText());
            assertEquals(ParagraphAlignment.RIGHT, date.getAlignment());
            assertEquals(0, date.getIndentationFirstLine());
        }
    }

    @Test
    void ignoresSignatureLikeLineOutsideLastEightNonEmptyLines() throws Exception
    {
        StringBuilder content = new StringBuilder("申请人：李四");
        for (int i = 1; i <= 10; i++)
        {
            content.append("\n正文第").append(i).append("段。");
        }
        content.append("\n2026年11月21日");

        try (XWPFDocument document = readBack("借条", content.toString()))
        {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            // 首行虽匹配署名规则，但不在最后 8 个非空行内，应保持左对齐
            assertEquals("申请人：李四", paragraphs.get(1).getText());
            assertEquals(ParagraphAlignment.LEFT, paragraphs.get(1).getAlignment());
            assertEquals(DocxDocumentExporter.FIRST_LINE_INDENT, paragraphs.get(1).getIndentationFirstLine());
            // 末尾日期仍在最后 8 行内，右对齐
            XWPFParagraph date = paragraphs.get(paragraphs.size() - 1);
            assertEquals(ParagraphAlignment.RIGHT, date.getAlignment());
        }
    }

    @Test
    void matchesLawFirmSignatureLine() throws Exception
    {
        String content = String.join("\n",
                "债务人应于收到本函之日起七日内履行还款义务。",
                "XX律师事务所：王律师",
                "2026年11月21日");

        try (XWPFDocument document = readBack("律师催款函", content))
        {
            XWPFParagraph lawFirm = document.getParagraphs().get(2);
            assertEquals("XX律师事务所：王律师", lawFirm.getText());
            assertEquals(ParagraphAlignment.RIGHT, lawFirm.getAlignment());
            assertFalse(lawFirm.getRuns().isEmpty());
        }
    }

    private XWPFDocument readBack(String title, String content) throws Exception
    {
        byte[] bytes = exporter.export(title, content);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }
}
