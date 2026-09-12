# What is the difference between @RequestBody and @ModelAttribute?

Applied in [project2](../../../project2) (Web + JPA + PostgreSQL; it owns the product catalog the
note points at). Each block links the file it came from, and each of those files back-links here.

| Section | Code in project2 |
|---|---|
| [Answer](#answer) | [`RequestBindingController.java`](../../../project2/src/main/java/com/example/project2/controller/RequestBindingController.java) |
| [@RequestBody (JSON)](#requestbody-json) | [`CreateProductNoteRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/CreateProductNoteRequest.java) |
| [@ModelAttribute (form and query)](#modelattribute-form-and-query) | [`ProductNoteFormRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ProductNoteFormRequest.java) |
| [One service, two inputs](#one-service-two-inputs) | [`ProductNoteServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductNoteServiceImpl.java) |

## Answer

`@RequestBody` hands the raw body to a `HttpMessageConverter` (Jackson, for JSON), which turns it
into one object. `@ModelAttribute` ignores the body: it creates the object, then fills it field by
field from form fields and query parameters. JSON APIs use the first, HTML forms and filter-style
GET endpoints use the second. A GET has no body, so only `@ModelAttribute` works there.

No new dependency — `spring-boot-starter-web` already brings both.

| | `@RequestBody` | `@ModelAttribute` |
|---|---|---|
| Reads from | the request body | query string + form fields |
| Content type | `application/json` | `application/x-www-form-urlencoded`, `multipart/form-data`, or none |
| Converted by | `HttpMessageConverter` (Jackson) | `WebDataBinder` |
| Nested objects and lists | yes, naturally | only with `a.b` / `tags[0]` naming |
| Works on GET | no | yes |

## @RequestBody (JSON)

[`CreateProductNoteRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/CreateProductNoteRequest.java)
— the DTO Jackson fills:

```java
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductNoteRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    @NotBlank(message = "Author is required")
    private String author;

    @Min(1)
    @Max(5)
    private Integer rating;

    /** A real list in JSON, which is exactly the shape form data cannot express cleanly. */
    private List<String> tags;
}
```

[`RequestBindingController.java`](../../../project2/src/main/java/com/example/project2/controller/RequestBindingController.java):

```java
@PostMapping(path = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<ProductNoteResponse> fromJson(@Valid @RequestBody CreateProductNoteRequest request) {
    return ApiResponse.ok(productNoteService.fromJson(request));
}
```

- The body can be read only once, so a method gets at most one `@RequestBody` parameter.
- Broken JSON is rejected before your method runs, so you never see a half-filled object.


`POST /api/v1/binding/product-notes/json` with

```json
{ "productId": 1, "author": "ana", "message": "restock soon", "rating": 4, "tags": ["new", "sale"] }
```

→

```json
{
  "success": true,
  "status": 200,
  "message": "Success",
  "data": {
    "boundBy": "@RequestBody",
    "readFrom": "application/json body",
    "productId": 1,
    "author": "ana",
    "message": "restock soon",
    "rating": 4,
    "tags": ["new", "sale"],
    "productName": "Product 1"
  }
}
```

## @ModelAttribute (form and query)

[`ProductNoteFormRequest.java`](../../../project2/src/main/java/com/example/project2/dto/request/ProductNoteFormRequest.java)
— same data, but settable one property at a time:

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductNoteFormRequest {

    @NotNull(message = "Product id is required")
    private Long productId;

    /** Arrives as the text "4" and is converted to a number by Spring, not by Jackson. */
    @Min(1)
    @Max(5)
    private Integer rating;

    /** Flat text only: "new,sale" has to be split by hand because form data has no list type. */
    private String tags;
}
```

[`RequestBindingController.java`](../../../project2/src/main/java/com/example/project2/controller/RequestBindingController.java)
— one DTO, two sources:

```java
@PostMapping(path = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
public ApiResponse<ProductNoteResponse> fromForm(@Valid @ModelAttribute ProductNoteFormRequest request) {
    return ApiResponse.ok(productNoteService.fromForm(request));
}

@GetMapping("/query")
public ApiResponse<ProductNoteResponse> fromQuery(@Valid @ModelAttribute ProductNoteFormRequest request) {
    return ApiResponse.ok(productNoteService.fromForm(request));
}
```

- Spring binds this way even without the annotation; writing it just makes the intent obvious.
- An unknown parameter is ignored in silence, so a typo in a field name is a bug you never see.

`GET /api/v1/binding/product-notes/query?productId=1&author=ana&message=restock&rating=4&tags=new,sale`
→ same body as above, with `"boundBy": "@ModelAttribute"`.

## One service, two inputs

[`ProductNoteServiceImpl.java`](../../../project2/src/main/java/com/example/project2/service/impl/ProductNoteServiceImpl.java):

```java
@Override
@Transactional(readOnly = true)
public ProductNoteResponse fromForm(ProductNoteFormRequest request) {
    return build("@ModelAttribute", "form fields or query string", request.getProductId(),
            request.getAuthor(), request.getMessage(), request.getRating(),
            splitTags(request.getTags()));
}

/** Form data has no list type, so a comma-separated field is the usual workaround. */
private List<String> splitTags(String tags) {
    if (tags == null || tags.isBlank()) {
        return List.of();
    }
    return Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
}
```

- Only the binding differs, so both paths share one `build(...)` and the annotation stays a controller concern.

## Comparison

| Aspect | `@RequestBody` | `@ModelAttribute` |
|---|---|---|
| What it does | converts the whole body into one object | copies each parameter into a matching property |
| When it applies | JSON or XML clients | HTML forms, file uploads, search filters |
| Requires | a converter for the content type | a no-arg constructor and setters |
| Failure mode | bad JSON gives a 400 before your method runs | a wrong parameter name is skipped and the field stays null |
| Validation error | `MethodArgumentNotValidException` | `BindException`, its parent class |
| Use when | callers send JSON to your API | a browser or a query string sends the data |

Rule of thumb: data in the URL means `@ModelAttribute`, JSON in the body means `@RequestBody`.

## Pitfalls

| Pitfall | Why it hurts |
|---|---|
| Two `@RequestBody` parameters on one method | the body is consumed once, so the second one fails |
| `@RequestBody` on a GET handler | there is no body to read, so the request is rejected |
| No no-arg constructor on a `@ModelAttribute` DTO | Spring cannot create the object and binding fails |
| Expecting `@ModelAttribute` to parse a JSON body | it ignores the body, so every field comes back null |
| Forgetting `@Valid` | the validation annotations on the DTO are simply never checked |
| A bound DTO with an `isAdmin`-style field | any caller can set it by adding one query parameter |

## Follow-up questions

**Can I use both in one method?** One `@RequestBody` plus `@RequestParam` or `@PathVariable`, yes.
Two `@RequestBody`, no.

**Is `@ModelAttribute` needed on the parameter?** No. On a *method* it means something else
entirely: run before every handler, put the result in the model.

**How do I upload a file with other fields?** `@ModelAttribute` with a `MultipartFile` property —
`@RequestBody` cannot read multipart.

**How is `@RequestParam` different?** It binds one value; `@ModelAttribute` fills a whole object.

## Try it

```bash
cd back-end/spring-boot/practice
mvn -pl project2 -am spring-boot:run

# JSON body -> boundBy is "@RequestBody"
curl -X POST http://localhost:8082/api/v1/binding/product-notes/json \
  -H 'Content-Type: application/json' \
  -d '{"productId":1,"author":"ana","message":"restock soon","rating":4,"tags":["new","sale"]}'

# Form fields -> boundBy is "@ModelAttribute"
curl -X POST http://localhost:8082/api/v1/binding/product-notes/form \
  -d 'productId=1&author=ana&message=restock&rating=4&tags=new,sale'

# Query string, no body at all
curl 'http://localhost:8082/api/v1/binding/product-notes/query?productId=1&author=ana&message=restock&rating=4&tags=new,sale'

# JSON sent to the form endpoint -> 415, the pitfall above
curl -X POST http://localhost:8082/api/v1/binding/product-notes/form \
  -H 'Content-Type: application/json' -d '{"productId":1}'
```

## References

- [Spring MVC: `@RequestBody`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestbody.html) — official docs
- [Spring MVC: `@ModelAttribute` method arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/modelattrib-method-args.html)
- Related, same topic folder: [01 - How do you design a versioned REST API in Spring Boot?](./01-rest-api-versioning.md)
- Related, other topic folder: [02 - Test slices vs Mockito](../spring-boot/02-test-slices-vs-mockito.md)
