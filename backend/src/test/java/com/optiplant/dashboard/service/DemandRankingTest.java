package com.optiplant.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.dashboard.dto.DemandResponse.Item;
import com.optiplant.dashboard.dto.DemandResponse.Ranking;
import com.optiplant.dashboard.service.DemandRanking.Sold;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** RF-0046: ceil(N/2) high and floor(N/2) low below 10 products, 5 by end from 10 on, ties by SKU, one ranking per unit. */
class DemandRankingTest {
    @Test void threeProductsGoTwoHighAndOneLow() {
        Ranking ranking = DemandRanking.rank(List.of(sold("C", "10"), sold("A", "30"), sold("B", "20"))).getFirst();
        assertThat(skus(ranking.high())).containsExactly("A", "B");
        assertThat(skus(ranking.low())).containsExactly("C");
        assertThat(ranking.productsWithSales()).isEqualTo(3);
    }

    @Test void aSingleProductIsOnlyHighAndNoSalesInventNoGroup() {
        Ranking ranking = DemandRanking.rank(List.of(sold("A", "1"))).getFirst();
        assertThat(skus(ranking.high())).containsExactly("A");
        assertThat(ranking.low()).isEmpty();
        assertThat(DemandRanking.rank(List.of())).isEmpty();
    }

    @Test void tiesGoBySkuAscending() {
        Ranking ranking = DemandRanking.rank(List.of(sold("C", "5"), sold("B", "5"), sold("A", "5"))).getFirst();
        assertThat(skus(ranking.high())).containsExactly("A", "B");
        assertThat(skus(ranking.low())).containsExactly("C");
    }

    @Test void nineProductsSplitInHalvesWithTheExtraInHigh() {
        Ranking ranking = DemandRanking.rank(products(9)).getFirst();
        assertThat(skus(ranking.high())).containsExactly("P09", "P08", "P07", "P06", "P05");
        assertThat(skus(ranking.low())).containsExactly("P01", "P02", "P03", "P04");
    }

    @Test void fromTenProductsEachEndTakesFiveAndTheMiddleIsLeftOut() {
        Ranking ten = DemandRanking.rank(products(10)).getFirst();
        assertThat(skus(ten.high())).containsExactly("P10", "P09", "P08", "P07", "P06");
        assertThat(skus(ten.low())).containsExactly("P01", "P02", "P03", "P04", "P05");
        Ranking twelve = DemandRanking.rank(products(12)).getFirst();
        assertThat(skus(twelve.high())).containsExactly("P12", "P11", "P10", "P09", "P08");
        assertThat(skus(twelve.low())).containsExactly("P01", "P02", "P03", "P04", "P05");
    }

    @Test void differentBaseUnitsAreRankedApartAndTheSpellingOfTheUnitDoesNotSplitThem() {
        List<Ranking> rankings = DemandRanking.rank(List.of(sold("A", "9", "kg"), sold("B", "1", "Kg "), sold("C", "100", "unidad")));
        assertThat(rankings).hasSize(2);
        assertThat(skus(rankings.get(0).high())).containsExactly("A");
        assertThat(skus(rankings.get(0).low())).containsExactly("B");
        assertThat(rankings.get(1).baseUnitName()).isEqualTo("unidad");
        assertThat(skus(rankings.get(1).high())).containsExactly("C");
    }

    private static List<Sold> products(int count) {
        List<Sold> sold = new ArrayList<>(IntStream.rangeClosed(1, count).mapToObj(i -> sold("P%02d".formatted(i), Integer.toString(i))).toList());
        java.util.Collections.shuffle(sold, new java.util.Random(46));
        return sold;
    }

    private static List<String> skus(List<Item> items) {
        return items.stream().map(Item::sku).toList();
    }

    private static Sold sold(String sku, String quantity) {
        return sold(sku, quantity, "unidad");
    }

    private static Sold sold(String sku, String quantity, String unit) {
        return new Sold(UUID.randomUUID(), sku, sku, unit, new BigDecimal(quantity));
    }
}
