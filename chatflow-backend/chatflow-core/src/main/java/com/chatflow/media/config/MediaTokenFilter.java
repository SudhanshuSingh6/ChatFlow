package com.chatflow.media.config;

import com.chatflow.media.storage.MediaUrlSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Locale;

/**
 * Gates {@code /media/**} on a valid HMAC token ({@code ?exp=&t=}) minted by {@link MediaUrlSigner},
 * so media URLs are self-authorizing (no bearer header) and time-limited. {@code /media/**} is
 * permitted in SecurityConfig; this filter — not the bearer chain — is the real gate, and it runs
 * before the static resource handler serves bytes.
 *
 * <p>Any malformed/unauthorized request yields {@code 401} (never {@code 500}). Path handling is
 * canonicalized defensively (traversal rejected) as defense-in-depth on top of the resource
 * resolver and {@code LocalMediaStorageService.resolve()}.
 */
@Component
@RequiredArgsConstructor
public class MediaTokenFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/media/";

    private final UrlPathHelper pathHelper = new UrlPathHelper();
    private final MediaUrlSigner urlSigner;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !pathHelper.getPathWithinApplication(request).startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = extractAndValidateKey(request);
        if (key == null) {
            unauthorized(response);
            return;
        }

        long exp;
        try {
            exp = Long.parseLong(request.getParameter("exp"));
        } catch (NumberFormatException ex) {  // missing or non-numeric exp
            unauthorized(response);
            return;
        }

        if (!urlSigner.verify(key, exp, request.getParameter("t"))) {
            unauthorized(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Returns the storage key under {@code /media/}, or {@code null} if absent/illegal. */
    private String extractAndValidateKey(HttpServletRequest request) {
        // Reject encoded traversal before any decoding.
        String rawUri = request.getRequestURI().toLowerCase(Locale.ROOT);
        if (rawUri.contains("%2e") || rawUri.contains("%2f") || rawUri.contains("%5c")) {
            return null;
        }

        String path = pathHelper.getPathWithinApplication(request);
        if (!path.startsWith(PREFIX)) {
            return null;
        }
        String key = path.substring(PREFIX.length());

        if (!StringUtils.hasText(key)
                || key.contains("..")
                || key.contains("\\")
                || key.startsWith("/")
                || key.indexOf('\0') >= 0) {
            return null;
        }
        return key;
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Invalid or expired media token\"}");
    }
}
