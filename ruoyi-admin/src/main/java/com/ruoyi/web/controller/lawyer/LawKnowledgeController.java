package com.ruoyi.web.controller.lawyer;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.lawyer.dto.KnowledgeChunkRequest;
import com.ruoyi.lawyer.dto.KnowledgeDocRequest;
import com.ruoyi.lawyer.dto.KnowledgeRechunkRequest;
import com.ruoyi.lawyer.service.ILawKnowledgeService;

/**
 * 法律知识库 控制器
 *
 * <p>路由顺序：/search、/import 为固定路径，/{id} 使用数字正则约束，避免被误匹配。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/knowledge/docs")
public class LawKnowledgeController extends BaseController
{
    /** 文档列表默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 检索调试默认 Top-K */
    private static final int DEFAULT_TOP_K = 4;

    @Autowired
    private ILawKnowledgeService knowledgeService;

    /**
     * 分页查询法规文档
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        return getDataTable(knowledgeService.selectDocList(title, category, status, pageNum, pageSize));
    }

    /**
     * 检索调试（与聊天共用检索器）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:query')")
    @GetMapping("/search")
    public AjaxResult search(@RequestParam("q") String q,
            @RequestParam(required = false) Integer topK)
    {
        int limit = topK == null || topK < 1 ? DEFAULT_TOP_K : Math.min(topK, 10);
        return success(knowledgeService.search(q, limit));
    }

    /**
     * 导入法规文件并自动切分
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:import')")
    @Log(title = "知识库法规导入", businessType = BusinessType.IMPORT, isSaveRequestData = false,
            isSaveResponseData = false)
    @PostMapping("/import")
    public AjaxResult importDoc(@RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("category") String category,
            @RequestParam(value = "docNumber", required = false) String docNumber,
            @RequestParam(value = "effectiveDate", required = false) String effectiveDate,
            @RequestParam(value = "remark", required = false) String remark)
    {
        KnowledgeDocRequest request = new KnowledgeDocRequest();
        request.setTitle(title);
        request.setCategory(category);
        request.setDocNumber(docNumber);
        request.setRemark(remark);
        if (StringUtils.isNotBlank(effectiveDate))
        {
            request.setEffectiveDate(DateUtils.parseDate(effectiveDate));
        }
        Map<String, Object> result = knowledgeService.importDoc(file, request, getUsername());
        return success(result);
    }

    /**
     * 查询文档详情
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:query')")
    @GetMapping("/{id:\\d+}")
    public AjaxResult getInfo(@PathVariable Long id)
    {
        return success(knowledgeService.selectDocById(id));
    }

    /**
     * 新增文档，content 可选（传入则自动切分入库）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:add')")
    @Log(title = "知识库法规", businessType = BusinessType.INSERT, isSaveRequestData = false,
            isSaveResponseData = false)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody KnowledgeDocRequest request)
    {
        return success(knowledgeService.addDoc(request, getUsername()));
    }

    /**
     * 修改文档元数据
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:edit')")
    @Log(title = "知识库法规", businessType = BusinessType.UPDATE, isSaveRequestData = false,
            isSaveResponseData = false)
    @PutMapping("/{id:\\d+}")
    public AjaxResult edit(@PathVariable Long id, @Validated @RequestBody KnowledgeDocRequest request)
    {
        knowledgeService.updateDoc(id, request);
        return success();
    }

    /**
     * 删除旧切片后重新切分
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:edit')")
    @Log(title = "知识库法规重切", businessType = BusinessType.UPDATE, isSaveRequestData = false,
            isSaveResponseData = false)
    @PostMapping("/{id:\\d+}/rechunk")
    public AjaxResult rechunk(@PathVariable Long id, @Validated @RequestBody KnowledgeRechunkRequest request)
    {
        return success(knowledgeService.rechunkDoc(id, request));
    }

    /**
     * 批量删除文档，级联删除切片
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:remove')")
    @Log(title = "知识库法规", businessType = BusinessType.DELETE, isSaveRequestData = false,
            isSaveResponseData = false)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        knowledgeService.deleteDocByIds(ids);
        return success();
    }

    /**
     * 分页查询某文档的切片
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:query')")
    @GetMapping("/{id:\\d+}/chunks")
    public TableDataInfo chunks(@PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize)
    {
        return getDataTable(knowledgeService.selectChunkList(id, keyword, pageNum, pageSize));
    }

    /**
     * 修改切片
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:edit')")
    @Log(title = "知识库切片", businessType = BusinessType.UPDATE, isSaveRequestData = false,
            isSaveResponseData = false)
    @PutMapping("/{docId:\\d+}/chunks/{chunkId:\\d+}")
    public AjaxResult editChunk(@PathVariable Long docId, @PathVariable Long chunkId,
            @Validated @RequestBody KnowledgeChunkRequest request)
    {
        knowledgeService.updateChunk(docId, chunkId, request);
        return success();
    }

    /**
     * 删除切片并回写 chunk_count
     */
    @PreAuthorize("@ss.hasPermi('lawyer:knowledge:remove')")
    @Log(title = "知识库切片", businessType = BusinessType.DELETE, isSaveRequestData = false,
            isSaveResponseData = false)
    @DeleteMapping("/{docId:\\d+}/chunks/{chunkId:\\d+}")
    public AjaxResult removeChunk(@PathVariable Long docId, @PathVariable Long chunkId)
    {
        knowledgeService.deleteChunk(docId, chunkId);
        return success();
    }
}
