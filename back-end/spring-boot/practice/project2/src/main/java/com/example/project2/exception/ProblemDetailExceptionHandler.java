package com.example.project2.exception;

import com.example.project2.controller.ErrorDemoController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#the-advice
 * (this class is the code sample in that section)
 *
 * <p>One place that turns every exception thrown by {@link ErrorDemoController} into an RFC 7807
 * body, so no controller ever needs a try/catch.
 *
 * <p>It is scoped to that one controller on purpose: the rest of project2 keeps the shared
 * {@code ApiError} envelope from common-lib's {@code GlobalExceptionHandler}. A real service picks
 * one of the two styles for the whole application.
 *
 * <p>Extending {@code ResponseEntityExceptionHandler} means Spring's own MVC exceptions (unknown
 * media type, unreadable body, wrong path variable type) already come back as RFC 7807 without a
 * line of code here.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ErrorDemoController.class)
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    /** Documentation URL per error kind - the "type" is what makes a problem machine-readable. */
    private static final String PROBLEM_TYPE_BASE = "https://example.com/problems/";

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
     * (the 404 handler in that section)
     *
     * <p>Returning ProblemDetail is enough - Spring takes the response status from its own status
     * field.
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFound(ProductNotFoundException ex, HttpServletRequest request) {
        log.warn("Product not found: {}", ex.getProductId());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "product-not-found"));
        problem.setTitle("Product not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("traceId", newTraceId());
        return problem;
    }

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#domain-exceptions
     * (the 409 handler in that section)
     *
     * <p>The extra properties tell the client what to send next; the message text is for humans only.
     */
    @ExceptionHandler(StockUnavailableException.class)
    public ProblemDetail handleStockUnavailable(StockUnavailableException ex, HttpServletRequest request) {
        log.warn("Stock conflict on product {}: requested {}, available {}",
                ex.getProductId(), ex.getRequested(), ex.getAvailable());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "stock-unavailable"));
        problem.setTitle("Not enough stock");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("productId", ex.getProductId());
        problem.setProperty("requested", ex.getRequested());
        problem.setProperty("available", ex.getAvailable());
        problem.setProperty("traceId", newTraceId());
        return problem;
    }

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#validation-errors
     * (the @Valid handler in that section)
     *
     * <p>The base class already built a 400 ProblemDetail, so we only add the per-field list to it.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<FieldProblem> fieldProblems = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldProblem(error.getField(), error.getDefaultMessage(), error.getRejectedValue()))
                .toList();

        ProblemDetail problem = ex.getBody();
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "validation-failed"));
        problem.setTitle("Request validation failed");
        problem.setDetail("The request body has " + fieldProblems.size() + " invalid field(s)");
        problem.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        problem.setProperty("errors", fieldProblems);
        problem.setProperty("traceId", newTraceId());

        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /**
     * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#the-catch-all
     * (the last-resort handler in that section)
     *
     * <p>The stack trace goes to the log, never to the client - the traceId is how support links
     * the two.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = newTraceId();
        log.error("Unhandled exception [traceId={}] on {}", traceId, request.getRequestURI(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side");
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "internal-error"));
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** One rejected field, as it appears inside the "errors" member of the problem body. */
    public record FieldProblem(String field, String message, Object rejectedValue) {
    }
}
