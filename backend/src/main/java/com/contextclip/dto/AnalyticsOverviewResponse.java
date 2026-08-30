package com.contextclip.dto;

public record AnalyticsOverviewResponse(
        long totalEntries,
        String mostUsedType,
        String mostUsedTechnology,
        String mostUsedCategory
) {}
