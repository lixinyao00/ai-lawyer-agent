package com.ruoyi.lawyer.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.config.LawyerStorageProperties;
import com.ruoyi.lawyer.domain.LawPrivateFile;
import com.ruoyi.lawyer.mapper.LawPrivateFileMapper;
import com.ruoyi.lawyer.service.ILawPrivateFileService;
import com.ruoyi.lawyer.vo.PrivateFileVO;

/**
 * 用户私有文件 业务层实现
 *
 * <p>存储根为 lawyer.storage.root（默认 ./data/lawyer-files），
 * 绝不写入 ruoyi.profile，也不经 /profile/** 暴露。</p>
 *
 * @author ruoyi
 */
@Service
public class LawPrivateFileServiceImpl implements ILawPrivateFileService
{
    private static final Logger log = LoggerFactory.getLogger(LawPrivateFileServiceImpl.class);

    /** 固定不接受的历史格式 */
    private static final String DOC_EXT = "doc";

    /** 原始文件名最大长度 */
    private static final int MAX_NAME_LENGTH = 255;

    /** 物理文件缺失时的统一提示 */
    private static final String NOT_FOUND_MESSAGE = "文件不存在或无权访问";

    /** 存储月份目录格式 */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private LawyerStorageProperties storageProperties;

    @Autowired
    private LawPrivateFileMapper fileMapper;

    /**
     * 启动时创建存储根目录
     */
    @PostConstruct
    public void init()
    {
        try
        {
            Files.createDirectories(storageRoot());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("私有文件存储目录创建失败: " + storageRoot(), e);
        }
    }

    @Override
    public PrivateFileVO uploadFile(Long userId, MultipartFile file)
    {
        if (userId == null)
        {
            throw new ServiceException("用户未登录", 401);
        }
        if (file == null || file.isEmpty() || file.getSize() <= 0)
        {
            throw new ServiceException("请选择要上传的文件", 422);
        }
        long maxBytes = storageProperties.getMaxSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes)
        {
            throw new ServiceException("文件大小不能超过" + storageProperties.getMaxSizeMb() + "MB", 422);
        }

        String originalName = sanitizeName(file.getOriginalFilename());
        String ext = resolveExtension(originalName);
        if (DOC_EXT.equals(ext))
        {
            throw new ServiceException("暂不支持 .doc，请另存为 .docx", 422);
        }
        if (!allowedExtensions().contains(ext))
        {
            throw new ServiceException("不支持的文件类型，仅支持 " + String.join("、", allowedExtensions()), 422);
        }

        byte[] bytes = readBytes(file);
        if (bytes.length > maxBytes)
        {
            throw new ServiceException("文件大小不能超过" + storageProperties.getMaxSizeMb() + "MB", 422);
        }
        String sha256 = sha256(bytes);

        // 同一用户同一内容且物理文件仍在：直接复用，不重复落盘
        LawPrivateFile existing = fileMapper.selectByUserIdAndSha(userId, sha256);
        if (existing != null && physicalExistsQuietly(existing))
        {
            return toVO(existing);
        }

        Path root = ensureRoot();
        String storagePath = userId + "/" + LocalDateTime.now().format(MONTH_FORMATTER) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(storagePath).normalize();
        if (!target.startsWith(root))
        {
            throw new ServiceException("文件存储路径非法", 422);
        }
        try
        {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (IOException e)
        {
            log.warn("私有文件落盘失败, userId={}, 原因={}", userId, e.getMessage());
            throw new ServiceException("文件保存失败，请稍后重试");
        }

        LawPrivateFile record = new LawPrivateFile();
        record.setUserId(userId);
        record.setOriginalName(originalName);
        record.setStoragePath(storagePath);
        record.setFileExt(ext);
        record.setFileSize((long) bytes.length);
        record.setSha256(sha256);
        record.setCreateTime(new Date());
        try
        {
            fileMapper.insertFile(record);
        }
        catch (RuntimeException e)
        {
            deletePhysicalQuietly(target);
            throw e;
        }
        return toVO(record);
    }

