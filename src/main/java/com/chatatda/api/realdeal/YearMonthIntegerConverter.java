package com.chatatda.api.realdeal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.YearMonth;

@Converter
public class YearMonthIntegerConverter implements AttributeConverter<YearMonth, Integer> {

    @Override
    public Integer convertToDatabaseColumn(YearMonth ym) {
        return ym == null ? null : ym.getYear() * 100 + ym.getMonthValue();
    }

    @Override
    public YearMonth convertToEntityAttribute(Integer value) {
        return value == null ? null : YearMonth.of(value / 100, value % 100);
    }
}
