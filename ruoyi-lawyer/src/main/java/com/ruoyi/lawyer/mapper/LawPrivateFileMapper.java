package com.ruoyi.lawyer.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.lawyer.domain.LawPrivateFile;

/**
 * 用户私有文件 数据层
 *
 * @author ruoyi
 */
public interface LawPrivateFileMapper
{
    /**
     * 按主键与归属用户查询
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 私有文件，不存在或非本人返回 null
     */
    LawPrivateFile selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 按用户与 SHA-256 查询（用于去重复用）
     *
     * @param userId 所有者用户ID
     * @param sha256 十六进制小写摘要
     * @return 私有文件
     */
    LawPrivateFile selectByUserIdAndSha(@Param("userId") Long userId, @Param("sha256") String sha256);

    /**
     * 新增私有文件记录
     *
     * @param file 私有文件
     * @return 影响行数
     */
    int insertFile(LawPrivateFile file);

    /**
     * 按主键删除记录
     *
     * @param id 主键
     * @param userId 所有者用户ID
     * @return 影响行数
     */
    int deleteFileByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
