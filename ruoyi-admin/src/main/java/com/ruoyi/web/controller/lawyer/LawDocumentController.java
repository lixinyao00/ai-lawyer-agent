package com.ruoyi.web.controller.lawyer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lawyer.dto.CreateDraftRequest;
import com.ruoyi.lawyer.dto.DraftQuery;
import com.ruoyi.lawyer.dto.UpdateDraftRequest;
import com.ruoyi.lawyer.service.ILawDocumentService;
import com.ruoyi.lawyer.vo.DocumentDraftVO;
import com.ruoyi.lawyer.vo.DocumentTemplateVO;

/**
 * 法律文书草稿 控制器
 *
 * <p>涉及文书正文的接口不添加 @Log，避免正文进入操作日志。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/documents")
public class LawDocumentController extends BaseController
{
    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private ILawDocumentService documentService;

    /**
     * 查询全部文书模板
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:list')")
    @GetMapping("/templates")
    public AjaxResult templates()
    {
        return success(documentService.listTemplates());
    }

    /**
     * 创建文书草稿（同步生成，失败也落库）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:add')")
    @PostMapping("/drafts")
    public AjaxResult create(@RequestBody CreateDraftRequest request)
    {
        return success(documentService.createDraft(getUserId(), request));
    }

    /**
     * 编辑文书草稿，保存后置为 success
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:edit')")
    @PutMapping("/drafts/{id:\\d+}")
    public AjaxResult update(@PathVariable Long id, @RequestBody UpdateDraftRequest request)
    {
        return success(documentService.updateDraft(id, getUserId(), request));
    }

    /**
     * 分页查询本人草稿
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:list')")
    @GetMapping("/drafts")
    public TableDataInfo list(@RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String title,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        DraftQuery query = new DraftQuery();
        query.setTemplateCode(templateCode);
        query.setTitle(title);
        return getDataTable(documentService.selectDraftList(getUserId(), query, pageNum, pageSize));
    }

    /**
     * 查询本人草稿详情
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:list')")
    @GetMapping("/drafts/{id:\\d+}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(documentService.selectDraftById(id, getUserId()));
    }

    /**
     * 删除本人草稿
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:remove')")
    @DeleteMapping("/drafts/{id:\\d+}")
    public AjaxResult remove(@PathVariable Long id)
    {
        documentService.deleteDraft(id, getUserId());
        return success();
    }

    /**
     * 导出 DOCX（仅 success 且正文非空可导出）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:document:export')")
    @GetMapping("/drafts/{id:\\d+}/export-docx")
    public ResponseEntity<byte[]> exportDocx(@PathVariable Long id)
    {
        DocumentDraftVO draft = documentService.selectExportableDraft(id, getUserId());
        byte[] data = documentService.exportDocx(draft);
        String title = draft.getTitle() == null || draft.getTitle().isBlank()
                ? "法律文书" : draft.getTitle();
        String encodedName = URLEncoder.encode(title, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DOCX_CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName + ".docx")
                .contentLength(data.length)
                .body(data);
    }
}
