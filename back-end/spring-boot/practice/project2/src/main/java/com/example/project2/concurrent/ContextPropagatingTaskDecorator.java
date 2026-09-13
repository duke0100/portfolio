package com.example.project2.concurrent;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Map;

/**
 * Interview topic: docs/interview/concurrency/02-completablefuture-vs-future.md#spring-integration
 * (this class is the TaskDecorator code sample in that section)
 *
 * <p>MDC and the current request live in ThreadLocals, and a ThreadLocal does not follow a task
 * onto a pool thread. Without this decorator every async log line loses its trace id.
 *
 * <p>The snapshot is taken on the submitting thread and applied on the pool thread, then cleared.
 * Clearing matters because pool threads are reused - the next task would otherwise inherit this
 * request's trace id.
 */
public class ContextPropagatingTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return () -> {
            applyMdc(contextMap);
            if (requestAttributes != null) {
                RequestContextHolder.setRequestAttributes(requestAttributes);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }

    private void applyMdc(Map<String, String> contextMap) {
        MDC.clear();
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }
}
