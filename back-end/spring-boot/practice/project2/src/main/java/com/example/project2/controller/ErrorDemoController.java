package com.example.project2.controller;

import com.example.commonlib.response.ApiResponse;
import com.example.project2.dto.request.ReserveStockRequest;
import com.example.project2.dto.response.StockReservationResponse;
import com.example.project2.service.StockReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#endpoints
 * (these endpoints are the ones the section's curl calls hit)
 *
 * <p>Every endpoint here is a happy path plus one way to fail. Notice there is no try/catch
 * anywhere - {@code ProblemDetailExceptionHandler} owns the error responses for this controller.
 */
@RestController
@RequestMapping("/api/v1/errors")
@RequiredArgsConstructor
public class ErrorDemoController {

    private final StockReservationService stockReservationService;

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#validation-errors
     * (the endpoint that produces the 400, 404 and 409 problem bodies)
     *
     * <p>@Valid fails before the method body runs, which is why validation never reaches the service.
     */
    @PostMapping("/reservations")
    public ApiResponse<StockReservationResponse> reserve(@Valid @RequestBody ReserveStockRequest request) {
        return ApiResponse.ok(stockReservationService.reserve(request));
    }

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#what-the-base-class-gives-you
     * (pass a non-numeric id here and the base class answers with 400 - we wrote no handler for it)
     */
    @GetMapping("/products/{id}/stock")
    public ApiResponse<Integer> stock(@PathVariable Long id) {
        return ApiResponse.ok(stockReservationService.currentStock(id));
    }

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#the-catch-all
     * (an exception nobody planned for, to show what the client is allowed to see)
     */
    @GetMapping("/boom")
    public ApiResponse<String> boom() {
        throw new IllegalStateException("Connection pool 'project2-postgresql-pool' is exhausted");
    }
}
