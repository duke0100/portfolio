package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.NPlusOneReportResponse;
import com.example.project2.service.NPlusOneDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Interview topic: docs/interview/spring-boot/03-n-plus-one-query-problem.md#measuring-it
 * Demo endpoints for comparing N+1 fetch strategies. Lives under {@code /api/v1/performance}
 * instead of {@code /api/v1/orders} so it doesn't clash with {@code OrderController}'s routes.
 */
@RestController
@RequestMapping("/api/v1/performance/n-plus-one")
@RequiredArgsConstructor
public class NPlusOneDemoController {

    private final NPlusOneDemoService nPlusOneDemoService;

    /** One strategy, e.g. {@code ?strategy=NAIVE&status=PENDING&size=20}. */
    @GetMapping
    public ApiResponse<NPlusOneReportResponse> run(
            @RequestParam(defaultValue = "NAIVE") NPlusOneDemoService.Strategy strategy,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(nPlusOneDemoService.run(strategy, status, pageable));
    }

    /** All six strategies over the same page, so the statement counts line up side by side. */
    @GetMapping("/compare")
    public ApiResponse<List<NPlusOneReportResponse>> compare(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ApiResponse.ok(nPlusOneDemoService.compare(status, pageable));
    }
}
