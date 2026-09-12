## CORS Configuration Notes

### Assumption
- Service A does **not** include the base URL of Service B in its `allowed origins` (web origins) configuration.
- When Service B sends a request to Service A, it will receive a **CORS error**.

### Preflight Request
- Before sending `POST`, `PUT`, or other non-simple requests, the browser sends an **OPTIONS** request (preflight).
- This request checks whether Service A allows the actual request from Service B.

### Security Filter Behavior
- In `SecurityFilterChain`, if a request passes through the **Authentication Filter** first,
  it may **bypass the CORS filter**

---

## CORS Configuration Setup (Spring)

### Step 1: Define a `CorsConfigurationSource` bean

- Create a `CorsConfiguration` object:
    - Set allowed origins (`setAllowedOrigins`)
    - Set allowed methods (`setAllowedMethods`)
    - Set allowed headers (`setAllowedHeaders`)
    - Optionally allow credentials (`setAllowCredentials`)

- This configuration defines the CORS rules for specific endpoints, which origin/service can send request, which method, header request can be sent.

### Step 2: Register the configuration

- Create a `UrlBasedCorsConfigurationSource`
- Register the CORS configuration for specific URL patterns:
  ```
  source.registerCorsConfiguration("/api1/**", corsConfiguration1);
  source.registerCorsConfiguration("/api2/**", corsConfiguration2);
  ```

### Step 3: Return the `UrlBasedCorsConfigurationSource`
 