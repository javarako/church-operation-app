package com.church.operation.filter;

import com.church.operation.service.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MaintenanceModeFilter extends OncePerRequestFilter {
    private final MaintenanceModeService maintenanceMode;

    public MaintenanceModeFilter(MaintenanceModeService maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (maintenanceMode.isActive() && isBlockedMutation(request)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"code\":\"MAINTENANCE_MODE\",\"message\":\"Database maintenance is in progress.\"}"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlockedMutation(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return false;
        }
        return !request.getRequestURI().startsWith("/api/admin/data-management/restore/");
    }
}
