package com.contextclip.controller;

import com.contextclip.dto.AnalyticsActivityResponse;
import com.contextclip.dto.AnalyticsCountResponse;
import com.contextclip.dto.AnalyticsOverviewResponse;
import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;
import com.contextclip.service.AnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserRepository userRepository;

    public AnalyticsController(AnalyticsService analyticsService, UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    @GetMapping("/overview")
    public ResponseEntity<AnalyticsOverviewResponse> getOverview(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(analyticsService.getOverview(user));
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<AnalyticsCountResponse>> getByType(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(analyticsService.getByType(user));
    }

    @GetMapping("/by-technology")
    public ResponseEntity<List<AnalyticsCountResponse>> getByTechnology(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(analyticsService.getByTechnology(user));
    }

    @GetMapping("/by-category")
    public ResponseEntity<List<AnalyticsCountResponse>> getByCategory(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(analyticsService.getByCategory(user));
    }

    @GetMapping("/activity")
    public ResponseEntity<List<AnalyticsActivityResponse>> getActivity(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(analyticsService.getActivity(user));
    }
}

