package com.ruoyi.web.controller.lawyer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lawyer.dto.ContractReviewQuery;
import com.ruoyi.lawyer.service.ILawContractReviewService;

/**
 * 合同风险审查 控制器
 *
 * <p>涉及合同正文的接口不添加 @Log，避免正文进入操作日志。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/contracts/reviews")
public class LawContractReviewController extends BaseController
{
    @Autowired
    private ILawContractReviewService contractReviewService;

    /**
     * 发起合同审查（file / fileId / contractText 三选一，优先级 file > fileId > contractText）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:contract:add')")
    @PostMapping
    public AjaxResult create(@RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "contractText", required = false) String contractText,
            @RequestParam(value = "stance", required = false) String stance,
            @RequestParam(value = "title", required = false) String title)
    {
        return success(contractReviewService.createReview(getUserId(), file, fileId, contractText, stance, title));
    }

    /**
     * 分页查询本人审查记录
     */
    @PreAuthorize("@ss.hasPermi('lawyer:contract:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String status,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        ContractReviewQuery query = new ContractReviewQuery();
        query.setStatus(status);
        query.setRiskLevel(riskLevel);
        query.setTitle(title);
        return getDataTable(contractReviewService.selectReviewList(getUserId(), query, pageNum, pageSize));
    }

    /**
     * 查询本人审查详情
     */
    @PreAuthorize("@ss.hasPermi('lawyer:contract:query')")
    @GetMapping("/{id:\\d+}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(contractReviewService.selectReviewById(id, getUserId()));
    }

    /**
     * 删除审查记录；关联文件不再被引用时同时删除文件记录与物理文件
     */
    @PreAuthorize("@ss.hasPermi('lawyer:contract:remove')")
    @Log(title = "合同审查", businessType = BusinessType.DELETE, isSaveRequestData = false,
            isSaveResponseData = false)
    @DeleteMapping("/{id:\\d+}")
    public AjaxResult remove(@PathVariable Long id)
    {
        contractReviewService.deleteReview(id, getUserId());
        return success();
    }
}
