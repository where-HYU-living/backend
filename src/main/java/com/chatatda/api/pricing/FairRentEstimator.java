package com.chatatda.api.pricing;

import com.chatatda.api.realdeal.DongAdjacency;
import com.chatatda.api.realdeal.RealDeal;
import com.chatatda.api.realdeal.RealDealRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FairRentEstimator {

    private final RealDealRepository deals;
    private final DongAdjacency adjacency;
    private final PricingProperties properties;
    private final PriceCalculator calculator;

    public FairRentEstimator(RealDealRepository deals, DongAdjacency adjacency, PricingProperties properties) {
        this.deals = deals;
        this.adjacency = adjacency;
        this.properties = properties;
        this.calculator = new PriceCalculator(properties);
    }

    @Transactional(readOnly = true)
    public Sample sampleFor(String dong, double areaM2, YearMonth asOf) {
        YearMonth from = asOf.minusMonths(properties.periodMonths() - 1L);
        double minArea = round2(areaM2 * (1 - properties.areaTolerance()));
        double maxArea = round2(areaM2 * (1 + properties.areaTolerance()));

        List<RealDeal> sameDong = deals.findSampleDeals(List.of(dong), from, asOf, minArea, maxArea);
        if (sameDong.size() >= properties.minSample()) {
            return sample(sameDong, "DONG", List.of(dong), minArea, maxArea, from, asOf);
        }

        Set<String> expanded = new LinkedHashSet<>();
        expanded.add(dong);
        expanded.addAll(adjacency.neighbors(dong));
        List<RealDeal> withNeighbors = deals.findSampleDeals(expanded, from, asOf, minArea, maxArea);
        if (withNeighbors.size() >= properties.minSample()) {
            List<String> present = expanded.stream()
                    .filter(d -> withNeighbors.stream().anyMatch(x -> x.getDong().equals(d)))
                    .toList();
            return sample(withNeighbors, "ADJACENT", present, minArea, maxArea, from, asOf);
        }

        return new Sample(withNeighbors.size(), "NONE", List.of(), null, null, null, null, null);
    }

    private Sample sample(List<RealDeal> rows, String scope, List<String> dongs, double minArea, double maxArea,
                          YearMonth from, YearMonth to) {
        List<Double> perM2 = new ArrayList<>(rows.size());
        for (RealDeal d : rows) {
            long converted = calculator.convertedRent(d.getDeposit(), d.getRent(), null, properties.conversionRate());
            perM2.add(converted / d.getAreaM2());
        }
        return new Sample(rows.size(), scope, dongs, minArea, maxArea, from, to, median(perM2));
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
