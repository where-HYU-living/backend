package com.chatatda.api.listing;

import com.chatatda.api.transfer.TransferService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ListingGradeJob {

    private static final Logger log = LoggerFactory.getLogger(ListingGradeJob.class);

    private final ListingService listings;
    private final TransferService transfers;

    public ListingGradeJob(ListingService listings, TransferService transfers) {
        this.listings = listings;
        this.transfers = transfers;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        recompute();
    }

    /**
     * 판정의 실거래 표본 창(최근 12개월)은 달마다 한 칸씩 밀린다. 서버를 다시 켜지 않아도 목록 등급이 판결 카드와 같게
     * 매달 1일 새벽에 다시 계산한다.
     */
    @Scheduled(cron = "0 10 4 1 * *", zone = "Asia/Seoul")
    public void monthly() {
        recompute();
    }

    private void recompute() {
        try {
            log.info("매물 판정 다시 계산: {}건", listings.recomputeAll());
            log.info("양도 글 판정 다시 계산: {}건", transfers.recomputeAll());
        } catch (RuntimeException e) { // 그 사이 글이 고쳐져 낙관적 잠금에 걸리면 다음 달(또는 다음 재시작)에 다시
            log.warn("판정 다시 계산 실패: {}", e.getMessage());
        }
    }
}
