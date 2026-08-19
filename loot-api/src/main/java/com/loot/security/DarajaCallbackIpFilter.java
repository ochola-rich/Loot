package com.loot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Daraja doesn't sign its callbacks the way Flutterwave does (no header we
 * can verify), so the only integrity check available is restricting which
 * source IPs are trusted to call these endpoints, via
 * daraja.callback-allowed-ips (comma-separated CIDRs, e.g. Safaricom's
 * published callback ranges in staging/prod).
 *
 * Left unconfigured (the dev default), this fails open with a warning
 * rather than blocking every callback outright - Safaricom's sandbox
 * source IPs aren't fixed and local dev often fronts the callback through
 * a tunnel, so a hard default here would just break onboarding.
 */
public class DarajaCallbackIpFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DarajaCallbackIpFilter.class);

    private final List<String> allowedCidrs;

    public DarajaCallbackIpFilter(@Value("${daraja.callback-allowed-ips:}") String allowedIpsCsv) {
        this.allowedCidrs = allowedIpsCsv.isBlank()
                ? List.of()
                : Arrays.stream(allowedIpsCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/webhooks/mpesa/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (allowedCidrs.isEmpty()) {
            log.warn("daraja.callback-allowed-ips is not configured; accepting M-Pesa callback from {} "
                    + "without IP validation", request.getRemoteAddr());
            filterChain.doFilter(request, response);
            return;
        }

        String remoteIp = request.getRemoteAddr();
        boolean allowed = allowedCidrs.stream().anyMatch(cidr -> CidrMatcher.matches(cidr, remoteIp));
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Rejected M-Pesa callback from disallowed IP {}", remoteIp);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
