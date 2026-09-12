package com.example.project2.mapper;

import com.example.project2.dto.response.ProductResponse;
import com.example.project2.dto.response.v2.ProductV2Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#one-service-two-payloads
 *
 * <p>Only the response shape is versioned, not the service: {@code ProductService} still returns
 * the v1 {@link ProductResponse} and this mapper reshapes it. Retiring v2 means deleting this
 * class and its controllers, nothing else.
 */
@Component
public class ProductV2Mapper {

    private static final Pattern DIMENSIONS = Pattern.compile(
            "([0-9]+(?:[.][0-9]+)?)[ ]*[xX*][ ]*([0-9]+(?:[.][0-9]+)?)[ ]*[xX*][ ]*([0-9]+(?:[.][0-9]+)?)");
    private static final String CURRENCY = "USD";

    public ProductV2Response toV2(ProductResponse v1) {
        return ProductV2Response.builder()
                .id(v1.getId())
                .name(v1.getName())
                .sku(v1.getSku())
                .status(v1.getStatus())
                .stockQuantity(v1.getStockQuantity())
                .category(v1.getCategoryId() == null ? null
                        : new ProductV2Response.CategoryRef(v1.getCategoryId(), v1.getCategoryName()))
                .pricing(new ProductV2Response.Pricing(
                        v1.getPrice(),
                        v1.getDiscountPercent(),
                        effectivePrice(v1.getPrice(), v1.getDiscountPercent()),
                        CURRENCY))
                .dimensions(parseDimensions(v1.getDimensions()))
                .updatedAt(v1.getUpdatedAt())
                .build();
    }

    private static BigDecimal effectivePrice(BigDecimal listPrice, BigDecimal discountPercent) {
        if (listPrice == null) {
            return null;
        }
        if (discountPercent == null || discountPercent.signum() == 0) {
            return listPrice.setScale(2, RoundingMode.HALF_UP);
        }
        return listPrice.multiply(BigDecimal.ONE.subtract(discountPercent.movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Free text in v1, so anything that is not "L x W x H" simply has no structured value. */
    private static ProductV2Response.Dimensions parseDimensions(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = DIMENSIONS.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return new ProductV2Response.Dimensions(
                new BigDecimal(matcher.group(1)),
                new BigDecimal(matcher.group(2)),
                new BigDecimal(matcher.group(3)));
    }
}
