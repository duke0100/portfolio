# How would you implement global exception handling in Spring Boot REST APIs?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL; it owns the product stock these
endpoints reserve). Each block links its source file, and each file back-links here.

| Section | Code in project2 |
|---|---|
| [Config](#config) | [`application.properties`](../../../project2/src/main/resources/application.properties) |
| [The advice](#the-advice) | [`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java) |
| [Domain exceptions](#domain-exceptions) | [`StockUnavailableException.java`](../../../project2/src/main/java/com/example/project2/exception/StockUnavailableException.java), [`StockReservationServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/StockReservationServiceImpl.java) |
| [Validation errors](#validation-errors) | [`ReserveStockRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ReserveStockRequest.java) |
| [What the base class gives you](#what-the-base-class-gives-you) | [`ErrorDemoController.java`](../../../project2/src/main/java/com/example/project2/controller/ErrorDemoController.java) |
| [The catch-all](#the-catch-all) | [`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java) |

## Answer

One `@RestControllerAdvice` holds every `@ExceptionHandler`, so controllers and services just throw
and never catch. It extends `ResponseEntityExceptionHandler`, which already maps Spring's own MVC
exceptions, so I add handlers only for my domain exceptions. Every body is an RFC 7807
`ProblemDetail`. The catch-all for `Exception` returns a generic 500 with a traceId, and the stack
trace goes to the log only.

No new dependency needed.

| Piece | What it is for |
|---|---|
| `@RestControllerAdvice` | one advice bean shared by many controllers |
| `@ExceptionHandler(X.class)` | maps one exception type to one response |
| `ResponseEntityExceptionHandler` | base class that already handles Spring's MVC exceptions |
| `ProblemDetail` | the RFC 7807 body (`application/problem+json`) |
| `@Order` | decides which advice wins when two can handle the same exception |

## Config

[`application.properties`](../../../project2/src/main/resources/application.properties):

```properties
spring.mvc.problemdetails.enabled=false

server.error.include-message=never
server.error.include-stacktrace=never
server.error.include-binding-errors=never
server.error.include-exception=false
```

| Choice | vs the alternative |
|---|---|
| `spring.mvc.problemdetails.enabled=false` | vs `true`: `true` registers Spring's own ProblemDetail advice, which then competes with ours for the same MVC exceptions. Turn it on only if you write no advice yourself. |
| `server.error.include-stacktrace=never` | vs `always`: `always` puts your class names and library versions in the response. |
| `server.error.include-message=never` | vs `always`: an exception message often quotes SQL or a user's data. |

[`application-dev.properties`](../../../project2/src/main/resources/application-dev.properties)
— the same knobs set the leaky way on purpose:

```properties
server.error.include-message=always
server.error.include-stacktrace=always
server.error.include-binding-errors=always
server.error.include-exception=true
```

## The advice

[`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java):

```java
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ErrorDemoController.class)
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String PROBLEM_TYPE_BASE = "https://example.com/problems/";
```

- Scoped to one controller so the rest of project2 keeps the `ApiError` envelope from common-lib's
  [`GlobalExceptionHandler`](../../../common-lib/src/main/java/com/example/commonlib/exception/GlobalExceptionHandler.java). Pick one style per application.
- `@Order` decides which of the two advices wins; without it that depends on bean sorting.

## Domain exceptions

[`StockUnavailableException.java`](../../../project2/src/main/java/com/example/project2/exception/StockUnavailableException.java)
— carries data, not an HTTP status:

```java
@Getter
public class StockUnavailableException extends RuntimeException {

    private final Long productId;
    private final int requested;
    private final int available;
```

[`StockReservationServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/StockReservationServiceImpl.java)
— throws and moves on:

```java
Product product = productRepository.findById(request.getProductId())
        .orElseThrow(() -> new ProductNotFoundException(request.getProductId()));

int available = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
if (available < request.getQuantity()) {
    throw new StockUnavailableException(product.getId(), request.getQuantity(), available);
}
```

[`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java)
— the only place that knows status codes:

```java
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
```

- Returning `ProblemDetail` is enough: Spring reads the response status out of it.
- `setProperty` adds top-level JSON members, so the client can retry on `available` instead of
  parsing the message text.

`POST /api/v1/errors/reservations` with `{"productId": 1, "quantity": 99}` →

```json
{
  "type": "https://example.com/problems/stock-unavailable",
  "title": "Not enough stock",
  "status": 409,
  "detail": "Only 12 item(s) left for product 1",
  "instance": "/api/v1/errors/reservations",
  "productId": 1,
  "requested": 99,
  "available": 12,
  "traceId": "6f1c2a9e"
}
```

## Validation errors

[`ReserveStockRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ReserveStockRequest.java):

```java
@NotNull(message = "Product id is required")
private Long productId;

@NotNull(message = "Quantity is required")
@Min(value = 1, message = "Quantity must be at least 1")
@Max(value = 100, message = "Quantity must be 100 or less")
private Integer quantity;
```

[`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java)
— override the base class method, do not add a new `@ExceptionHandler`:

```java
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
```

- `ex.getBody()` is already a `ProblemDetail` built by Spring, so we enrich it.
- Field errors stay a list, because the UI has to highlight the right input.

`POST /api/v1/errors/reservations` with `{"quantity": 0}` →

```json
{
  "type": "https://example.com/problems/validation-failed",
  "title": "Request validation failed",
  "status": 400,
  "detail": "The request body has 2 invalid field(s)",
  "instance": "/api/v1/errors/reservations",
  "errors": [
    { "field": "productId", "message": "Product id is required", "rejectedValue": null },
    { "field": "quantity", "message": "Quantity must be at least 1", "rejectedValue": 0 }
  ],
  "traceId": "b28d7c41"
}
```

## What the base class gives you

[`ErrorDemoController.java`](../../../project2/src/main/java/com/example/project2/controller/ErrorDemoController.java):

```java
@GetMapping("/products/{id}/stock")
public ApiResponse<Integer> stock(@PathVariable Long id) {
    return ApiResponse.ok(stockReservationService.currentStock(id));
}
```

- We wrote no handler for a bad path variable, yet `.../products/abc/stock` still answers with a
  400 problem body. That is the base class.

| Exception it already handles | Status |
|---|---|
| `MethodArgumentNotValidException`, `HandlerMethodValidationException` | 400 |
| `HttpMessageNotReadableException`, `MissingServletRequestParameterException`, `TypeMismatchException` | 400 |
| `NoResourceFoundException`, `NoHandlerFoundException` | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `HttpMediaTypeNotAcceptableException` | 406 |
| `HttpMediaTypeNotSupportedException` | 415 |
| `AsyncRequestTimeoutException` | 503 |

## The catch-all

[`ProblemDetailExceptionHandler.java`](../../../project2/src/main/java/com/example/project2/exception/ProblemDetailExceptionHandler.java):

```java
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
```

- Spring picks the most specific handler, so this runs only when nothing else matched.
- The vague message is deliberate; the traceId is what support asks the caller for.

`GET /api/v1/errors/boom` →

```json
{
  "type": "https://example.com/problems/internal-error",
  "title": "Internal server error",
  "status": 500,
  "detail": "Something went wrong on our side",
  "instance": "/api/v1/errors/boom",
  "traceId": "0a4b91de"
}
```

## Endpoints

| Request | Result |
|---|---|
| `POST /api/v1/errors/reservations` `{"productId":1,"quantity":2}` | 200 `StockReservationResponse` |
| `POST /api/v1/errors/reservations` `{"quantity":0}` | 400 validation problem |
| `POST /api/v1/errors/reservations` `{"productId":999999999,"quantity":1}` | 404 product-not-found |
| `POST /api/v1/errors/reservations` `{"productId":1,"quantity":99}` | 409 stock-unavailable |
| `GET /api/v1/errors/products/abc/stock` | 400 from the base class |
| `GET /api/v1/errors/boom` | 500 with traceId only |

## Comparison

| Aspect | try/catch in the controller | `@ControllerAdvice` | `ResponseEntityExceptionHandler` |
|---|---|---|---|
| What it does | handles one call site | handles every controller in scope | adds ready-made handlers for Spring MVC exceptions |
| When it applies | you need per-endpoint recovery | almost always | when your advice extends it |
| Duplication | repeated in every method | none | none |
| Failure mode | one forgotten catch returns a raw 500 | wrong `@Order` between two advices | an override that skips `handleExceptionInternal` |
| Use when | retry or fallback logic | the default choice | you want consistent bodies for framework errors too |

| Aspect | `ProblemDetail` (RFC 7807) | custom envelope like `ApiError` |
|---|---|---|
| Content type | `application/problem+json` | `application/json` |
| Fields | standard `type/title/status/detail/instance` plus extras | whatever you invented |
| Client support | understood by generated clients and gateways | needs your own documentation |
| Use when | new or public APIs | an existing contract you cannot break |

Rule of thumb: new API, use `ProblemDetail`; existing consumers, keep your envelope.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| `@ExceptionHandler(Exception.class)` sitting above the framework handlers | it can swallow a 404 or a 405 and turn it into a 500 |
| Two advices handling the same exception without `@Order` | the winner depends on bean sorting, so behaviour can change between builds |
| Putting `ex.getMessage()` in `detail` for unexpected errors | those messages leak SQL, file paths and user data |
| Catching the exception in the service to "handle it" | the advice never sees it and the response silently becomes 200 |
| Expecting the advice to catch a servlet filter failure | `@ControllerAdvice` only covers what is thrown inside the DispatcherServlet handler chain |
| Returning 200 with an error field in the body | every client that checks the status code is now wrong |
| Logging the same exception in the service and again in the advice | duplicate stack traces make the log twice as hard to read |

## Follow-up questions

**@ControllerAdvice vs @RestControllerAdvice?** The second is the first plus `@ResponseBody`. Use
it for REST.

**How do you limit an advice to some controllers?** `basePackages`, `assignableTypes` or
`annotations` on the annotation. This one uses `assignableTypes`.

**Does it catch exceptions from a servlet filter?** No, filters run before the DispatcherServlet.
Those land on `/error`, which is why `server.error.*` still matters.

**What about @ResponseStatus on the exception class?** Fine for simple cases, but you get no extra
fields and no `type`.

**How do you test it?** `@WebMvcTest` plus `@Import(ProblemDetailExceptionHandler.class)`, then
assert `status()` and `jsonPath("$.type")`.

**Where does the traceId come from in a real system?** From the tracing context: Micrometer Tracing
puts it in the MDC, so you read it there instead of generating a UUID.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

# 400 - per-field problem body
curl -i -X POST localhost:8088/api/v1/errors/reservations \
  -H 'Content-Type: application/json' -d '{"quantity":0}'

# 409 - the extra members tell the client what to retry with
curl -s -X POST localhost:8088/api/v1/errors/reservations \
  -H 'Content-Type: application/json' -d '{"productId":1,"quantity":99}'

# 400 from the base class, with no handler of ours involved
curl -i localhost:8088/api/v1/errors/products/abc/stock

# 500 - traceId in the body, stack trace only in the log
curl -s localhost:8088/api/v1/errors/boom
```

## References

- [Spring Framework — Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) (obsoletes RFC 7807)
- [`ProblemDetail` javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ProblemDetail.html)
- Related, same topic folder: [REST API versioning](./01-rest-api-versioning.md), [@RequestBody vs @ModelAttribute](./02-request-body-vs-model-attribute.md)
- Related, other topic folder: [Test slices vs Mockito](../spring-boot/02-test-slices-vs-mockito.md)
