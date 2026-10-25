package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringJUnitConfig
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=integration-admin", "app.admin-password=temporary integration secret"})
class UserManagementHttpTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11")
        .withInitScript("db/test_roles.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "optiplant_test_runtime");
        registry.add("spring.datasource.password", () -> "test-runtime-not-for-deployment");
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private String suffix;

    @BeforeEach
    void fixtures() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        createAccount("admin-" + suffix, "ADMIN", null);
    }

    @Test
    void administratorHasGlobalScopeWithOrWithoutAnOptionalBranch() throws Exception {
        UUID branch = createBranch("optional-" + suffix);
        Browser admin = authenticated("admin-" + suffix);
        var result = admin.post("/api/v1/users", Map.of("code", "global-" + suffix,
            "fullName", "Global administrator", "role", "ADMIN", "branchExternalId", branch,
            "temporaryPassword", "individual global password " + suffix));
        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(json.readTree(result.body()).path("branchExternalId").asText()).isEqualTo(branch.toString());
    }

    @Test
    void administratorManagesUserLifecycleWithoutExposingCredentialsOrInternalKeys() throws Exception {
        UUID firstBranch = createBranch("first-" + suffix);
        UUID secondBranch = createBranch("second-" + suffix);
        Browser admin = authenticated("admin-" + suffix);
        String initialPassword = "individual temporary password " + suffix;

        var created = admin.post("/api/v1/users", Map.of("code", "manager-" + suffix,
            "fullName", "Manager " + suffix, "role", "MANAGER", "branchExternalId", firstBranch,
            "temporaryPassword", initialPassword));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode createdUser = json.readTree(created.body());
        UUID externalId = UUID.fromString(createdUser.path("externalId").asText());
        assertThat(createdUser.path("code").asText()).isEqualTo("manager-" + suffix);
        assertThat(createdUser.path("fullName").asText()).isEqualTo("Manager " + suffix);
        assertThat(createdUser.path("role").asText()).isEqualTo("MANAGER");
        assertThat(createdUser.path("branchExternalId").asText()).isEqualTo(firstBranch.toString());
        assertThat(createdUser.path("active").asBoolean()).isTrue();
        assertThat(createdUser.path("mustChangePassword").asBoolean()).isTrue();
        assertThat(created.body()).doesNotContain("password", "Hash", "\"id\"");

        assertThat(admin.get("/api/v1/users").body()).contains(externalId.toString(), "manager-" + suffix);
        assertThat(admin.get("/api/v1/users/" + externalId).body()).contains("Manager " + suffix);
        Browser managedUser = authenticated("manager-" + suffix, initialPassword);

        var updated = admin.post("/api/v1/users/" + externalId + "/update", Map.of("code", "operator-" + suffix,
            "fullName", "Operator " + suffix, "role", "OPERATOR", "branchExternalId", secondBranch));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json.readTree(updated.body()).path("externalId").asText()).isEqualTo(externalId.toString());
        assertThat(json.readTree(updated.body()).path("branchExternalId").asText()).isEqualTo(secondBranch.toString());
        assertProblem(managedUser.get("/api/v1/auth/me"), 401, "SESSION_REVOKED");
        Browser reassignedUser = authenticated("operator-" + suffix, initialPassword);

        var deactivated = admin.post("/api/v1/users/" + externalId + "/deactivate", Map.of());
        assertThat(deactivated.statusCode()).isEqualTo(204);
        assertProblem(reassignedUser.get("/api/v1/auth/me"), 401, "SESSION_REVOKED");
        JsonNode inactive = json.readTree(admin.get("/api/v1/users/" + externalId).body());
        assertThat(inactive.path("externalId").asText()).isEqualTo(externalId.toString());
        assertThat(inactive.path("active").asBoolean()).isFalse();
        Browser login = new Browser();
        login.csrf();
        assertProblem(login.post("/api/v1/auth/login", Map.of("code", "operator-" + suffix,
            "password", initialPassword)), 401, "LOGIN_INVALID");
    }

    @Test
    void rejectsInvalidOrUnknownPayloadsAndDuplicateCodesWithoutChangingExistingUsers() throws Exception {
        UUID branch = createBranch("validation-" + suffix);
        Browser admin = authenticated("admin-" + suffix);
        Map<String, Object> valid = Map.of("code", "inactive-" + suffix, "fullName", "Inactive " + suffix,
            "role", "MANAGER", "branchExternalId", branch, "temporaryPassword", "individual password " + suffix);
        var created = admin.post("/api/v1/users", valid);
        UUID inactiveId = UUID.fromString(json.readTree(created.body()).path("externalId").asText());
        assertThat(admin.post("/api/v1/users/" + inactiveId + "/deactivate", Map.of()).statusCode()).isEqualTo(204);
        assertProblem(admin.post("/api/v1/users", valid), 409, "USER_CODE_CONFLICT");

        var other = admin.post("/api/v1/users", Map.of("code", "other-" + suffix, "fullName", "Other " + suffix,
            "role", "ADMIN", "temporaryPassword", "another individual password " + suffix));
        UUID otherId = UUID.fromString(json.readTree(other.body()).path("externalId").asText());
        assertProblem(admin.post("/api/v1/users/" + otherId + "/update", Map.of("code", "inactive-" + suffix,
            "fullName", "Changed", "role", "ADMIN")), 409, "USER_CODE_CONFLICT");
        assertThat(json.readTree(admin.get("/api/v1/users/" + otherId).body()).path("code").asText())
            .isEqualTo("other-" + suffix);

        assertProblem(admin.postRaw("/api/v1/users", "{\"code\":\"x\",\"fullName\":\"Name\",\"role\":\"ADMIN\",\"temporaryPassword\":\"individual password value\",\"active\":false}"),
            400, "VALIDATION_FAILED");
        assertProblem(admin.postRaw("/api/v1/users", "{\"code\":\"x\",\"fullName\":\"Name\",\"role\":[\"ADMIN\",\"MANAGER\"],\"temporaryPassword\":\"individual password value\"}"),
            400, "VALIDATION_FAILED");
        assertProblem(admin.postRaw("/api/v1/users", "{\"code\":\"x\",\"fullName\":\"Name\",\"temporaryPassword\":\"individual password value\"}"),
            400, "VALIDATION_FAILED");
        assertProblem(admin.postRaw("/api/v1/users", "{\"code\":\"x\",\"fullName\":\"Name\",\"role\":\"MANAGER\",\"branchExternalId\":[\"" + branch + "\",\"" + UUID.randomUUID() + "\"],\"temporaryPassword\":\"individual password value\"}"),
            400, "VALIDATION_FAILED");
        assertProblem(admin.postRaw("/api/v1/users", "{\"code\":\"   \",\"fullName\":\"Name\",\"role\":\"ADMIN\",\"temporaryPassword\":\"individual password value\"}"),
            400, "VALIDATION_FAILED");
    }

    @Test
    void managerAndOperatorCannotEnumerateOrMutateUsersEvenWithGuessedIdentifiers() throws Exception {
        UUID branch = createBranch("forbidden-" + suffix);
        createAccount("manager-" + suffix, "MANAGER", branch);
        createAccount("operator-" + suffix, "OPERATOR", branch);
        Browser manager = authenticated("manager-" + suffix);
        Browser operator = authenticated("operator-" + suffix);
        UUID guessed = UUID.randomUUID();

        assertProblem(manager.get("/api/v1/users"), 403, "ROLE_FORBIDDEN");
        assertProblem(operator.get("/api/v1/users/" + guessed), 403, "ROLE_FORBIDDEN");
        assertProblem(manager.post("/api/v1/users/" + guessed + "/deactivate", Map.of()), 403, "ROLE_FORBIDDEN");
        Browser admin = authenticated("admin-" + suffix);
        assertThat(admin.get("/api/v1/users").body()).doesNotContain(guessed.toString());
    }

    @Test
    void acceptsTemporaryPasswordBoundariesIncludingSpacesWithoutReturningThem() throws Exception {
        Browser admin = authenticated("admin-" + suffix);
        var spaces = admin.post("/api/v1/users", Map.of("code", "spaces-" + suffix, "fullName", "Spaces " + suffix,
            "role", "ADMIN", "temporaryPassword", " ".repeat(15)));
        assertThat(spaces.statusCode()).isEqualTo(201);
        Browser spaceUser = authenticated("spaces-" + suffix, " ".repeat(15));
        assertThat(spaceUser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        var maximum = admin.post("/api/v1/users", Map.of("code", "maximum-" + suffix, "fullName", "Maximum " + suffix,
            "role", "ADMIN", "temporaryPassword", "x".repeat(128)));
        assertThat(maximum.statusCode()).isEqualTo(201);
        assertThat(maximum.body()).doesNotContain("x".repeat(128));
        assertProblem(admin.post("/api/v1/users", Map.of("code", "short-" + suffix, "fullName", "Short " + suffix,
            "role", "ADMIN", "temporaryPassword", "x".repeat(14))), 400, "VALIDATION_FAILED");
        String reusedCode = "individual-code-" + suffix;
        assertProblem(admin.post("/api/v1/users", Map.of("code", reusedCode, "fullName", "Reuse " + suffix,
            "role", "ADMIN", "temporaryPassword", reusedCode)), 400, "VALIDATION_FAILED");
    }

    private UUID createBranch(String code) {
        UUID externalId = UUID.randomUUID();
        jdbc.update("INSERT INTO identity_branch(external_id, code, name) VALUES (?,?,?)", externalId, code, "Sucursal de prueba");
        return externalId;
    }

    private void createAccount(String code, String role, UUID branch) {
        jdbc.update("""
            INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password)
            VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)
            """, UUID.randomUUID(), code, "Cuenta de prueba", passwords.encode("fixture individual password"), role, branch);
    }

    private Browser authenticated(String code) throws Exception { return authenticated(code, "fixture individual password"); }

    private Browser authenticated(String code, String password) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", code, "password", password)).statusCode()).isEqualTo(200);
        browser.csrf();
        return browser;
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
    }

    class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header;
        URI uri(String path) { return URI.create("http://localhost:" + port + path); }
        HttpResponse<String> get(String path) throws Exception { return send(path, null); }
        void csrf() throws Exception {
            JsonNode body = json.readTree(get("/api/v1/auth/csrf").body());
            token = body.path("token").asText();
            header = body.path("headerName").asText();
        }
        HttpResponse<String> post(String path, Object body) throws Exception { return send(path, json.writeValueAsString(body)); }
        HttpResponse<String> postRaw(String path, String body) throws Exception { return send(path, body); }
        private HttpResponse<String> send(String path, String body) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15));
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").header(header, token).POST(HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
