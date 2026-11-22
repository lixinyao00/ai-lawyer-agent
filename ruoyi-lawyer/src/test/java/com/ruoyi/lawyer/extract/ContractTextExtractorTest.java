package com.ruoyi.lawyer.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.ruoyi.common.exception.ServiceException;

/**
 * ContractTextExtractor 单元测试（现场生成 docx/pdf 样例）
 *
 * @author ruoyi
 */
class ContractTextExtractorTest
{
    private static final Charset GB18030 = Charset.forName("GB18030");

    private final ContractTextExtractor extractor = new ContractTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extractsDocxParagraphsAndTablesInOrder() throws Exception
    {
        Path file = tempDir.resolve("contract.docx");
        try (XWPFDocument document = new XWPFDocument())
        {
            document.createParagraph().createRun().setText("第一条 合同标的");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("甲方");
            table.getRow(0).getCell(1).setText("乙方");
            table.getRow(1).getCell(0).setText("付款方");
            table.getRow(1).getCell(1).setText("收款方");
            try (OutputStream output = Files.newOutputStream(file))
            {
                document.write(output);
            }
        }

        String text = extractor.extract(file, "docx");

        assertTrue(text.contains("第一条 合同标的"));
        assertTrue(text.contains("甲方\t乙方"), "表格单元格应以制表符拼接: " + text);
        assertTrue(text.contains("付款方\t收款方"), "表格行应完整提取: " + text);
        assertTrue(text.indexOf("第一条 合同标的") < text.indexOf("甲方"), "应按文档顺序提取段落与表格");
    }

    @Test
    void extractsPdfText() throws Exception
    {
        Path file = tempDir.resolve("contract.pdf");
        try (PDDocument document = new PDDocument())
        {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page))
            {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("Payment clause and liability");
                content.endText();
            }
            document.save(file.toFile());
        }

        String text = extractor.extract(file, "pdf");

        assertTrue(text.contains("Payment clause"), "应提取 PDF 文本: " + text);
    }

    @Test
    void fallsBackToGb18030WhenUtf8DecodeFails() throws Exception
    {
        String expected = "合同审查测试，违约责任应当明确。";
        Path file = tempDir.resolve("contract.txt");
        Files.write(file, expected.getBytes(GB18030));

        String text = extractor.extract(file, "txt");

        assertEquals(expected, text);
    }

    @Test
    void stripsUtf8Bom() throws Exception
    {
        Path file = tempDir.resolve("contract.md");
        Files.write(file, ("\uFEFF" + "合同正文").getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(file, "md");

        assertEquals("合同正文", text);
        assertFalse(text.startsWith("\uFEFF"));
    }

    @Test
    void throws422WhenExtractedTextIsBlank() throws Exception
    {
        Path file = tempDir.resolve("empty.txt");
        Files.write(file, "  \n\n\t \n".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class, () -> extractor.extract(file, "txt"));

        assertEquals(422, exception.getCode());
        assertEquals("未能从文件中提取到文字，可能是扫描件或加密文件", exception.getMessage());
    }

    @Test
    void collapsesRepeatedBlankLines() throws Exception
    {
        Path file = tempDir.resolve("blank-lines.txt");
        Files.write(file, "第一段\n\n\n\n\n第二段".getBytes(StandardCharsets.UTF_8));

        String text = extractor.extract(file, "txt");

        assertEquals("第一段\n\n第二段", text);
    }

    @Test
    void rejectsUnsupportedExtension()
    {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> extractor.extract(tempDir.resolve("a.exe"), "exe"));

        assertEquals(422, exception.getCode());
        assertEquals("不支持的文件类型", exception.getMessage());
    }
}
