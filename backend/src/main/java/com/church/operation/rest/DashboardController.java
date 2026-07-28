package com.church.operation.rest;

import com.church.operation.dto.DashboardResponse;
import com.church.operation.entity.Member;
import com.church.operation.service.DashboardService;
import com.church.operation.config.ChurchTimeZoneProperties;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;
    private final ChurchTimeZoneProperties timeZoneProperties;

    public DashboardController(DashboardService dashboardService, ChurchTimeZoneProperties timeZoneProperties) {
        this.dashboardService = dashboardService;
        this.timeZoneProperties = timeZoneProperties;
    }

    @GetMapping
    DashboardResponse dashboard(Authentication authentication) {
        return dashboardService.getDashboard(
            (Member) authentication.getPrincipal(), LocalDate.now(timeZoneProperties.zoneId())
        );
    }
}
