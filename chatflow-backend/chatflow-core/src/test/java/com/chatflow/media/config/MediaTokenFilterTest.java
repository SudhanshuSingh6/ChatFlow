package com.chatflow.media.config;

import com.chatflow.media.storage.MediaUrlSigner;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MediaTokenFilterTest {

    private static final String KEY = "image/2026/05/x.jpg";

    private final MediaUrlSigner signer = new MediaUrlSigner("test-media-signing-secret-at-least-32-chars");
    private final MediaTokenFilter filter = new MediaTokenFilter(signer);

    @Test
    void validTokenPassesThrough() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        MockHttpServletRequest req = mediaRequest(KEY);
        req.setParameter("exp", Long.toString(exp));
        req.setParameter("t", signer.sign(KEY, exp));

        MockFilterChain chain = run(req);

        assertThat(chain.getRequest()).isNotNull(); // chain proceeded
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        MockHttpServletResponse resp = runExpectingBlock(mediaRequest(KEY));
        assertThat(resp.getStatus()).isEqualTo(401);
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        long exp = Instant.now().getEpochSecond() - 10;
        MockHttpServletRequest req = mediaRequest(KEY);
        req.setParameter("exp", Long.toString(exp));
        req.setParameter("t", signer.sign(KEY, exp));

        assertThat(runExpectingBlock(req).getStatus()).isEqualTo(401);
    }

    @Test
    void tamperedKeyIsUnauthorized() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        // Token minted for KEY, but the request asks for a different object.
        MockHttpServletRequest req = mediaRequest("image/2026/05/other.jpg");
        req.setParameter("exp", Long.toString(exp));
        req.setParameter("t", signer.sign(KEY, exp));

        assertThat(runExpectingBlock(req).getStatus()).isEqualTo(401);
    }

    @Test
    void literalTraversalPathIsUnauthorized() throws Exception {
        MockHttpServletRequest req = mediaRequest("../secret");
        long exp = Instant.now().getEpochSecond() + 3600;
        req.setParameter("exp", Long.toString(exp));
        req.setParameter("t", signer.sign("../secret", exp));

        assertThat(runExpectingBlock(req).getStatus()).isEqualTo(401);
    }

    @Test
    void encodedTraversalPathIsUnauthorized() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/media/%2e%2e/secret");
        req.setRequestURI("/media/%2e%2e/secret");

        assertThat(runExpectingBlock(req).getStatus()).isEqualTo(401);
    }

    @Test
    void malformedExpIsUnauthorizedNotServerError() throws Exception {
        MockHttpServletRequest req = mediaRequest(KEY);
        req.setParameter("exp", "not-a-number");
        req.setParameter("t", "whatever");

        assertThat(runExpectingBlock(req).getStatus()).isEqualTo(401);
    }

    // --- helpers ---

    private MockHttpServletRequest mediaRequest(String key) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/media/" + key);
        req.setRequestURI("/media/" + key);
        return req;
    }

    private MockFilterChain run(MockHttpServletRequest req) throws ServletException, IOException {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return chain;
    }

    private MockHttpServletResponse runExpectingBlock(MockHttpServletRequest req)
            throws ServletException, IOException {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, resp, chain);
        assertThat(chain.getRequest()).isNull(); // chain did NOT proceed
        return resp;
    }
}
