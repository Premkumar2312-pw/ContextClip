package com.contextclip.dto;

import java.time.LocalDate;

public record AnalyticsActivityResponse(String date, long count) {

    public AnalyticsActivityResponse(LocalDate date, long count) {
        this(date != null ? date.toString() : null, count);
    }
}
