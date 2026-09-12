package com.example.project2.dto.response;

import lombok.*;

/**
 * Interview topic: docs/interview/rest-api/03-global-exception-handling.md#endpoints
 * (the success body of the reservation endpoint, shown next to the error bodies)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationResponse {

    private Long productId;

    private String sku;

    private Integer requested;

    /** What the stock would be after the reservation - nothing is written, this is a demo. */
    private Integer remaining;
}
