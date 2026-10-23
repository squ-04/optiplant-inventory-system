package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@Import(BootstrapHttpTest.TimeConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.admin-code=integration-admin", "app.admin-password=temporary integration secret",
        "app.demo-data=true"})
class BootstrapHttpTest {
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
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void resetTime() { clock.now = Instant.now(); }

    @Test
    void securityProblemDetailsAreValidUtf8OnThePublicHttpInterface() throws Exception {
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(403);
        String body = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(response.body())).toString();
        assertThat(json.readTree(body).get("code").asText()).isEqualTo("CSRF_INVALID");
        assertThat(json.readTree(body).get("detail").asText()).isEqualTo("La solicitud no está autorizada.");
        assertThat(body).doesNotContain("\uFFFD");
    }

    @Test
    void roleAndBranchAreAuthorizedAndSensitiveChangesRevokeSessions() throws Exception {
        UUID branch = UUID.randomUUID();
        UUID otherBranch = UUID.randomUUID();
        createBranch(branch, "scope-a");
        createBranch(otherBranch, "scope-b");
        createAccount("scope-manager", "MANAGER", branch);
        createAccount("scope-operator", "OPERATOR", branch);
        Browser manager = authenticated("scope-manager");
        Browser operator = authenticated("scope-operator");
        assertThat(manager.get("/api/v1/bootstrap?branchExternalId=" + branch).statusCode()).isEqualTo(200);
        assertProblem(manager.get("/api/v1/bootstrap?branchExternalId=" + otherBranch), 403, "BRANCH_FORBIDDEN");
        assertThat(operator.get("/api/v1/bootstrap").statusCode()).isEqualTo(200);
        assertProblem(operator.get("/api/v1/bootstrap?view=management"), 403, "ROLE_FORBIDDEN");
        assertThat(manager.get("/api/v1/bootstrap?view=management").statusCode()).isEqualTo(200);
        assertProblem(manager.get("/api/v1/bootstrap?branchExternalId=not-a-uuid"), 400, "VALIDATION_FAILED");
        assertProblem(manager.get("/api/v1/bootstrap?branchExternalId=550e8400-e29b-11d4-a716-446655440000"),
            400, "VALIDATION_FAILED");
        jdbc.update("UPDATE identity_user_account SET role='OPERATOR' WHERE code='scope-manager'");
        assertProblem(manager.get("/api/v1/auth/me"), 401, "SESSION_REVOKED");
        jdbc.update("UPDATE identity_user_account SET active=false WHERE code='scope-operator'");
        assertProblem(operator.get("/api/v1/bootstrap"), 401, "SESSION_REVOKED");
        createAccount("scope-reassigned", "MANAGER", branch);
        Browser reassigned = authenticated("scope-reassigned");
        jdbc.update("UPDATE identity_user_account SET branch_id=(SELECT id FROM identity_branch WHERE external_id=?) WHERE code='scope-reassigned'", otherBranch);
        assertProblem(reassigned.get("/api/v1/auth/me"), 401, "SESSION_REVOKED");
    }

    @Test
    void pollingDoesNotExtendInactivityAndAbsoluteExpiryIsEnforced() throws Exception {
        createAccount("expiry-user", "ADMIN", null);
        Browser browser = authenticated("expiry-user");
        clock.advance(Duration.ofMinutes(29));
        assertThat(browser.get("/api/v1/bootstrap").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(1));
        assertProblem(browser.get("/api/v1/bootstrap"), 401, "SESSION_EXPIRED");
        browser = authenticated("expiry-user");
        browser.csrf();
        // A structurally valid password command is server-classified interaction,
        // even when the current credential is rejected; it never changes credentials.
        for (int i = 0; i < 16; i++) {
            clock.advance(Duration.ofMinutes(29));
            assertProblem(browser.post("/api/v1/auth/password", Map.of("currentPassword", "incorrect current password",
                "newPassword", "unchanged replacement password"), true), 400, "CURRENT_PASSWORD_INVALID");
        }
        clock.advance(Duration.ofMinutes(15));
        assertThat(browser.get("/api/v1/bootstrap").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(1));
        // Interactive inactivity is only 16 minutes here; this isolates absolute expiry.
        assertProblem(browser.get("/api/v1/bootstrap"), 401, "SESSION_EXPIRED");
    }

    @Test
    void passwordChangeRevokesOtherSessionsAndAcceptsBoundarySpaces() throws Exception {
        createAccount("password-user", "ADMIN", null);
        Browser first = authenticated("password-user");
        Browser second = authenticated("password-user");
        first.csrf();
        assertProblem(first.post("/api/v1/auth/password", Map.of("currentPassword", "fixture individual password",
            "newPassword", "x".repeat(129)), true), 400, "VALIDATION_FAILED");
        assertThat(second.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        assertThat(first.post("/api/v1/auth/password", Map.of("currentPassword", "fixture individual password",
            "newPassword", " ".repeat(15)), true).statusCode()).isEqualTo(204);
        assertProblem(second.get("/api/v1/auth/me"), 401, "SESSION_REVOKED");
        first.csrf();
        assertThat(first.post("/api/v1/auth/login", Map.of("code", "password-user", "password", " ".repeat(15)), true)
            .statusCode()).isEqualTo(200);
        first.csrf();
        assertThat(first.post("/api/v1/auth/password", Map.of("currentPassword", " ".repeat(15),
            "newPassword", "z".repeat(128)), true).statusCode()).isEqualTo(204);
        first.csrf();
        assertThat(first.post("/api/v1/auth/login", Map.of("code", "password-user", "password", "z".repeat(128)), true)
            .statusCode()).isEqualTo(200);
    }

    @Test
    void loginGuardHasFiveFailureWindowAndProgressiveTemporaryWaits() throws Exception {
        createAccount("guard-user", "ADMIN", null);
        Browser browser = new Browser();
        browser.csrf();
        for (int minutes : new int[] {5, 15, 30}) {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "guard-user",
                    "password", "incorrect individual password"), true), 401, "LOGIN_INVALID");
            }
            assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "guard-user",
                "password", "fixture individual password"), true), 429, "LOGIN_THROTTLED");
            clock.advance(Duration.ofMinutes(minutes));
        }
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", "guard-user",
            "password", "fixture individual password"), true).statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void rejectedCsrfAndInvalidInputDoNotConsumeLoginFailureBudget() throws Exception {
        createAccount("validation-user", "ADMIN", null);
        Browser browser = new Browser();
        for (int i = 0; i < 5; i++) {
            assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "validation-user",
                "password", "fixture individual password"), false), 403, "CSRF_INVALID");
        }
        browser.csrf();
        for (int i = 0; i < 5; i++) {
            assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "validation-user",
                "password", "short"), true), 400, "VALIDATION_FAILED");
        }
        for (int i = 0; i < 4; i++) {
            assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "validation-user",
                "password", "incorrect individual password"), true), 401, "LOGIN_INVALID");
        }
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", "validation-user",
            "password", "fixture individual password"), true).statusCode()).isEqualTo(200);
        // Login rotates the pre-authentication CSRF token.
        assertProblem(browser.post("/api/v1/auth/logout", Map.of(), true), 403, "CSRF_INVALID");
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        browser.csrf();
        assertProblem(browser.post("/api/v1/auth/password", Map.of("currentPassword", "fixture individual password",
            "newPassword", "replacement individual password"), false), 403, "CSRF_INVALID");
        assertProblem(browser.post("/api/v1/auth/logout", Map.of(), false), 403, "CSRF_INVALID");
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void failureWindowResetsAfterFifteenMinutesAndUnknownAccountsDoNotAuthenticate() throws Exception {
        createAccount("window-user", "ADMIN", null);
        Browser browser = new Browser();
        browser.csrf();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++) {
                assertProblem(browser.post("/api/v1/auth/login", Map.of("code", "window-user",
                    "password", "incorrect individual password"), true), 401, "LOGIN_INVALID");
            }
            clock.advance(Duration.ofMinutes(15));
        }
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", "window-user",
            "password", "fixture individual password"), true).statusCode()).isEqualTo(200);
        Browser anonymous = new Browser();
        anonymous.csrf();
        assertProblem(anonymous.post("/api/v1/auth/login", Map.of("code", "unknown-account",
            "password", "fixture individual password"), true), 401, "LOGIN_INVALID");
        assertProblem(anonymous.get("/api/v1/auth/me"), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void revokedVersionAndInactiveBranchInvalidateAccessWithoutLeakingProtectedData() throws Exception {
        createAccount("version-user", "ADMIN", null);
        Browser versioned = authenticated("version-user");
        jdbc.update("UPDATE identity_user_account SET credentials_version=credentials_version+1 WHERE code='version-user'");
        assertProblem(versioned.get("/api/v1/bootstrap"), 401, "SESSION_REVOKED");
        UUID branch = UUID.randomUUID();
        createBranch(branch, "disabled-branch");
        createAccount("disabled-branch-user", "MANAGER", branch);
        Browser scoped = authenticated("disabled-branch-user");
        jdbc.update("UPDATE identity_branch SET active=false WHERE external_id=?", branch);
        assertProblem(scoped.get("/api/v1/bootstrap"), 401, "SESSION_REVOKED");
        scoped.csrf();
        assertProblem(scoped.post("/api/v1/auth/login", Map.of("code", "disabled-branch-user",
            "password", "fixture individual password"), true), 401, "LOGIN_INVALID");
    }

    private void createBranch(UUID externalId, String code) {
        jdbc.update("INSERT INTO identity_branch(external_id, code, name) VALUES (?,?,?)", externalId, code, "Sucursal de prueba");
    }

    private void createAccount(String code, String role, UUID branch) {
        jdbc.update("""
            INSERT INTO identity_user_account(external_id,code,full_name,password_hash,role,branch_id,must_change_password)
            VALUES (?,?,?,?,?,(SELECT id FROM identity_branch WHERE external_id=?),false)
            """, UUID.randomUUID(), code, "Cuenta de prueba", passwords.encode("fixture individual password"), role, branch);
    }

    private Browser authenticated(String code) throws Exception {
        Browser browser = new Browser();
        browser.csrf();
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", code, "password", "fixture individual password"), true)
            .statusCode()).isEqualTo(200);
        return browser;
    }

    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock mutableClock() { return new MutableClock(); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.now();
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void csrfCorsSessionAndMandatoryPasswordFlow() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.get("/api/v1/bootstrap").statusCode()).isEqualTo(401);
        var noCsrf = browser.post("/api/v1/auth/login", Map.of("code", "integration-admin",
            "password", "temporary integration secret"), false);
        assertProblem(noCsrf, 403, "CSRF_INVALID");
        browser.csrf();
        var login = browser.post("/api/v1/auth/login", Map.of("code", "integration-admin",
            "password", "temporary integration secret"), true);
        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode user = json.readTree(login.body());
        assertThat(UUID.fromString(user.path("externalId").asText()).version()).isEqualTo(4);
        assertThat(user.path("role").asText()).isEqualTo("ADMIN");
        assertThat(user.path("mustChangePassword").asBoolean()).isTrue();
        assertThat(user.has("id")).isFalse();
        assertThat(user.has("passwordHash")).isFalse();
        assertThat(browser.cookies.getCookieStore().getCookies()).anySatisfy(cookie -> {
            assertThat(cookie.getName()).isEqualTo("SESSION");
            assertThat(cookie.isHttpOnly()).isTrue();
        });
        assertThat(login.headers().allValues("set-cookie").toString()).containsIgnoringCase("SameSite=Lax");
        assertThat(browser.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        assertProblem(browser.get("/api/v1/bootstrap"), 403, "PASSWORD_CHANGE_REQUIRED");
        browser.csrf();
        assertProblem(browser.post("/api/v1/auth/password", Map.of("currentPassword", "wrong password here",
            "newPassword", "new integration password"), true), 400, "CURRENT_PASSWORD_INVALID");
        assertProblem(browser.post("/api/v1/auth/password", Map.of("currentPassword", "temporary integration secret",
            "newPassword", "short"), true), 400, "VALIDATION_FAILED");
        assertThat(browser.get("/api/v1/auth/me").body()).contains("\"mustChangePassword\":true");
        assertThat(browser.post("/api/v1/auth/password", Map.of("currentPassword", "temporary integration secret",
            "newPassword", "new integration password"), true).statusCode()).isEqualTo(204);
        assertProblem(browser.get("/api/v1/auth/me"), 401, "AUTHENTICATION_REQUIRED");
        browser.csrf();
        assertThat(browser.post("/api/v1/auth/login", Map.of("code", "integration-admin",
            "password", "new integration password"), true).statusCode()).isEqualTo(200);
        browser.csrf();
        var bootstrap = browser.get("/api/v1/bootstrap");
        assertThat(bootstrap.statusCode()).isEqualTo(200);
        JsonNode dto = json.readTree(bootstrap.body());
        assertThat(UUID.fromString(dto.path("externalId").asText()).version()).isEqualTo(4);
        assertThat(Instant.parse(dto.path("occurredAt").asText())).isBeforeOrEqualTo(Instant.now());
        assertThat(dto.path("amount").isString()).isTrue();
        assertThat(dto.path("amount").asText()).isEqualTo("123456789012345678.123456789012345678");
        String sessionCookie = browser.cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("SESSION")).findFirst().orElseThrow().getValue();
        assertThat(browser.post("/api/v1/auth/logout", Map.of(), true).statusCode()).isEqualTo(204);
        assertProblem(browser.get("/api/v1/auth/me"), 401, "AUTHENTICATION_REQUIRED");
        var replay = HttpClient.newHttpClient().send(HttpRequest.newBuilder(browser.uri("/api/v1/auth/me"))
            .header("Cookie", "SESSION=" + sessionCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertProblem(replay, 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void publicHealthOpenApiAndRestrictiveCors() throws Exception {
        Browser browser = new Browser();
        assertThat(browser.get("/actuator/health").statusCode()).isEqualTo(200);
        var api = browser.get("/v3/api-docs");
        assertThat(api.statusCode()).isEqualTo(200);
        assertThat(api.body()).contains("/api/v1/auth/login", "/api/v1/bootstrap", "externalId", "occurredAt");
        assertThat(browser.get("/swagger-ui/index.html").statusCode()).isEqualTo(200);
        var allowed = browser.client.send(HttpRequest.newBuilder(browser.uri("/api/v1/auth/me"))
            .header("Origin", "http://localhost:5173").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        var preflight = browser.client.send(HttpRequest.newBuilder(browser.uri("/api/v1/auth/login"))
            .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type,x-csrf-token")
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        var rejected = browser.client.send(HttpRequest.newBuilder(browser.uri("/api/v1/auth/me"))
            .header("Origin", "http://untrusted.example").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertProblem(rejected, 403, "CORS_FORBIDDEN");
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo(code);
        assertThat(response.body()).doesNotContain("password_hash", "SQLException", "stackTrace");
    }

    class Browser {
        final CookieManager cookies = new CookieManager();
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(10)).build();
        String token;
        String header;

        URI uri(String path) { return URI.create("http://localhost:" + port + path); }
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response = get("/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            var body = json.readTree(response.body());
            token = body.path("token").asText();
            header = body.path("headerName").asText();
            assertThat(token).isNotBlank();
        }
        HttpResponse<String> post(String path, Map<String, String> body, boolean csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (csrf) request.header(header, token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
