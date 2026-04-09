package com.peatroxd.streamcutproject.e2e;

import com.codeborne.selenide.WebDriverConditions;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static com.codeborne.selenide.Condition.attributeMatching;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.webdriver;
import static org.assertj.core.api.Assertions.assertThat;

class AuthE2ETest extends E2ETestBase {

    @Test
    void loginPageRendersForm() {
        open("/login.html");

        $("#login-form").shouldBe(visible);
        $("#username-input").shouldBe(visible);
        $("#password-input").shouldBe(visible);
        $("#login-form button[type='submit']").shouldBe(visible);
    }

    @Test
    void wrongCredentialsShowError() {
        open("/login.html");

        $("#csrf-token").shouldHave(attributeMatching("value", ".+"));
        $("#username-input").setValue("wrong-user");
        $("#password-input").setValue("wrong-password");
        $("#login-form button[type='submit']").click();

        $("#status-message").shouldBe(visible).shouldHave(text("Invalid username or password"));
    }

    @Test
    void correctCredentialsRedirectToJobList() {
        login();

        webdriver().shouldHave(WebDriverConditions.urlContaining("/index.html"));
        $$("h2").findBy(text("Job List")).shouldBe(visible);
    }

    @Test
    void unauthenticatedApiAccessRedirects() {
        HttpResponse<String> response = unauthenticatedGet("/api/jobs");

        assertThat(response.statusCode()).isIn(401, 302, 303);
        if (response.statusCode() != 401) {
            assertThat(response.headers().firstValue("Location")).hasValueSatisfying(location ->
                    assertThat(location).contains("/login.html"));
        }
    }
}
