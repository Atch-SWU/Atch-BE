package com.swu.atch.config;

import com.swu.atch.support.IsolatedAuthIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class CorsSecurityIntegrationTest extends IsolatedAuthIntegrationTest {
    private static final String ORIGIN = "https://atch-fe.vercel.app";

    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void allowedPreflightDoesNotRequireAuthentication(String method) throws Exception {
        mvc.perform(options("/api/v1/users/me/nickname")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", method)
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString(method)))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")))
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("content-type")))
                .andExpect(header().string("Access-Control-Max-Age", "600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.invalid", "https://preview.atch-fe.vercel.app", "http://localhost:5173", "null"})
    void otherOriginsAreRejected(String origin) throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void unapprovedHeaderIsRejected() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Unapproved-Header"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void allowedOriginDoesNotBypassBearerAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/users/me/nickname").header("Origin", ORIGIN)
                        .contentType("application/json").content("{\"nickname\":\"엣취\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void invalidBearerStillReturnsCorsHeaders() throws Exception {
        mvc.perform(patch("/api/v1/users/me/nickname").header("Origin", ORIGIN)
                        .header("Authorization", "Bearer invalid-test-token")
                        .contentType("application/json").content("{\"nickname\":\"엣취\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void publicApiValidationFailureReturnsCorsHeadersWithoutSendingEmail() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/code").header("Origin", ORIGIN)
                        .contentType("application/json").content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void requestsWithoutOriginKeepExistingAuthentication() throws Exception {
        mvc.perform(patch("/api/v1/users/me/nickname")
                        .contentType("application/json").content("{\"nickname\":\"엣취\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
