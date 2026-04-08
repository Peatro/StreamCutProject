from __future__ import annotations

from playwright.sync_api import Page, expect

from e2e.tests.utils import job_row, wait_for_status


def test_force_fail_transcribing_job_from_ui(
    authenticated_page: Page,
    api_client,
    unique_url_factory,
) -> None:
    seeded_job = api_client.create_seeded_job(
        "TRANSCRIBING",
        source_url=unique_url_factory("force-fail"),
    )
    page = authenticated_page

    page.goto(f"/job.html?id={seeded_job['id']}")
    wait_for_status(page, "TRANSCRIBING")
    page.locator('[data-job-control="force-fail"]').click()

    wait_for_status(page, "FAILED")
    expect(page.locator("[data-status-banner]")).to_contain_text("force-failed")


def test_retry_failed_job_from_ui(
    authenticated_page: Page,
    api_client,
    unique_url_factory,
) -> None:
    seeded_job = api_client.create_seeded_job(
        "FAILED",
        source_url=unique_url_factory("retry"),
    )
    page = authenticated_page

    page.goto(f"/job.html?id={seeded_job['id']}")
    wait_for_status(page, "FAILED")
    page.locator('[data-job-control="retry"]').click()

    wait_for_status(page, "QUEUED_FOR_DOWNLOAD")
    expect(page.locator("[data-status-banner]")).to_contain_text("requeued for download")


def test_cancel_queued_job_from_ui(
    authenticated_page: Page,
    unique_url_factory,
) -> None:
    page = authenticated_page
    source_url = unique_url_factory("cancel")

    page.get_by_label("Create from URL").fill(source_url)
    page.get_by_role("button", name="Create URL Job").click()
    job_row(page, source_url).get_by_role("link").click()

    wait_for_status(page, "QUEUED_FOR_DOWNLOAD")
    page.locator('[data-job-control="cancel"]').click()

    wait_for_status(page, "CANCELED")
    expect(page.locator("[data-status-banner]")).to_contain_text("was canceled")
