package com.chatatda.api.pricing;

import java.time.YearMonth;
import java.util.List;

public record Sample(int count, String scope, List<String> dongs, Double areaMinM2, Double areaMaxM2,
                     YearMonth from, YearMonth to, Double medianPerM2) {
}
