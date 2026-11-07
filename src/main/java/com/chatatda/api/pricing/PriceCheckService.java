package com.chatatda.api.pricing;

import com.chatatda.api.user.Budget;
import com.chatatda.api.user.ProfileService;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PriceCheckService {

    public record SampleView(int count, String scope, List<String> dongs, Double areaMinM2, Double areaMaxM2,
                             YearMonth from, YearMonth to) {

        static SampleView of(Sample s) {
            return new SampleView(s.count(), s.scope(), s.dongs(), s.areaMinM2(), s.areaMaxM2(), s.from(), s.to());
        }
    }

    public record BudgetView(Long rentCap, Long depositCap, Long overRent, Long overDeposit) {
    }

    public record PriceCheckDto(long convertedRent, Long fairRent, Long monthlyExcess, int contractMonths,
                                Long totalExcess, Double excessRate, Grade grade, double conversionRate,
                                SampleView sample, BudgetView budget) {
    }

    private final FairRentEstimator estimator;
    private final PriceCalculator calculator;
    private final ProfileService profiles;
    private final Clock clock;

    public PriceCheckService(FairRentEstimator estimator, PricingProperties properties, ProfileService profiles,
                             Clock clock) {
        this.estimator = estimator;
        this.calculator = new PriceCalculator(properties);
        this.profiles = profiles;
        this.clock = clock;
    }

    public PriceResult evaluate(PriceInput input) {
        YearMonth asOf = YearMonth.now(clock).minusMonths(1);
        return calculator.evaluate(input, estimator.sampleFor(input.dong(), input.areaM2(), asOf));
    }

    public PriceCheckDto check(PriceInput input, Long userIdOrNull) {
        PriceResult r = evaluate(input);
        BudgetView budget = userIdOrNull == null ? null
                : profiles.budget(userIdOrNull).map(b -> budgetView(b, r.convertedRent(), input.deposit())).orElse(null);
        return new PriceCheckDto(r.convertedRent(), r.fairRent(), r.monthlyExcess(), r.contractMonths(),
                r.totalExcess(), r.excessRate(), r.grade(), r.conversionRate(), SampleView.of(r.sample()), budget);
    }

    private static BudgetView budgetView(Budget b, long convertedRent, long deposit) {
        Long overRent = b.rentCap() == null ? null : Math.max(0, convertedRent - b.rentCap());
        Long overDeposit = b.depositCap() == null ? null : Math.max(0, deposit - b.depositCap());
        return new BudgetView(b.rentCap(), b.depositCap(), overRent, overDeposit);
    }
}
