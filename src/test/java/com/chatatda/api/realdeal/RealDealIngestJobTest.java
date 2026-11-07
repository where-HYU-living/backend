package com.chatatda.api.realdeal;

import static com.chatatda.api.realdeal.RealDealNormalizerTest.readXml;
import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.IntegrationTest;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class RealDealIngestJobTest extends IntegrationTest {

    private static final YearMonth OCT = YearMonth.of(2026, 10);

    @Autowired
    RealDealRepository repository;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void clean() {
        jdbc.execute("truncate table real_deals restart identity");
        calls.clear();
    }

    private RealDealIngestJob job(MolitClient client) {
        return new RealDealIngestJob(client, new RealDealNormalizer(), repository, txManager, clock, false, 12);
    }

    private MolitClient onlySeongdongOfficetel(String xmlName) {
        return (lawdCd, ym, type, pageNo, numOfRows) -> {
            calls.add(lawdCd + "/" + ym + "/" + type + "/" + pageNo);
            if (lawdCd.equals("11200") && type == RealDealType.OFFICETEL) {
                return readXml(xmlName);
            }
            return readXml("empty.xml");
        };
    }

    @Test
    void ingest_coversBothDistrictsAndAllTypes() {
        job(onlySeongdongOfficetel("invalid-rows.xml")).ingest(OCT, OCT);

        assertThat(calls).containsExactlyInAnyOrder(
                "11200/2026-10/OFFICETEL/1", "11200/2026-10/ROWHOUSE/1", "11200/2026-10/DETACHED/1",
                "11230/2026-10/OFFICETEL/1", "11230/2026-10/ROWHOUSE/1", "11230/2026-10/DETACHED/1");
    }

    @Test
    void ingest_isIdempotentForSameBatch() {
        var job = job(onlySeongdongOfficetel("invalid-rows.xml"));

        var first = job.ingest(OCT, OCT);
        var second = job.ingest(OCT, OCT);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(first.saved()).isEqualTo(1);
        assertThat(first.skipped()).isEqualTo(4);
        assertThat(second.saved()).isEqualTo(1);
    }

    @Test
    void ingest_followsPagesUntilTotalCount() {
        MolitClient paging = (lawdCd, ym, type, pageNo, numOfRows) -> {
            calls.add(lawdCd + "/" + type + "/" + pageNo);
            if (lawdCd.equals("11200") && type == RealDealType.OFFICETEL) {
                return readXml(pageNo == 1 ? "paging-page1.xml" : "paging-page2.xml");
            }
            return readXml("empty.xml");
        };

        job(paging).ingest(OCT, OCT);

        assertThat(repository.count()).isEqualTo(3);
        assertThat(calls).contains("11200/OFFICETEL/1", "11200/OFFICETEL/2").doesNotContain("11200/OFFICETEL/3");
    }

    @Test
    void ingest_failedBatchKeepsOldDataAndOthersContinue() {
        job(onlySeongdongOfficetel("invalid-rows.xml")).ingest(OCT, OCT);

        MolitClient failing = (lawdCd, ym, type, pageNo, numOfRows) -> {
            if (lawdCd.equals("11200") && type == RealDealType.OFFICETEL) {
                throw new MolitApiException("22: LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR.");
            }
            if (lawdCd.equals("11230") && type == RealDealType.ROWHOUSE) {
                return readXml("paging-page2.xml").replace("<totalCount>3</totalCount>", "<totalCount>1</totalCount>");
            }
            return readXml("empty.xml");
        };
        var report = job(failing).ingest(OCT, OCT);

        assertThat(report.failures()).singleElement().asString().contains("11200", "OFFICETEL", "2026-10");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void defaultRange_isTwelveMonthsEndingLastMonth() {
        var range = job(onlySeongdongOfficetel("empty.xml")).defaultRange();
        assertThat(range.from()).isEqualTo(YearMonth.of(2025, 11));
        assertThat(range.to()).isEqualTo(YearMonth.of(2026, 10));
    }
}