    @Override
    public PrivateFileVO selectFileById(Long id, Long userId)
    {
        return toVO(getOwnedFile(id, userId));
    }

    @Override
    public LawPrivateFile getOwnedFile(Long id, Long userId)
    {
        if (id == null || userId == null)
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        LawPrivateFile file = fileMapper.selectByIdAndUserId(id, userId);
        if (file == null)
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        return file;
    }

    @Override
    public Path resolvePhysicalPath(LawPrivateFile file)
    {
        if (file == null || file.getStoragePath() == null || file.getStoragePath().isBlank())
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        Path root = storageRoot();
        Path target;
        try
        {
            target = root.resolve(file.getStoragePath()).normalize();
        }
        catch (RuntimeException e)
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        if (!target.startsWith(root))
        {
            throw new ServiceException(NOT_FOUND_MESSAGE);
        }
        return target;
    }

    @Override
    public void deleteFile(Long id, Long userId)
    {
        if (id == null || userId == null)
        {
            return;
        }
        LawPrivateFile file = fileMapper.selectByIdAndUserId(id, userId);
        if (file == null)
        {
            return;
        }
        fileMapper.deleteFileByIdAndUserId(id, userId);
        try
        {
            deletePhysicalQuietly(resolvePhysicalPath(file));
        }
        catch (ServiceException e)
        {
            log.warn("私有文件物理路径非法，跳过删除, id={}", id);
        }
    }

    /**
     * 存储根：相对路径按进程工作目录解析为绝对路径并 normalize
     */
    private Path storageRoot()
    {
        return Paths.get(storageProperties.getRoot()).toAbsolutePath().normalize();
    }

    private Path ensureRoot()
    {
        Path root = storageRoot();
        try
        {
            Files.createDirectories(root);
        }
        catch (IOException e)
        {
            throw new ServiceException("文件存储目录不可用，请联系管理员");
        }
        return root;
    }

    private Set<String> allowedExtensions()
    {
        Set<String> result = new LinkedHashSet<>();
        String configured = storageProperties.getAllowedExt();
        if (configured != null)
        {
            Arrays.stream(configured.split(","))
                    .map(item -> item.trim().toLowerCase(Locale.ROOT))
                    .filter(item -> !item.isEmpty())
                    .forEach(result::add);
        }
        if (result.isEmpty())
        {
            result.addAll(Arrays.asList("docx", "pdf", "txt", "md"));
        }
        return result;
    }

    /**
     * 去除路径分隔符并截断 255
     */
    private String sanitizeName(String originalName)
    {
        String name = originalName == null ? "" : originalName;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0)
        {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty())
        {
            name = "未命名文件";
        }
        if (name.length() > MAX_NAME_LENGTH)
        {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name;
    }

    private String resolveExtension(String fileName)
    {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1)
        {
            throw new ServiceException("不支持的文件类型", 422);
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readBytes(MultipartFile file)
    {
        try (InputStream input = file.getInputStream())
        {
            return input.readAllBytes();
        }
        catch (IOException e)
        {
            log.warn("读取上传文件失败: {}", e.getMessage());
            throw new ServiceException("文件读取失败，请重试", 422);
        }
    }

    private String sha256(byte[] bytes)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    private boolean physicalExistsQuietly(LawPrivateFile file)
    {
        try
        {
            return Files.isRegularFile(resolvePhysicalPath(file));
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    private void deletePhysicalQuietly(Path path)
    {
        if (path == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException e)
        {
            log.warn("删除私有文件物理文件失败: {}", e.getMessage());
        }
    }

    private PrivateFileVO toVO(LawPrivateFile file)
    {
        PrivateFileVO vo = new PrivateFileVO();
        vo.setId(file.getId());
        vo.setOriginalName(file.getOriginalName());
        vo.setFileExt(file.getFileExt());
        vo.setFileSize(file.getFileSize());
        vo.setSha256(file.getSha256());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }
}
