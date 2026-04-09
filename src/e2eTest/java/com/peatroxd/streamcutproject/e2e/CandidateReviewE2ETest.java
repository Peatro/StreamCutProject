package com.peatroxd.streamcutproject.e2e;

import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.open;

class CandidateReviewE2ETest extends E2ETestBase {

    @Test
    void readyForReviewJobShowsCandidateCards() {
        SeededJob job = createSeedJob("READY_FOR_REVIEW");

        login();
        open("/job.html?id=" + job.id());

        $$("h2").findBy(text("Candidate Review")).shouldBe(visible);
        $$("[data-candidate-card]").shouldHave(sizeGreaterThanOrEqual(2));
        $$("[data-candidate-action='approve']").first().shouldBe(visible);
    }

    @Test
    void jobWithNoCandidatesShowsEmptyState() {
        SeededJob job = createSeedJob("TRANSCRIBING");

        login();
        open("/job.html?id=" + job.id());

        $$("h2").findBy(text("Candidate Review")).shouldBe(visible);
        $(".empty-state").shouldBe(visible);
        $(".empty-state").shouldHave(text("No candidates available yet."));
    }
}
