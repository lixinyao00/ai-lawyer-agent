package com.ruoyi.web.controller.lawyer;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.domain.LawPrivateFile;
import com.ruoyi.lawyer.service.ILawPrivateFileService;

/**
 * 私有文件 控制器
 *
 * <p>文件存储在 lawyer.storage.root，下载必须校验归属，不暴露 storage_path。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/files")
public class LawFileController extends BaseController
{
    private static final String NOT_FOUND_MESSAGE = "文件不存在或无权访问";

    @Autowired
    private ILawPrivateFileService privateFileService;

    /**
     * 上传私有文件
     */
    @PreAuthorize("@ss.hasPermi('lawyer:file:access')")
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam(value = "file", required = false) MultipartFile file)
    {
        return success(privateFileService.uploadFile(getUserId(), file));
    }

    /**
     * 下载本人私有文件
     */
    @PreAuthorize("@ss.hasPermi('lawyer:file:access')")
    @GetMapping("/{id:\\d+}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id)
    {
        LawPrivateFile file = privateFileService.getOwnedFile(id, getUserId());
        Path path = privateFileService.resolvePhysicalPath(file);
        if (!Files.isRegularFile(path))
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }

        Resource resource = new FileSystemResource(path);
        String encodedName = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType(file.getFileExt()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName);
        try
        {
            builder.contentLength(Files.size(path));
        }
        catch (IOException e)
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        return builder.body(resource);
    }

    private MediaType mediaType(String ext)
    {
        if ("docx".equalsIgnoreCase(ext))
        {
            return MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        }
        if ("pdf".equalsIgnoreCase(ext))
        {
            return MediaType.APPLICATION_PDF;
        }
        if ("txt".equalsIgnoreCase(ext))
        {
            return MediaType.TEXT_PLAIN;
        }
        if ("md".equalsIgnoreCase(ext))
        {
            return MediaType.parseMediaType("text/markdown");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
