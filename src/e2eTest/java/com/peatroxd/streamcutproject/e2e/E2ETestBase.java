package com.peatroxd.streamcutproject.e2e;

import com.codeborne.selenide.Configuration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.codeborne.selenide.Condition.attributeMatching;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;
import static org.assertj.core.api.Assertions.assertThat;

abstract class E2ETestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpClient httpClient;
    private URI baseUri;
    private String username;
    private String password;
    private String csrfToken;

    @BeforeEach
    void setUpE2e() {
        baseUri = URI.create(System.getProperty("selenide.baseUrl", "http://localhost:8080"));
        username = System.getProperty("e2e.username", "operator");
        password = System.getProperty("e2e.password", "operator-password");
        csrfToken = null;

        Configuration.baseUrl = baseUri.toString();
        Configuration.browser = System.getProperty("selenide.browser", "chrome");
        Configuration.browserSize = System.getProperty("selenide.browserSize", "1440x900");
        Configuration.headless = Boolean.parseBoolean(System.getProperty("selenide.headless", "true"));
        Configuration.timeout = Long.getLong("selenide.timeout", 10_000L);

        closeWebDriver();

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .cookieHandler(cookieManager)
                .build();

        resetState();
    }

    @AfterEach
    void tearDownE2e() {
        closeWebDriver();
    }

    protected void login() {
        open("/login.html");
        $("#login-form").shouldBe(visible);
        $("#csrf-token").shouldHave(attributeMatching("value", ".+"));
        $("#username-input").setValue(username);
        $("#password-input").setValue(password);
        $("#login-form button[type='submit']").click();
        $$("h1").findBy(text("Jobs")).shouldBe(visible);
    }

    protected SeededJob createSeedJob(String status) {
        return createSeedJob(status, null);
    }

    protected SeededJob createSeedJob(String status, String sourceUrl) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        if (sourceUrl != null) {
            payload.put("sourceUrl", sourceUrl);
        }

        JsonNode response = postJson("/api/internal/e2e/jobs", payload, 201);
        return new SeededJob(
                response.path("id").asLong(),
                response.path("status").asText(),
                response.path("sourceUrl").isMissingNode() ? null : response.path("sourceUrl").asText()
        );
    }

    protected HttpResponse<String> unauthenticatedGet(String path) {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request);
    }

    protected void resetState() {
        postJson("/api/internal/e2e/reset", null, 204);
    }

    private JsonNode postJson(String path, Object body, int expectedStatus) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("Authorization", basicAuthHeader())
                .header("X-XSRF-TOKEN", csrfToken())
                .POST(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8));

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        HttpResponse<String> response = send(builder.build());
        assertThat(response.statusCode())
                .withFailMessage("Expected HTTP %s from %s but got %s with body: %s",
                        expectedStatus, path, response.statusCode(), response.body())
                .isEqualTo(expectedStatus);

        if (expectedStatus == 204) {
            return OBJECT_MAPPER.nullNode();
        }
        return readJson(response.body());
    }

    private String csrfToken() {
        if (csrfToken != null) {
            return csrfToken;
        }

        HttpRequest request = HttpRequest.newBuilder(resolve("/csrf"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        assertThat(response.statusCode())
                .withFailMessage("Expected /csrf to return 200 but got %s", response.statusCode())
                .isEqualTo(200);

        csrfToken = readJson(response.body()).path("token").asText();
        assertThat(csrfToken).isNotBlank();
        return csrfToken;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("HTTP call failed for " + request.uri(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP call interrupted for " + request.uri(), ex);
        }
    }

    private URI resolve(String path) {
        return baseUri.resolve(path);
    }

    private String basicAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode readJson(String body) {
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse JSON response: " + body, ex);
        }
    }

    private static String writeJson(Object body) {
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to serialize JSON payload", ex);
        }
    }

    protected record SeededJob(long id, String status, String sourceUrl) {
    }
}
