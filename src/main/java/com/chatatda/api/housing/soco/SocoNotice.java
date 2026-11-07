package com.chatatda.api.housing.soco;

import java.time.LocalDate;

public record SocoNotice(long boardId, String title, LocalDate postedOn, String bodyText) {
}
