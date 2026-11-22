package com.ruoyi.lawyer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.lawyer.config.LawyerStorageProperties;
import com.ruoyi.lawyer.domain.LawPrivateFile;
import com.ruoyi.lawyer.mapper.LawPrivateFileMapper;
import com.ruoyi.lawyer.vo.PrivateFileVO;

/**
 * LawPrivateFileServiceImpl 单元测试（Mockito + 临时目录，不启动 Spring 上下文）
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LawPrivateFileServiceTest
{
    private static final Long USER_ID = 7L;

    private static final Long OTHER_USER_ID = 8L;

    @Mock
    private LawPrivateFileMapper fileMapper;

    @InjectMocks
    private LawPrivateFileServiceImpl service;

    @TempDir
    Path tempDir;

    private Path storageRoot;

    @BeforeEach
    void setUp()
    {
        storageRoot = tempDir.resolve("lawyer-files");
        LawyerStorageProperties properties = new LawyerStorageProperties();
        properties.setRoot(storageRoot.toString());
        properties.setAllowedExt("docx,pdf,txt,md");
        properties.setMaxSizeMb(10L);
        ReflectionTestUtils.setField(service, "storageProperties", properties);
    }

    @Test
    void uploadsFileAndWritesOutsideProfileRoot() throws Exception
    {
        byte[] content = "合同正文内容".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "采购合同.txt", "text/plain", content);

        PrivateFileVO vo = service.uploadFile(USER_ID, file);

        ArgumentCaptor<LawPrivateFile> captor = ArgumentCaptor.forClass(LawPrivateFile.class);
        verify(fileMapper).insertFile(captor.capture());
        LawPrivateFile saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals("采购合同.txt", saved.getOriginalName());
        assertEquals("txt", saved.getFileExt());
        assertEquals((long) content.length, saved.getFileSize());
        assertEquals(sha256(content), saved.getSha256());
        assertTrue(saved.getStoragePath().startsWith(USER_ID + "/"), "落盘路径为 {userId}/{yyyyMM}/{uuid}.ext");
        assertTrue(saved.getStoragePath().endsWith(".txt"));
        assertTrue(Files.isRegularFile(storageRoot.resolve(saved.getStoragePath())));
        assertEquals(saved.getId(), vo.getId());
        assertEquals("采购合同.txt", vo.getOriginalName());
    }

    @Test
    void reusesExistingRecordWhenSameUserAndShaAndPhysicalFileExists() throws Exception
    {
        byte[] content = "重复内容".getBytes(StandardCharsets.UTF_8);
        String sha = sha256(content);
        String storagePath = USER_ID + "/202611/existing.txt";
        Path physical = storageRoot.resolve(storagePath);
        Files.createDirectories(physical.getParent());
        Files.write(physical, content);

        LawPrivateFile existing = new LawPrivateFile();
        existing.setId(99L);
        existing.setUserId(USER_ID);
        existing.setOriginalName("既有合同.txt");
        existing.setStoragePath(storagePath);
        existing.setFileExt("txt");
        existing.setFileSize((long) content.length);
        existing.setSha256(sha);
        existing.setCreateTime(new Date());
        when(fileMapper.selectByUserIdAndSha(USER_ID, sha)).thenReturn(existing);

        PrivateFileVO vo = service.uploadFile(USER_ID,
                new MockMultipartFile("file", "再次上传.txt", "text/plain", content));

        assertEquals(99L, vo.getId());
        assertEquals("既有合同.txt", vo.getOriginalName());
        verify(fileMapper, never()).insertFile(any());
    }

    @Test
    void rejectsDocExtensionWithExplicitMessage()
    {
        MockMultipartFile file = new MockMultipartFile("file", "旧合同.doc", "application/msword",
                "abc".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.uploadFile(USER_ID, file));

        assertEquals(422, exception.getCode());
        assertEquals("暂不支持 .doc，请另存为 .docx", exception.getMessage());
        verify(fileMapper, never()).insertFile(any());
    }

    @Test
    void rejectsExtensionOutsideWhitelist()
    {
        MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/octet-stream",
                "abc".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.uploadFile(USER_ID, file));

        assertEquals(422, exception.getCode());
        assertTrue(exception.getMessage().contains("不支持的文件类型"));
        verify(fileMapper, never()).insertFile(any());
    }

    @Test
    void rejectsSizeOverConfiguredLimit()
    {
        LawyerStorageProperties properties = new LawyerStorageProperties();
        properties.setRoot(storageRoot.toString());
        properties.setMaxSizeMb(0L);
        ReflectionTestUtils.setField(service, "storageProperties", properties);

        MockMultipartFile file = new MockMultipartFile("file", "big.txt", "text/plain",
                "abc".getBytes(StandardCharsets.UTF_8));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.uploadFile(USER_ID, file));

        assertEquals(422, exception.getCode());
        assertTrue(exception.getMessage().contains("文件大小"));
    }

    @Test
    void rejectsPathTraversalStoragePath()
    {
        LawPrivateFile evil = new LawPrivateFile();
        evil.setId(1L);
        evil.setUserId(USER_ID);
        evil.setStoragePath("../../etc/passwd");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.resolvePhysicalPath(evil));

        assertEquals("文件不存在或无权访问", exception.getMessage());
    }

    @Test
    void rejectsAbsoluteStoragePathOutsideRoot()
    {
        LawPrivateFile evil = new LawPrivateFile();
        evil.setStoragePath("/etc/passwd");

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.resolvePhysicalPath(evil));

        assertEquals("文件不存在或无权访问", exception.getMessage());
    }

    @Test
    void rejectsOtherUsersFileOnDownload()
    {
        when(fileMapper.selectByIdAndUserId(5L, OTHER_USER_ID)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.getOwnedFile(5L, OTHER_USER_ID));

        assertEquals("文件不存在或无权访问", exception.getMessage());
    }

    @Test
    void resolvesRelativePathToAbsoluteUnderRoot()
    {
        LawPrivateFile file = new LawPrivateFile();
        file.setStoragePath(USER_ID + "/202611/abc.txt");

        Path resolved = service.resolvePhysicalPath(file);

        assertTrue(resolved.isAbsolute());
        assertTrue(resolved.normalize().startsWith(storageRoot.toAbsolutePath().normalize()));
        assertEquals(storageRoot.resolve(USER_ID + "/202611/abc.txt").normalize(), resolved);
        assertFalse(resolved.toString().contains(".."));
    }

    private String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
