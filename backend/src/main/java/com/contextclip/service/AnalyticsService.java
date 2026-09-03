package com.contextclip.service;

import com.contextclip.dto.AnalyticsActivityResponse;
import com.contextclip.dto.AnalyticsCountResponse;
import com.contextclip.dto.AnalyticsOverviewResponse;
import com.contextclip.model.User;
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
        return getOverview(null);
    }

    public AnalyticsOverviewResponse getOverview(User user) {
        long totalEntries = user == null ? clipboardRepository.count() : clipboardRepository.countByUser(user);
        if (totalEntries == 0) {
            return new AnalyticsOverviewResponse(0, null, null, null);
        }

        List<AnalyticsCountResponse> byType = user == null ? clipboardRepository.countGroupedByType() : clipboardRepository.countGroupedByType(user);
        List<AnalyticsCountResponse> byTechnology = user == null ? clipboardRepository.countGroupedByTechnology() : clipboardRepository.countGroupedByTechnology(user);
        List<AnalyticsCountResponse> byCategory = user == null ? clipboardRepository.countGroupedByCategory() : clipboardRepository.countGroupedByCategory(user);

        String mostUsedType = byType.isEmpty() ? null : byType.get(0).name();
        String mostUsedTechnology = byTechnology.isEmpty() ? null : byTechnology.get(0).name();
        String mostUsedCategory = byCategory.isEmpty() ? null : byCategory.get(0).name();

        return new AnalyticsOverviewResponse(totalEntries, mostUsedType, mostUsedTechnology, mostUsedCategory);
    }

    public List<AnalyticsCountResponse> getByType() {
        return getByType(null);
    }

    public List<AnalyticsCountResponse> getByType(User user) {
        return user == null ? clipboardRepository.countGroupedByType() : clipboardRepository.countGroupedByType(user);
    }

    public List<AnalyticsCountResponse> getByTechnology() {
        return getByTechnology(null);
    }

    public List<AnalyticsCountResponse> getByTechnology(User user) {
        return user == null ? clipboardRepository.countGroupedByTechnology() : clipboardRepository.countGroupedByTechnology(user);
    }

    public List<AnalyticsCountResponse> getByCategory() {
        return getByCategory(null);
    }

    public List<AnalyticsCountResponse> getByCategory(User user) {
        return user == null ? clipboardRepository.countGroupedByCategory() : clipboardRepository.countGroupedByCategory(user);
    }

    public List<AnalyticsActivityResponse> getActivity() {
        return getActivity(null);
    }

    public List<AnalyticsActivityResponse> getActivity(User user) {
        return user == null ? clipboardRepository.countDailyActivity() : clipboardRepository.countDailyActivity(user);
    }
}

