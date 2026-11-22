package com.ruoyi.lawyer.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/**
 * 合同文本抽取器
 *
 * <p>支持 docx / pdf / txt / md；抽取结果规范化连续空行，空文本统一报 422。</p>
 *
 * @author ruoyi
 */
@Component
public class ContractTextExtractor
{
    private static final Logger log = LoggerFactory.getLogger(ContractTextExtractor.class);

    /** 空文本 / 扫描件 / 加密文件的统一提示 */
    private static final String EMPTY_TEXT_MESSAGE = "未能从文件中提取到文字，可能是扫描件或加密文件";

    /** GB18030 回退字符集 */
    private static final Charset GB18030 = Charset.forName("GB18030");

    /**
     * 从文件中抽取纯文本
     *
     * @param file 物理文件路径
     * @param ext 小写不带点扩展名：docx/pdf/txt/md
     * @return 规范化后的文本（已 trim，非空）
     */
    public String extract(Path file, String ext)
    {
        if (file == null || ext == null || ext.isBlank())
        {
            throw new ServiceException("不支持的文件类型", 422);
        }
        String normalizedExt = ext.trim().toLowerCase();
        String text;
        try
        {
            switch (normalizedExt)
            {
                case "docx":
                    text = extractDocx(file);
                    break;
                case "pdf":
                    text = extractPdf(file);
                    break;
                case "txt":
                case "md":
                    text = extractPlainText(file);
                    break;
                default:
                    throw new ServiceException("不支持的文件类型", 422);
            }
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.warn("合同文本抽取失败, ext={}, 原因={}", normalizedExt, e.getMessage());
            throw new ServiceException(EMPTY_TEXT_MESSAGE, 422);
        }
        String result = normalize(text);
        if (result.isEmpty())
        {
            throw new ServiceException(EMPTY_TEXT_MESSAGE, 422);
        }
        return result;
    }

    /**
     * docx：按文档顺序提取段落与表格（单元格以 \t 拼接，行以 \n）
     */
    private String extractDocx(Path file) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        try (InputStream input = Files.newInputStream(file);
                XWPFDocument document = new XWPFDocument(input))
        {
            List<IBodyElement> elements = document.getBodyElements();
            for (IBodyElement element : elements)
            {
                if (element instanceof XWPFParagraph paragraph)
                {
                    String line = paragraph.getText();
                    if (line != null)
                    {
                        builder.append(line);
                    }
                    builder.append('\n');
                }
                else if (element instanceof XWPFTable table)
                {
                    for (XWPFTableRow row : table.getRows())
                    {
                        List<XWPFTableCell> cells = row.getTableCells();
                        for (int i = 0; i < cells.size(); i++)
                        {
                            String cellText = cells.get(i).getText();
                            if (cellText != null)
                            {
                                builder.append(cellText);
                            }
                            if (i < cells.size() - 1)
                            {
                                builder.append('\t');
                            }
                        }
                        builder.append('\n');
                    }
                }
            }
        }
        return builder.toString();
    }

    /**
     * pdf：PDFBox 3 Loader + PDFTextStripper（按位置排序）
     */
    private String extractPdf(Path file) throws IOException
    {
        try (PDDocument document = Loader.loadPDF(file.toFile()))
        {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    /**
     * txt/md：优先 UTF-8（去 BOM），解码失败回退 GB18030
     */
    private String extractPlainText(Path file) throws IOException
    {
        byte[] bytes = Files.readAllBytes(file);
        String text;
        try
        {
            text = decodeStrict(bytes, StandardCharsets.UTF_8);
        }
        catch (CharacterCodingException e)
        {
            text = decodeStrict(bytes, GB18030);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF')
        {
            text = text.substring(1);
        }
        return text;
    }

    /**
     * 严格解码：遇到非法字节立即失败，便于触发字符集回退
     */
    private String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException
    {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    /**
     * 统一换行并合并 3 个及以上连续空行，最后 trim
     */
    private String normalize(String text)
    {
        if (text == null)
        {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized.trim();
    }
}
