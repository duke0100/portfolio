package com.example.project2.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interview topic: docs/interview/spring-data-jpa/03-jpa-relationship-annotations.md#jointable-many-to-many
 * Returns tag names rather than tag entities, so the join table stays an implementation detail.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductTagsResponse {

    private Long productId;
    private String productName;

    /** Computed after load from the transient field, so it is not stored anywhere. */
    private BigDecimal effectivePrice;

    private List<String> tagNames;
    private int tagCount;
}
