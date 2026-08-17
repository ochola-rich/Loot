package com.loot.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = requestWithKey("key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsThe101stRequestWithinAMinuteWith429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain filterChain = mock(FilterChain.class);

        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestWithKey("key-2"), response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse limitedResponse = new MockHttpServletResponse();
        filter.doFilter(requestWithKey("key-2"), limitedResponse, filterChain);

        assertThat(limitedResponse.getStatus()).isEqualTo(429);
        assertThat(limitedResponse.getHeader("Retry-After")).isEqualTo("60");
        verify(filterChain, times(100)).doFilter(any(), any());
    }

    @Test
    void tracksSeparateBucketsPerApiKey() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain filterChain = mock(FilterChain.class);

        for (int i = 0; i < 100; i++) {
            filter.doFilter(requestWithKey("exhausted-key"), new MockHttpServletResponse(), filterChain);
        }

        MockHttpServletResponse exhaustedResponse = new MockHttpServletResponse();
        filter.doFilter(requestWithKey("exhausted-key"), exhaustedResponse, filterChain);
        assertThat(exhaustedResponse.getStatus()).isEqualTo(429);

        MockHttpServletResponse freshResponse = new MockHttpServletResponse();
        filter.doFilter(requestWithKey("fresh-key"), freshResponse, filterChain);
        assertThat(freshResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsWebhookAndHealthPaths() throws Exception {
        RateLimitFilter filter = new RateLimitFilter();
        FilterChain filterChain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/webhooks/mpesa/confirmation");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest requestWithKey(String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/tournaments");
        request.addHeader("X-API-Key", apiKey);
        return request;
    }
}
