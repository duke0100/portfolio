package com.example.project2.lifecycle;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#non-web-work
 * (this class is the SmartLifecycle code sample in that section; {@code IdempotencyCleanupJob}
 * asks it before every sweep)
 *
 * <p>Graceful shutdown only covers HTTP. A scheduled sweep that fires while the context is closing
 * would open a transaction on a connection pool that is about to be closed, and fail with a stack
 * trace on the way out.
 *
 * <p>The phase is what makes this work. Spring stops lifecycle beans from the highest phase down,
 * and the web server sits just under {@code DEFAULT_PHASE}, so a lower phase closes this gate
 * after the last HTTP request is done. Every {@code stop()} also finishes before the first
 * {@code @PreDestroy}, so the pool is still open while background work is winding down.
 */
@Slf4j
@Component
public class BackgroundWorkGate implements SmartLifecycle {

    /** Low enough to stop after the web server, high enough to stop before anything is destroyed. */
    private static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;

    /** Read by scheduler threads, written by the lifecycle thread, hence volatile. */
    private volatile boolean accepting;

    /** True while this instance still runs background jobs. */
    public boolean isAccepting() {
        return accepting;
    }

    @Override
    public void start() {
        accepting = true;
    }

    @Override
    public void stop() {
        accepting = false;
        log.info("Shutdown: background jobs closed for new work");
    }

    @Override
    public boolean isRunning() {
        return accepting;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
