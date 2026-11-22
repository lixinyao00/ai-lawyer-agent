package com.ruoyi.web.controller.lawyer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lawyer.dto.AdminSessionQuery;
import com.ruoyi.lawyer.service.ILawAdminSessionService;

/**
 * 后台会话审计 控制器
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/admin/sessions")
public class LawAdminSessionController extends BaseController
{
    /** 会话列表默认每页条数 */
    private static final int DEFAULT_SESSION_PAGE_SIZE = 10;

    /** 消息审计默认每页条数 */
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 50;

    /** 消息审计每页上限 */
    private static final int MAX_MESSAGE_PAGE_SIZE = 100;

    @Autowired
    private ILawAdminSessionService adminSessionService;

    /**
     * 后台会话分页查询
     */
    @PreAuthorize("@ss.hasPermi('lawyer:manage:list')")
    @GetMapping
    public TableDataInfo list(AdminSessionQuery query,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_SESSION_PAGE_SIZE : pageSize;
        PageHelper.startPage(num, size);
        return getDataTable(adminSessionService.selectSessionList(query));
    }

    /**
     * 会话消息审计，按消息 id 正序返回
     */
    @PreAuthorize("@ss.hasPermi('lawyer:manage:query')")
    @GetMapping("/{id}/messages")
    public TableDataInfo messages(@PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize)
    {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? DEFAULT_MESSAGE_PAGE_SIZE
                : Math.min(pageSize, MAX_MESSAGE_PAGE_SIZE);
        return getDataTable(adminSessionService.selectMessageList(id, num, size));
    }

    /**
     * 后台删除会话
     */
    @PreAuthorize("@ss.hasPermi('lawyer:manage:remove')")
    @Log(title = "会话管理", businessType = BusinessType.DELETE, isSaveRequestData = false, isSaveResponseData = false)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        adminSessionService.deleteSession(id);
        return success();
    }
}
