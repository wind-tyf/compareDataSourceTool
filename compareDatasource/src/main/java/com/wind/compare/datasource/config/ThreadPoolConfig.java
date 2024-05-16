package com.wind.compare.datasource.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池配置
 * @author wind.tan
 * @date 2024-05-15
 * 核心线程
 * 最大线程
 * 超时时间
 * 超时单位
 * 阻塞队列
 * 线程工程
 * 拒绝策略
 */
@Configuration
public class ThreadPoolConfig {

    public final static String SEARCH_SQL = "searchSql";
    public final static String COMMON_POOL = "commonPool";

    /**
     * 查询数据库的线程池
     */
    @Bean(SEARCH_SQL)
    public ThreadPoolExecutor searchSql(){
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(4, 4, 1L, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(20), new ThreadFactoryBuilder().setNamePrefix(SEARCH_SQL + "_").setDaemon(true).build(),
                new ThreadPoolExecutor.AbortPolicy());
        return threadPoolExecutor;
    }

    /**
     * 非特定场景的通用线程池
     */
    @Bean(COMMON_POOL)
    public ThreadPoolExecutor commonPool(){
        return new ThreadPoolExecutor(4, 4, 1L, TimeUnit.MINUTES,
                new ArrayBlockingQueue<>(20), new ThreadFactoryBuilder().setNamePrefix(COMMON_POOL+"_").setDaemon(true).build(),
                new ThreadPoolExecutor.AbortPolicy());
    }
}
