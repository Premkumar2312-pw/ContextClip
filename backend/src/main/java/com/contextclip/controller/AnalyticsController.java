package com.contextclip.controller;

import com.contextclip.dto.AnalyticsActivityResponse;
import com.contextclip.dto.AnalyticsCountResponse;
import com.contextclip.dto.AnalyticsOverviewResponse;
import com.contextclip.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview() {
        return ResponseEntity.ok(analyticsService.getOverview());
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<AnalyticsCountResponse>> getByType() {
        return ResponseEntity.ok(analyticsService.getByType());
    }

    @GetMapping("/by-technology")
    public ResponseEntity<List<AnalyticsCountResponse>> getByTechnology() {
        return ResponseEntity.ok(analyticsService.getByTechnology());
    }

    @GetMapping("/by-category")
    public ResponseEntity<List<AnalyticsCountResponse>> getByCategory() {
        return ResponseEntity.ok(analyticsService.getByCategory());
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AnalyticsActivityResponse>> getActivity() {
        return ResponseEntity.ok(analyticsService.getActivity());
    }
}
