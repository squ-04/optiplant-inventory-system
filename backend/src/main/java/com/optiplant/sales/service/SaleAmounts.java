package com.optiplant.sales.service;

import java.math.BigDecimal;

/** Exact sale amounts (RF-0029, data model §5.1): nothing is rounded here; display rounding belongs to the readers. */
final class SaleAmounts {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SaleAmounts() {}

    /** Unit price after the line discount: price × (1 − discount/100); 100 with 10% → 90 (RF-0029-CA-02). */
    static BigDecimal netUnitPrice(BigDecimal price, BigDecimal discount) {
        return price.multiply(HUNDRED.subtract(discount)).movePointLeft(2);
    }

    /** Line net stored on the sale line: commercial quantity × net unit price. */
    static BigDecimal lineNet(BigDecimal quantity, BigDecimal price, BigDecimal discount) {
        return netUnitPrice(price, discount).multiply(quantity);
    }
}
