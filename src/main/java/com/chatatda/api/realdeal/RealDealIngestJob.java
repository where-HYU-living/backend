package com.chatatda.api.realdeal;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class RealDealIngestJob implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RealDealIngestJob.class);

    static final List<String> LAWD_CODES = List.of("11200", "11230");
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 50;

    public record Range(YearMonth from, YearMonth to) {
    }

    public record Report(int saved, int skipped, List<String> failures) {
    }

    private final MolitClient client;
    private final RealDealNormalizer normalizer;
    private final RealDealRepository repository;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean enabled;
    private final int months;

    public RealDealIngestJob(MolitClient client, RealDealNormalizer normalizer, RealDealRepository repository,
                             PlatformTransactionManager txManager, Clock clock,
                             @Value("${app.ingest.enabled}") boolean enabled,
                             @Value("${app.ingest.months}") int months) {
        this.client = client;
        this.normalizer = normalizer;
        this.repository = repository;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.enabled = enabled;
        this.months = months;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        Range range = defaultRange();
        log.info("실거래 수집 시작 {} ~ {}", range.from(), range.to());
        Report report = ingest(range.from(), range.to());
        log.info("실거래 수집 끝: 저장 {}건, 형식 오류로 제외 {}건, 실패 {}묶음", report.saved(), report.skipped(),
                report.failures().size());
        report.failures().forEach(f -> log.warn("수집 실패: {}", f));
    }

    public Range defaultRange() {
        YearMonth to = YearMonth.now(clock).minusMonths(1);
        return new Range(to.minusMonths(months - 1L), to);
    }

    public Report ingest(YearMonth from, YearMonth to) {
        int saved = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<>();
        for (String lawdCd : LAWD_CODES) {
            for (YearMonth ym = from; !ym.isAfter(to); ym = ym.plusMonths(1)) {
                for (RealDealType type : RealDealType.values()) {
                    try {
                        Batch batch = fetchAll(lawdCd, ym, type);
                        replace(lawdCd, ym, type, batch.deals());
                        saved += batch.deals().size();
                        skipped += batch.skipped();
                    } catch (RuntimeException e) {
                        failures.add(lawdCd + " " + ym + " " + type + ": " + e.getMessage());
                    }
                }
            }
        }
        return new Report(saved, skipped, failures);
    }

    private record Batch(List<RealDeal> deals, int skipped) {
    }

    private Batch fetchAll(String lawdCd, YearMonth ym, RealDealType type) {
        Instant now = clock.instant();
        List<RealDeal> deals = new ArrayList<>();
        int skipped = 0;
        int seen = 0;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            RealDealNormalizer.MolitPage page = normalizer.parse(client.fetch(lawdCd, ym, type, pageNo, PAGE_SIZE), type, now);
            deals.addAll(page.deals());
            skipped += page.skipped();
            seen += page.itemCount();
            if (page.itemCount() == 0 || seen >= page.totalCount()) {
                return new Batch(deals, skipped);
            }
        }
        throw new MolitApiException("페이지가 " + MAX_PAGES + "쪽을 넘어서 중단했어요.");
    }

    private void replace(String lawdCd, YearMonth ym, RealDealType type, List<RealDeal> deals) {
        tx.executeWithoutResult(status -> {
            repository.deleteBatch(lawdCd, type, ym);
            repository.saveAll(deals);
        });
    }
}
