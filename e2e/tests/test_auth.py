from __future__ import annotations

import re

from playwright.sync_api import Page, expect

from e2e.tests.utils import wait_for_login_error


def test_login_page_renders_and_shows_form(page: Page) -> None:
    page.goto("/login.html")

    expect(page.get_by_role("heading", name="Sign in to continue.")).to_be_visible()
    expect(page.get_by_label("Username")).to_be_visible()
    expect(page.get_by_label("Password")).to_be_visible()
    expect(page.get_by_role("button", name="Sign in")).to_be_enabled()


def test_login_with_wrong_credentials_shows_error(page: Page) -> None:
    page.goto("/login.html")
    page.wait_for_function(
        "() => document.getElementById('csrf-token')?.value?.length > 0"
    )
    page.get_by_label("Username").fill("operator")
    page.get_by_label("Password").fill("wrong-password")
    page.get_by_role("button", name="Sign in").click()

    wait_for_login_error(page)


def test_login_with_correct_credentials_redirects_to_job_list(
    page: Page,
    test_username: str,
    test_password: str,
) -> None:
    page.goto("/login.html")
    page.wait_for_function(
        "() => document.getElementById('csrf-token')?.value?.length > 0"
    )
    page.get_by_label("Username").fill(test_username)
    page.get_by_label("Password").fill(test_password)
    page.get_by_role("button", name="Sign in").click()

    page.wait_for_url(re.compile(r".*/index\.html$"))
    expect(page.get_by_role("heading", name="Jobs")).to_be_visible()
    expect(page.get_by_role("heading", name="Job List")).to_be_visible()


def test_unauthenticated_api_jobs_access_redirects_browser_to_login(
    authenticated_page: Page,
    base_url: str,
) -> None:
    page = authenticated_page
    page.context.clear_cookies()

    with page.expect_response(
        lambda response: response.url == f"{base_url}/api/jobs" and response.status == 401
    ):
        page.get_by_role("button", name="Refresh List").click()

    page.wait_for_url(re.compile(r".*/login\.html\?reason=session$"))
    expect(page.get_by_role("button", name="Sign in")).to_be_visible()
