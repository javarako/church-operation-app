package com.church.operation.filter;

import com.church.operation.service.MaintenanceModeService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MaintenanceModeFilterTest {
    private final MaintenanceModeService maintenanceMode = new MaintenanceModeService();
    private final MaintenanceModeFilter filter = new MaintenanceModeFilter(maintenanceMode);
    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void blocksMutationsOutsideRestoreEndpointsDuringMaintenance() throws Exception {
        maintenanceMode.enable();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/members");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString()).contains("MAINTENANCE_MODE");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void permitsHealthStatusRestoreAndNormalRequests() throws Exception {
        maintenanceMode.enable();
        assertPasses(new MockHttpServletRequest("GET", "/actuator/health"));
        assertPasses(new MockHttpServletRequest("GET", "/api/admin/data-management/restore/op-1"));
        assertPasses(new MockHttpServletRequest("POST", "/api/admin/data-management/restore/op-1/execute"));

        maintenanceMode.disable();
        assertPasses(new MockHttpServletRequest("POST", "/api/members"));
    }

    private void assertPasses(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
}
