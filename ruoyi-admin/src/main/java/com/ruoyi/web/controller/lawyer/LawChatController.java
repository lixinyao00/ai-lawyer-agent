package com.ruoyi.web.controller.lawyer;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lawyer.dto.SendMessageRequest;
import com.ruoyi.lawyer.service.ILawChatService;
import com.ruoyi.lawyer.vo.ChatMessageVO;

/**
 * 咨询聊天 控制器
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/lawyer/sessions/{sessionId}/messages")
public class LawChatController extends BaseController
{
    /** 历史消息默认每页条数 */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /** 历史消息每页上限 */
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private ILawChatService chatService;

    /**
     * 查询会话的历史消息，按时间正序返回
     */
    @PreAuthorize("@ss.hasPermi('lawyer:chat:query')")
    @GetMapping
    public TableDataInfo list(@PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "50") Integer pageSize)
    {
        int size = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        List<ChatMessageVO> messages = chatService.selectMessageList(sessionId, getUserId(), num, size);
        return getDataTable(messages);
    }

    /**
     * 发送问题并生成回答（同步，兼容 + 降级）
     */
    @PreAuthorize("@ss.hasPermi('lawyer:chat:send')")
    @PostMapping
    public AjaxResult send(@PathVariable Long sessionId,
            @Validated @RequestBody SendMessageRequest request)
    {
        return success(chatService.sendMessage(sessionId, getUserId(), request));
    }

    /**
     * 流式发送问题并生成回答
     *
     * <p>流开始前的参数校验、鉴权、归属失败、409、422 等异常仍返回 JSON（HTTP 200）；
     * 只有成功进入流式流程后才设置 text/event-stream 响应头。</p>
     */
    @PreAuthorize("@ss.hasPermi('lawyer:chat:send')")
    @PostMapping("/stream")
    public SseEmitter stream(@PathVariable Long sessionId,
            @Validated @RequestBody SendMessageRequest request,
            HttpServletResponse response)
    {
        SseEmitter emitter = chatService.streamMessage(sessionId, getUserId(), request);
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return emitter;
    }
}
