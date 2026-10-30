package com.optiplant.dashboard.service;

import com.optiplant.dashboard.dto.DemandResponse.Item;
import com.optiplant.dashboard.dto.DemandResponse.Ranking;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The ranking of RF-0046. Within each base unit, N products with sales: high takes up to 5 by quantity descending and low
 * up to 5 of the rest by quantity ascending; below 10 products high takes ceil(N/2) and low floor(N/2). Ties go by SKU
 * ascending, so the result is stable.
 */
final class DemandRanking {
    static final int MAX_PER_GROUP = 5;
    private static final Comparator<Sold> BY_SKU = Comparator.comparing(Sold::sku);
    private static final Comparator<Sold> ASCENDING = Comparator.comparing(Sold::quantity).thenComparing(BY_SKU);
    private static final Comparator<Sold> DESCENDING = Comparator.comparing(Sold::quantity, Comparator.reverseOrder()).thenComparing(BY_SKU);

    private DemandRanking() {}

    record Sold(UUID productExternalId, String sku, String name, String baseUnitName, BigDecimal quantity) {}

    static List<Ranking> rank(List<Sold> sold) {
        // A unit typed "Kg" in one product and "kg" in another is the same unit.
        Map<String, List<Sold>> units = sold.stream().collect(Collectors.groupingBy(
            item -> item.baseUnitName().strip().toLowerCase(Locale.ROOT), TreeMap::new, Collectors.toList()));
        return units.values().stream().map(DemandRanking::ranking).toList();
    }

    private static Ranking ranking(List<Sold> products) {
        int total = products.size();
        List<Sold> descending = products.stream().sorted(DESCENDING).toList();
        int highCount = Math.min(MAX_PER_GROUP, total - total / 2);
        List<Sold> high = descending.subList(0, highCount);
        List<Sold> low = descending.subList(highCount, total).stream().sorted(ASCENDING)
            .limit(Math.min(MAX_PER_GROUP, total / 2)).toList();
        return new Ranking(products.getFirst().baseUnitName(), total, items(high), items(low));
    }

    private static List<Item> items(List<Sold> products) {
        List<Item> items = new ArrayList<>();
        products.forEach(item -> items.add(new Item(item.productExternalId(), item.sku(), item.name(),
            DashboardService.quantity(item.quantity()))));
        return items;
    }
}
