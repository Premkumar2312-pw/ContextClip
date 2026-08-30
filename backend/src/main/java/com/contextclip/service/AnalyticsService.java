package com.contextclip.service;

import com.contextclip.dto.AnalyticsActivityResponse;
import com.contextclip.dto.AnalyticsCountResponse;
import com.contextclip.dto.AnalyticsOverviewResponse;
import com.contextclip.repository.ClipboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsService {

    private final ClipboardRepository clipboardRepository;

    public AnalyticsService(ClipboardRepository clipboardRepository) {
        this.clipboardRepository = clipboardRepository;
    }

    public AnalyticsOverviewResponse getOverview() {
        long totalEntries = clipboardRepository.count();
        if (totalEntries == 0) {
            return new AnalyticsOverviewResponse(0, null, null, null);
        }

        List<AnalyticsCountResponse> byType = clipboardRepository.countGroupedByType();
        List<AnalyticsCountResponse> byTechnology = clipboardRepository.countGroupedByTechnology();
        List<AnalyticsCountResponse> byCategory = clipboardRepository.countGroupedByCategory();

        String mostUsedType = byType.isEmpty() ? null : byType.get(0).name();
        String mostUsedTechnology = byTechnology.isEmpty() ? null : byTechnology.get(0).name();
        String mostUsedCategory = byCategory.isEmpty() ? null : byCategory.get(0).name();

        return new AnalyticsOverviewResponse(totalEntries, mostUsedType, mostUsedTechnology, mostUsedCategory);
    }

    public List<AnalyticsCountResponse> getByType() {
        return clipboardRepository.countGroupedByType();
    }

    public List<AnalyticsCountResponse> getByTechnology() {
        return clipboardRepository.countGroupedByTechnology();
    }

    public List<AnalyticsCountResponse> getByCategory() {
        return clipboardRepository.countGroupedByCategory();
    }

    public List<AnalyticsActivityResponse> getActivity() {
        return clipboardRepository.countDailyActivity();
    }
}
