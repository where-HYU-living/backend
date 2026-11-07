package com.chatatda.api.listing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class ListingGradeJob {

    private static final Logger log = LoggerFactory.getLogger(ListingGradeJob.class);

    private final ListingService listings;

    public ListingGradeJob(ListingService listings) {
        this.listings = listings;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("매물 판정 다시 계산: {}건", listings.recomputeAll());
    }
}
