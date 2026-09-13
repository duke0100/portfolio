package com.example.project2.config;

import com.example.project2.concurrent.ContextPropagatingTaskDecorator;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#spring-integration
 * (this class is the AsyncConfigurer code sample in that section, and the executor every
 * {@code supplyAsync} in {@code ProductPricingServiceImpl} is handed)
 *
 * <p>Implementing {@code AsyncConfigurer} makes this pool the default for every {@code @Async}
 * method in the app, Modulith's {@code @ApplicationModuleListener} included. That is the point:
 * one bounded, named, metered pool beats Spring's fallback of a fresh thread per task.
 *
 * <p>The alternative - {@code ForkJoinPool.commonPool()}, which is what {@code supplyAsync} uses
 * when you pass no executor - has one thread per core minus one and is shared with every
 * parallel stream in the JVM. A blocking query on it stalls unrelated work.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(CatalogAsyncProperties.class)
@RequiredArgsConstructor
public class CatalogAsyncConfig implements AsyncConfigurer {

    /** Seconds a shutdown waits for in-flight stages before it interrupts them. */
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    private final CatalogAsyncProperties properties;

    @Bean(name = "catalogAsyncExecutor")
    public ThreadPoolTaskExecutor catalogAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getPoolSize());
        executor.setMaxPoolSize(properties.getPoolSize());
        // Bounded queue. An unbounded one accepts stages until the heap runs out.
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("catalog-async-");
        // A full queue slows the submitter down instead of throwing the stage away.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Carries MDC and the request attributes onto the pool thread.
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
        return executor;
    }

    /**
     * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#under-load
     * Pool gauges, so you can see queue depth instead of guessing. Binding through a separate bean
     * means Spring has already initialized the executor when we reach for the inner pool.
     */
    @Bean
    public MeterBinder catalogAsyncExecutorMetrics(ThreadPoolTaskExecutor catalogAsyncExecutor) {
        return registry -> ExecutorServiceMetrics.monitor(registry,
                catalogAsyncExecutor.getThreadPoolExecutor(),
                "catalog-async",
                Tags.of("pool", "catalog-async"));
    }

    @Override
    public Executor getAsyncExecutor() {
        return catalogAsyncExecutor();
    }

    /**
     * Only fires for {@code @Async void} methods, where there is no future to carry the failure.
     * A method returning {@code CompletableFuture} fails the future instead, and never lands here.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Async void method {} failed with args {}", method, Arrays.toString(params), throwable);
    }
}
