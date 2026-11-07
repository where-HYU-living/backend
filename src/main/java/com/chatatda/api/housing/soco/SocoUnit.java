package com.chatatda.api.housing.soco;

public record SocoUnit(String name, String roomKind, Double exclusiveAreaM2, long deposit, long rent, Long mgmtFee) {
}
