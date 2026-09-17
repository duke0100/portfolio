package com.example.project2.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#proving-the-drain
 * Result of one deliberately slow request. A 200 with {@code finishedDuringShutdown=true} is the
 * proof that graceful shutdown waited for an in-flight request instead of cutting the connection.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrainProbeResponse {

    private long requestedHoldMillis;

    private long actualHoldMillis;

    /** True when SIGTERM had already arrived before this request started. */
    private boolean startedDuringShutdown;

    /** True when SIGTERM arrived while this request was running. */
    private boolean finishedDuringShutdown;
}
