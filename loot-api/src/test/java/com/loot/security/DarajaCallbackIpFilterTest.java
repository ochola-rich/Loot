package com.loot.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DarajaCallbackIpFilterTest {

    @Test
    void allowsAnyIpWhenNoAllowlistIsConfigured() throws Exception {
        DarajaCallbackIpFilter filter = new DarajaCallbackIpFilter("");
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = callbackRequest("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void allowsAnIpInsideTheConfiguredAllowlist() throws Exception {
        DarajaCallbackIpFilter filter = new DarajaCallbackIpFilter("196.201.214.0/24, 41.90.64.0/20");
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = callbackRequest("196.201.214.55");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsAnIpOutsideTheConfiguredAllowlistWith403() throws Exception {
        DarajaCallbackIpFilter filter = new DarajaCallbackIpFilter("196.201.214.0/24");
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = callbackRequest("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void skipsNonMpesaWebhookPaths() throws Exception {
        DarajaCallbackIpFilter filter = new DarajaCallbackIpFilter("196.201.214.0/24");
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.setRemoteAddr("8.8.8.8");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest callbackRequest(String remoteIp) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhooks/mpesa/confirmation");
        request.setRemoteAddr(remoteIp);
        return request;
    }
}
