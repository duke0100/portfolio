package com.example.project2;

import com.example.project2.dto.request.CreateProductRequest;
import com.example.project2.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interview topic: docs/interview/spring-boot/02-test-slices-vs-mockito.md#springboottest
 * Boots the full application on a real (embedded) database - nothing is mocked. This is the
 * slowest test style, so it's used for one end-to-end check, not every edge case.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ProductApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void create_thenFindById_roundTripsThroughTheRealDatabase() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .name("4K Monitor")
                .price(BigDecimal.valueOf(299.99))
                .sku("MON-4K-001")
                .build();

        ResponseEntity<String> createResponse = restTemplate.postForEntity(url("/api/v1/products"), request, String.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = objectMapper.readTree(createResponse.getBody());
        long id = created.get("data").get("id").asLong();

        assertThat(productRepository.findById(id)).isPresent(); // proves it is the real repository, not a stub

        ResponseEntity<String> getResponse = restTemplate.getForEntity(url("/api/v1/products/" + id), String.class);
        JsonNode fetched = objectMapper.readTree(getResponse.getBody());

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.get("data").get("sku").asText()).isEqualTo("MON-4K-001");
    }

    @Test
    void findById_missing_goesThroughTheRealGlobalExceptionHandler() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/v1/products/999999"), String.class);
        JsonNode body = objectMapper.readTree(response.getBody());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body.get("error").asText()).isEqualTo("NOT_FOUND");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
