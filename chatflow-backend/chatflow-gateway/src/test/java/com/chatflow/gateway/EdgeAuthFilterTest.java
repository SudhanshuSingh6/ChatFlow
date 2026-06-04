package com.chatflow.gateway;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EdgeAuthFilterTest {

    private final JwtValidator validator = mock(JwtValidator.class);
    private final EdgeAuthFilter filter = new EdgeAuthFilter(validator);

    private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void rejectsProtectedRequestWithoutToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/ai/conversations/x/ask");
        MockHttpServletResponse res = run(req);
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsInvalidToken() throws Exception {
        when(validator.isValid("bad")).thenReturn(false);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/conversations");
        req.addHeader("Authorization", "Bearer bad");
        assertThat(run(req).getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void passesValidToken() throws Exception {
        when(validator.isValid("good")).thenReturn(true);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/conversations");
        req.addHeader("Authorization", "Bearer good");
        assertThat(run(req).getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void skipsPublicAuthEndpoints() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        // no token, but auth endpoints are public → not rejected
        assertThat(run(req).getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        verifyNoInteractions(validator);
    }
}
