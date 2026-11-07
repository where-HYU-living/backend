package com.chatatda.api.housing.soco;

import java.util.List;

public record SocoHomeDetail(Double lat, Double lng, String phone, String homepage, List<SocoUnit> units,
                             List<String> floorplanImageUrls) {
}
