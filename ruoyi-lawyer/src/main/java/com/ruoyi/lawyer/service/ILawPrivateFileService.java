package com.ruoyi.lawyer.service;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.lawyer.domain.LawPrivateFile;
import com.ruoyi.lawyer.vo.PrivateFileVO;

/**
 * 用户私有文件 业务层
 *
 * @author ruoyi
 */
public interface ILawPrivateFileService
{
    /**
     * 上传私有文件（落盘 + 入库，同用户同 SHA-256 复用已有记录）
     *
     * @param userId 所有者用户ID
     * @param file 上传文件
     * @return 私有文件视图（不含 storage_path）
     */
    PrivateFileVO uploadFile(Long userId, MultipartFile file);

    /**
     * 查询本人私有文件视图
     *
     * @param id 文件ID
     * @param userId 所有者用户ID
     * @return 私有文件视图
     */
    PrivateFileVO selectFileById(Long id, Long userId);

    /**
     * 查询本人私有文件实体（含 storage_path，仅服务端内部使用）
     *
     * @param id 文件ID
     * @param userId 所有者用户ID
     * @return 私有文件实体
     */
    LawPrivateFile getOwnedFile(Long id, Long userId);

    /**
     * 解析物理文件路径并做路径穿越校验
     *
     * @param file 私有文件实体
     * @return 归一化后的绝对路径
     */
    Path resolvePhysicalPath(LawPrivateFile file);

    /**
     * 删除文件记录与磁盘物理文件（调用方负责引用检查与归属校验）
     *
     * @param id 文件ID
     * @param userId 所有者用户ID
     */
    void deleteFile(Long id, Long userId);
}
