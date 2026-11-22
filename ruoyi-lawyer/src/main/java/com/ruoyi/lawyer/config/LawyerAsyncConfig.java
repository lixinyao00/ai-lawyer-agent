package com.ruoyi.lawyer.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 律师业务线程池配置
 *
 * @author ruoyi
 */
@Configuration
public class LawyerAsyncConfig
{
    /**
     * SSE 流式问答线程池：core 4 / max 16 / queue 50 / AbortPolicy
     */
    @Bean(name = "lawyerStreamExecutor")
    public ThreadPoolTaskExecutor lawyerStreamExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("lawyer-stream-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    /**
     * 合同审查等异步任务线程池：core 2 / max 4 / queue 20 / AbortPolicy
     */
    @Bean(name = "lawyerTaskExecutor")
    public ThreadPoolTaskExecutor lawyerTaskExecutor()
    {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("lawyer-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    /**
     * SSE 心跳调度器：单线程
     */
    @Bean(name = "lawyerHeartbeatScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService lawyerHeartbeatScheduler()
    {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lawyer-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }
}
