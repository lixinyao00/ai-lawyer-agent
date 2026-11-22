package com.ruoyi.lawyer.support;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 事件写出工具
 *
 * <p>事件格式：{@code event: <name>\n data: <单行 JSON>\n\n}；所有写操作串行化，
 * 避免心跳与增量内容交叉写入。</p>
 *
 * @author ruoyi
 */
public class SseEventWriter
{
    /** 心跳注释内容 */
    public static final String PING_COMMENT = "ping";

    private final SseEmitter emitter;

    private final ObjectMapper objectMapper;

    public SseEventWriter(SseEmitter emitter, ObjectMapper objectMapper)
    {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送命名事件
     *
     * @param event 事件名：meta / citation / delta / done / error
     * @param data 事件数据，序列化为单行 JSON
     * @throws IOException 客户端已断开
     */
    public synchronized void send(String event, Object data) throws IOException
    {
        emitter.send(SseEmitter.event().name(event)
                .data(objectMapper.writeValueAsString(data), MediaType.TEXT_PLAIN));
    }

    /**
     * 发送心跳注释行
     *
     * @throws IOException 客户端已断开
     */
    public synchronized void ping() throws IOException
    {
        emitter.send(SseEmitter.event().comment(PING_COMMENT));
    }

    /**
     * 正常结束
     */
    public synchronized void complete()
    {
        emitter.complete();
    }

    /**
     * 异常结束
     *
     * @param throwable 异常
     */
    public synchronized void completeWithError(Throwable throwable)
    {
        emitter.completeWithError(throwable);
    }
}
