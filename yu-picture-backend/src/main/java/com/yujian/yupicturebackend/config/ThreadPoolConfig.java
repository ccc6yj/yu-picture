package com.yujian.yupicturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean("verificationExecutor")
    public ThreadPoolExecutor verificationExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = cpuCores * 2;       // 核心线程数：CPU核心数*2
        int maxPoolSize = corePoolSize * 2;    // 最大线程数：核心线程数*2
        int queueCapacity = 2000;              // 缩减队列容量，避免任务堆积

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时让提交者运行任务，避免任务丢失
        );
    }
}
