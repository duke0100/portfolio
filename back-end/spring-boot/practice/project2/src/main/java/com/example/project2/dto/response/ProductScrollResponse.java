package com.example.project2.dto.response;

import java.util.List;

/**
 * Interview topic: docs/interview/rest-api/05-pagination-and-sorting.md#slice-and-cursor-based-pagination
 * What a {@code Slice} can tell the client: the rows, whether another batch exists, and the cursor
 * to ask for it. There is no total count here because no count query was run.
 *
 * @param content    the rows in this batch
 * @param hasNext    true when at least one more row exists after this batch
 * @param nextAfterId cursor to pass back as {@code afterId}, null when the scroll is finished
 */
public record ProductScrollResponse(List<ProductSummaryResponse> content,
                                    boolean hasNext,
                                    Long nextAfterId) {
}
