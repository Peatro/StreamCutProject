package com.peatroxd.streamcutproject.e2e;

import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

class JobWorkflowE2ETest extends E2ETestBase {

    @Test
    void authenticatedUserSeesJobList() {
        login();

        $$("h1").findBy(text("Jobs")).shouldBe(visible);
        $$("h2").findBy(text("Job List")).shouldBe(visible);
    }

    @Test
    void submitUrlJobAppearsInList() {
        String sourceUrl = "https://example.com/video";

        login();
        $("#job-url-input").setValue(sourceUrl);
        $("[data-url-job-form] button[type='submit']").click();

        $("tbody").shouldHave(text(sourceUrl));
        $("tbody").shouldHave(text("QUEUED_FOR_DOWNLOAD"));
    }

    @Test
    void jobDetailPageShowsStatusAndEvents() {
        SeededJob job = createSeedJob("TRANSCRIBING");

        login();
        open("/job.html?id=" + job.id());

        $(".page-header").shouldHave(text("TRANSCRIBING"));
        $$("h2").findBy(text("Job Events")).shouldBe(visible);
        $("[data-event-feed]").shouldBe(visible);
    }
}
