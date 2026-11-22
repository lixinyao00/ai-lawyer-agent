package com.ruoyi.web.controller.lawyer;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lawyer.domain.LawChatSession;
import com.ruoyi.lawyer.dto.CreateSessionRequest;
import com.ruoyi.lawyer.service.ILawSessionService;

/**
 * 咨询会话 控制器
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/sessions")
public class LawSessionController extends BaseController
{
    @Autowired
    private ILawSessionService sessionService;

    /**
     * 新建会话
     */
    @PreAuthorize("@ss.hasPermi('lawyer:session:add')")
    @PostMapping
    public AjaxResult add(@Validated @RequestBody(required = false) CreateSessionRequest request)
    {
        String title = request == null ? null : request.getTitle();
        return success(sessionService.createSession(getUserId(), title));
    }

    /**
     * 查询当前用户的会话列表
     */
    @PreAuthorize("@ss.hasPermi('lawyer:session:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize)
    {
        PageHelper.startPage(pageNum, pageSize);
        List<LawChatSession> list = sessionService.selectSessionList(getUserId());
        return getDataTable(list);
    }

    /**
     * 删除会话
     */
    @PreAuthorize("@ss.hasPermi('lawyer:session:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        sessionService.deleteSession(id, getUserId());
        return success();
    }
}
