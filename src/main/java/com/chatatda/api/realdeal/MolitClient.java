package com.chatatda.api.realdeal;

import java.time.YearMonth;

@FunctionalInterface
public interface MolitClient {

    String fetch(String lawdCd, YearMonth ym, RealDealType type, int pageNo, int numOfRows);
}
