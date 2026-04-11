package com.peatroxd.streamcutproject.e2e;

import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

class OperatorControlsE2ETest extends E2ETestBase {

    @Test
    void forceFailButtonVisibleForStuckJob() {
        SeededJob job = createSeedJob("TRANSCRIBING");

        login();
        open("/job.html?id=" + job.id());

        $("[data-job-control='force-fail']").shouldBe(visible).click();
        $(".page-status").shouldHave(text("force-failed"));
        $(".page-header").shouldHave(text("FAILED"));
    }

    @Test
    void retryButtonVisibleForFailedJob() {
        SeededJob job = createSeedJob("FAILED");

        login();
        open("/job.html?id=" + job.id());

        $(".worker-runtime-summary").click();
        $("[data-job-control='retry']").shouldBe(visible).click();
        $(".page-status").shouldHave(text("requeued for download"));
        $(".page-header").shouldHave(text("QUEUED_FOR_DOWNLOAD"));
    }

    @Test
    void cancelButtonVisibleForQueuedJob() {
        SeededJob job = createSeedJob("QUEUED_FOR_DOWNLOAD");

        login();
        open("/job.html?id=" + job.id());

        $("[data-job-control='cancel']").shouldBe(visible).click();
        $(".page-status").shouldHave(text("was canceled"));
        $(".page-header").shouldHave(text("CANCELED"));
    }
}
