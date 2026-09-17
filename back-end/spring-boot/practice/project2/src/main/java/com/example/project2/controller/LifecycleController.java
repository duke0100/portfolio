package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.DrainProbeResponse;
import com.example.project2.dto.response.LifecycleStateResponse;
import com.example.project2.service.ShutdownProbeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/spring-boot/04-graceful-shutdown.md#watching-the-shutdown
 * (these two endpoints are the ones quoted in that section and in #proving-the-drain)
 *
 * <p>Deliberately on the application port, not on the management port: the state it reports is
 * about this instance's own traffic, and Actuator's exposure allow-list keeps custom endpoints out.
 */
@RestController
@RequestMapping("/api/v1/lifecycle")
@RequiredArgsConstructor
public class LifecycleController {

    private final ShutdownProbeService shutdownProbeService;

    /** What this instance is doing: serving, draining, or refusing traffic. */
    @GetMapping("/state")
    public ApiResponse<LifecycleStateResponse> state() {
        return ApiResponse.ok(shutdownProbeService.currentState());
    }

    /**
     * {@code ?holdMillis=5000} - one slow request, so you can send SIGTERM while it runs and see
     * it still answer 200.
     */
    @GetMapping("/drain-probe")
    public ApiResponse<DrainProbeResponse> drainProbe(
            @RequestParam(defaultValue = "1000") long holdMillis) {
        return ApiResponse.ok(shutdownProbeService.hold(holdMillis));
    }
}
