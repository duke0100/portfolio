package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.CacheDemoReportResponse;
import com.example.project2.service.CacheDemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/spring-data-jpa/01-first-level-vs-second-level-cache.md#measuring-it
 * Lives under {@code /api/v1/performance} next to {@code NPlusOneDemoController}, its sibling
 * diagnostic endpoint.
 */
@RestController
@RequestMapping("/api/v1/performance/cache-demo")
@RequiredArgsConstructor
public class CacheDemoController {

    private final CacheDemoService cacheDemoService;

    /** {@code ?categoryId=1} - the id must already exist (create one via POST /api/v1/categories). */
    @GetMapping
    public ApiResponse<CacheDemoReportResponse> run(@RequestParam Long categoryId) {
        return ApiResponse.ok(cacheDemoService.run(categoryId));
    }
}
