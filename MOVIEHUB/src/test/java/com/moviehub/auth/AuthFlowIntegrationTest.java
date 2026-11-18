package com.moviehub.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/** Runs against the docker-compose database; each test is rolled back. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowIntegrationTest {

    private static final String EMAIL = "flow-test@example.com";
    private static final String PASSWORD = "secret123";

    @Autowired
    private MockMvc mvc;

    private String register() throws Exception {
        String body = """
                {"firstName":"Jane","lastName":"Doe","email":"Flow-Test@Example.com","password":"%s"}
                """.formatted(PASSWORD);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
    }

    private String login(String password, int expectedStatus) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, password);
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static String refreshBody(String token) {
        return "{\"refreshToken\":\"" + token + "\"}";
    }

    @Test
    void registerLoginAndFetchCurrentUser() throws Exception {
        register();
        String accessToken = JsonPath.read(login(PASSWORD, 200), "$.accessToken");

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.firstName").value("Jane"));
    }

    @Test
    void rejectsDuplicateEmail() throws Exception {
        register();
        String body = """
                {"firstName":"J","lastName":"D","email":"%s","password":"secret123"}
                """.formatted(EMAIL);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRegistration() throws Exception {
        String body = """
                {"firstName":"","lastName":"Doe","email":"not-an-email","password":"short"}
                """;
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.firstName").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        register();
        String response = login("wrong-password1", 401);
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(response, "$.message"))
                .isEqualTo("Invalid email or password");
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCannotAccessAdminEndpoints() throws Exception {
        String accessToken = JsonPath.read(register(), "$.accessToken");
        mvc.perform(get("/api/admin/anything").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        String firstRefresh = JsonPath.read(register(), "$.refreshToken");

        String refreshed = mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON).content(refreshBody(firstRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondRefresh = JsonPath.read(refreshed, "$.refreshToken");

        // Re-using the first (now revoked) token is rejected...
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(firstRefresh)))
                .andExpect(status().isUnauthorized());
        // ...and revokes every session of the user, including the newest token.
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(secondRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String refreshToken = JsonPath.read(register(), "$.refreshToken");

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }
}
