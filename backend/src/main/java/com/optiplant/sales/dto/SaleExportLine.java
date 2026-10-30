package com.optiplant.sales.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** One line of a confirmed sale for the exportable report (RF-0053): no header total, costs or internal keys. */
public record SaleExportLine(String saleCode, Instant confirmedAt, String branchCode, String branchName, String actorCode,
                             String actorName, String sku, String productName, String unit, BigDecimal quantity,
                             BigDecimal unitPrice, BigDecimal discountPercent, BigDecimal netAmount) {}
