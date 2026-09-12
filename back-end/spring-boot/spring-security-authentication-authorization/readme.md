# Table of Contents:
- [1. Necessary dependencies](#1-necessary-dependencies)
- [2. Swagger](#2-swagger)
- [3. Configuration](#3-configuration)
- [4. Security Filter Chain bean](#4-security-filter-chain-bean)

## 1. Necessary dependencies
```
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

- `org.springdoc:springdoc-openapi-starter-webmvc-api`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
```

## 2. Swagger
### OpenAPI Bean Configuration

Define an `OpenAPI` bean and configure the following sections:

### 2.1. Info
Provide basic API metadata:
- **title**: Name of the API
- **description**: Short description of the API
- **version**: API version

### 2.2. OAuthFlows
- password: tokenUrl is resourceServerUri

### 2.3. Security Scheme 1
Configure a security scheme with:
- **type**: SecurityScheme.type.oauth2
- **description**
- **flow**: defined oAuthFlow

=> OAuthFlows with password mode and SecurityScheme are used to login with username - password - clientId

### 2.4. OAuthFlows
- clientCredentials: tokenUrl is resourceServerUri

### 2.5. Security Scheme 2
Configure a security scheme with:
- **type**: SecurityScheme.type.oauth2
- **description**
- **flow**: defined oAuthFlow

=> OAuthFlows with client mode and SecurityScheme are used to login with clientId and clientSecret

### 2.6. Components
Include the following in components:
- **schemas**
- **securitySchemes**: add defined password schema and client schema

### 2.7. Security Requirement
- Define 2 SecurityRequirement with two name of mode

### 2.8. SpringDocUtils Customization
Customize Swagger behavior:
- Use `removeRequestWrapperToIgnore(...)` and `removeResponseWrapperToIgnore(...)` 
- Some data types (e.g., `Map` in `RequestBody`) are ignored in sample request/response by default in Swagger UI

### 2.9. Final OpenAPI Object
Return the configured `OpenAPI` instance with:
- **securityItem**
- **components**
- **info**

## 3. Configuration
```
swagger.api.oauth2.resourceserver.jwt.issuer-uri=uri #format: https://a.b.c/realms/<realm-name>/protocol/openid-connect/token
springdoc.swagger-ui.oauth.client-id=clientId #set default client
spring.security.oauth2.resourceserver.jwt.issuer-uri=uri #format: https://a.b.c/realms/<realm-name> token will be re-check at this endpoint
```

## 4. Security Filter Chain bean
We define two SecurityFilterChain beans: a default one and a custom (real) one.

The default SecurityFilterChain is provided by the shared library, while the custom one is defined in the main application.

Depending on whether the custom enable annotation is present, Spring will choose which bean to load into the application context.

### 4.1. Default SecurityFilterChain bean
- **authorizeHttpRequests**: anyRequest.permitAll
- **csrf**: AbstractHttpConfigure.disable
- assign with a checking annotation 

### 4.2. Custom SecurityFilterChain bean
- **cors**: Customizer.default
- **authorizeHttpRequests**: 
  - permitAll: requestMatchers list some url
  - authenticated: anyRequest
- **oauth2ResourceServer**:
  - authenticationEntrypoint: a custom AuthenticationEntryPoint [Custom AuthenticationEntryPoint](#custom-authenticationentrypoint)
  - jwt: jwtAuthenticationConverter set a custom bean jwtAuthenticationConverter [Custom JwtAuthenticationConverter](#custom-jwtauthenticationconverter)

### Custom AuthenticationEntryPoint
- Define bean A implement AuthenticationEntryPoint to catch 401,403 Exception
- In `commence` method, call bean HandlerExceptionResolver + @ControllerAdvice 
  - to catch 401 Exception: AuthenticationException
  - to catch 403 Exception: AuthorizationDeniedException

### Custom JwtAuthenticationConverter
- Configure JwtAuthenticationConverter to use a custom JwtGrantedAuthoritiesConverter that returns a Collection<GrantedAuthority>.
- When `new JwtGrantedAuthoritiesConverter` is invoked, the SecurityFilterChain automatically extracts the JWT from the request header and passes it to the converter.
- To get Collection<GrantedAuthority> from Jwt, we need to custom a method to get this. 
  - Extract the realm_access claim from the JWT
  - convert these claims from String to SimpleGrantedAuthority and add to Collection<GrantedAuthority>

### 4.3. Define WebSecurityCustomizer
**WebSecurityCustomizer**: httpFirewall set DefaultHttpFirewall

By default, Spring security use StrictHttpFirewall, which is a stricter firewall when reject some type of url.

### 4.4. Define custom annotations
- @EnableAuthentication annotation: assigned at the main class
- @Conditional(ConditionalOnEnableAuthentication.class) : assigned at SecurityFilterChain bean
- ConditionalOnEnableAuthentication class implement Condition: implement logic to check whether @EnableAuthentication is used or not
by using ConditionalContext.
- We need to define ConditionalOnEnableAuthentication and ConditionalOnDisableAuthentication, can't merge them
