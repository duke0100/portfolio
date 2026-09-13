package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.response.CatalogSnapshotResponse;
import com.example.project2.service.CatalogSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interview topic: docs/interview/concurrency/01-callable-vs-runnable.md#endpoint
 * (this class is the endpoint that runs the fan-out)
 *
 * <p>The request thread blocks while the three tasks run. That is on purpose: the work is short
 * and has a hard timeout, so async request handling would buy nothing here.
 */
@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
public class CatalogSnapshotController {

    private final CatalogSnapshotService catalogSnapshotService;

    @GetMapping("/{categoryId}/snapshot")
    public ApiResponse<CatalogSnapshotResponse> snapshot(@PathVariable Long categoryId) {
        return ApiResponse.ok(catalogSnapshotService.snapshot(categoryId));
    }
}
