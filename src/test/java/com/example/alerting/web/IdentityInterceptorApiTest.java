package com.example.alerting.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.alerting.identity.CurrentUser;
import com.example.alerting.identity.RequiresRole;
import com.example.alerting.identity.Role;
import com.example.alerting.support.ApiTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity and role handling for every {@code /api/v1} endpoint, tested with throw-away endpoints so that
 * the rules are proven independently of any real controller (docs/02 section 9; requirement R-19:
 * "identity stub with real ownership/role checks").
 *
 * <p>The authorization matrix: no identity is 401 everywhere; a user passes where no role or the USER role is
 * required; only an admin passes where ADMIN is required; a method-level role replaces a class-level one.
 */
@ApiTest
@Import({IdentityInterceptorApiTest.Endpoints.class, IdentityInterceptorApiTest.AdminController.class,
        IdentityInterceptorApiTest.OutsideTheApi.class})
class IdentityInterceptorApiTest {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    @RestController
    @RequestMapping("/api/v1/test")
    static class Endpoints {

        @GetMapping("/whoami")
        Map<String, Object> whoAmI(CurrentUser user) {
            return Map.of("id", user.id(), "email", user.email(), "displayName", user.displayName(),
                    "role", user.role().name());
        }

        @GetMapping("/for-users")
        @RequiresRole(Role.USER)
        Map<String, String> forUsers() {
            return Map.of("area", "users");
        }

        @GetMapping("/for-admins")
        @RequiresRole(Role.ADMIN)
        Map<String, String> forAdmins() {
            return Map.of("area", "admins");
        }
    }

    @RestController
    @RequestMapping("/api/v1/test-admin")
    @RequiresRole(Role.ADMIN)
    static class AdminController {

        @GetMapping("/inherited")
        Map<String, String> inherited() {
            return Map.of("rule", "class level");
        }

        @GetMapping("/relaxed")
        @RequiresRole(Role.USER)
        Map<String, String> relaxed() {
            return Map.of("rule", "method level");
        }
    }

    @RestController
    @RequestMapping("/outside-the-api")
    static class OutsideTheApi {

        @GetMapping("/whoami")
        Map<String, Object> whoAmI(CurrentUser user) {
            return Map.of("email", user.email());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theControllerReceivesTheIdentifiedUser() throws Exception {
        mockMvc.perform(get("/api/v1/test/whoami").header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice Demo"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    void anAdminIsIdentifiedWithTheAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/test/whoami").header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void everyEndpointNeedsAnIdentity() throws Exception {
        for (String path : new String[] {"/api/v1/test/whoami", "/api/v1/test/for-users", "/api/v1/test/for-admins",
                "/api/v1/test-admin/inherited", "/api/v1/test-admin/relaxed"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void anEndpointForUsersIsOpenToUsersAndAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/test/for-users").header("X-User-Id", ALICE)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/test/for-users").header("X-User-Id", ADMIN)).andExpect(status().isOk());
    }

    @Test
    void anEndpointForAdminsRefusesUsers() throws Exception {
        mockMvc.perform(get("/api/v1/test/for-admins").header("X-User-Id", ALICE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/test/for-admins").header("X-User-Id", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value("admins"));
    }

    @Test
    void aRoleOnTheClassAppliesToItsMethods() throws Exception {
        mockMvc.perform(get("/api/v1/test-admin/inherited").header("X-User-Id", ALICE)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/test-admin/inherited").header("X-User-Id", ADMIN)).andExpect(status().isOk());
    }

    @Test
    void aRoleOnAMethodReplacesTheOneOnTheClass() throws Exception {
        mockMvc.perform(get("/api/v1/test-admin/relaxed").header("X-User-Id", ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rule").value("method level"));
    }

    @Test
    void aControllerOutsideTheApiPathGetsNoIdentityAndFailsLoudlyWithoutLeakingWhy() throws Exception {
        mockMvc.perform(get("/outside-the-api/whoami").header("X-User-Id", ALICE))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }
}
