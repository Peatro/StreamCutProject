from __future__ import annotations

import re

from playwright.sync_api import Locator, Page, expect


def job_row(page: Page, source_text: str) -> Locator:
    row = page.locator("tbody tr", has_text=source_text).first
    expect(row).to_be_visible()
    return row


def wait_for_status(page: Page, status: str) -> None:
    expect(page.locator(f'[data-status-pill="{status}"]').first).to_be_visible()


def wait_for_login_error(page: Page) -> None:
    page.wait_for_url(re.compile(r".*/login\.html\?.*error.*"))
    expect(page.locator("#status-message")).to_contain_text("Incorrect username or password.")
