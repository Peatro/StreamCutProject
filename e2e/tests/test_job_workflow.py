from __future__ import annotations

from playwright.sync_api import Page, expect

from e2e.tests.utils import job_row, wait_for_status


def test_authenticated_user_sees_job_list_page(authenticated_page: Page) -> None:
    page = authenticated_page

    expect(page.get_by_role("heading", name="Jobs")).to_be_visible()
    expect(page.get_by_role("heading", name="Job List")).to_be_visible()
    expect(page.get_by_role("heading", name="Create Job")).to_be_visible()


def test_submit_url_job_via_form_job_appears_in_list(
    authenticated_page: Page,
    unique_url_factory,
) -> None:
    page = authenticated_page
    source_url = unique_url_factory("submit-url")

    page.get_by_label("Create from URL").fill(source_url)
    page.get_by_role("button", name="Create URL Job").click()

    expect(page.locator("[data-status-banner]")).to_contain_text("created from URL")
    expect(job_row(page, source_url)).to_contain_text("QUEUED_FOR_DOWNLOAD")


def test_submit_invalid_url_shows_error(authenticated_page: Page) -> None:
    page = authenticated_page

    page.get_by_label("Create from URL").fill("ftp://example.com/video")
    page.get_by_role("button", name="Create URL Job").click()

    expect(page.locator("[data-url-job-message]")).to_contain_text("url must use http or https")


def test_job_detail_page_accessible_and_shows_status_and_events_section(
    authenticated_page: Page,
    api_client,
    unique_url_factory,
) -> None:
    seeded_job = api_client.create_seeded_job(
        "QUEUED_FOR_DOWNLOAD",
        source_url=unique_url_factory("detail"),
    )
    page = authenticated_page

    page.goto(f"/job.html?id={seeded_job['id']}")

    wait_for_status(page, "QUEUED_FOR_DOWNLOAD")
    expect(page.get_by_role("heading", name="Job Events")).to_be_visible()
    expect(page.get_by_text("Execution History")).to_be_visible()
