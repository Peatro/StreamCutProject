from __future__ import annotations

import os
import re
import shutil
import uuid
from pathlib import Path
from typing import Iterator

import pytest
import requests
from playwright.sync_api import Browser, BrowserContext, Page, Playwright, sync_playwright

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TEST_USER = "operator"
DEFAULT_TEST_PASSWORD = "operator-password"
E2E_TIMEOUT_MS = 10_000
ARTIFACTS_DIR = Path(__file__).resolve().parent / "artifacts"


class OperatorApiClient:
    def __init__(self, base_url: str, username: str, password: str) -> None:
        self.base_url = base_url.rstrip("/")
        self.username = username
        self.password = password
        self.session = requests.Session()
        self.session.headers.update({"Accept": "application/json"})
        self._login()

    def reset(self) -> None:
        response = self.session.post(
            self._url("/api/internal/e2e/reset"),
            headers=self._csrf_headers(),
            timeout=E2E_TIMEOUT_MS / 1000,
        )
        response.raise_for_status()

    def create_seeded_job(self, status: str, source_url: str | None = None) -> dict:
        payload: dict[str, str] = {"status": status}
        if source_url:
            payload["sourceUrl"] = source_url

        response = self.session.post(
            self._url("/api/internal/e2e/jobs"),
            headers=self._csrf_headers(),
            json=payload,
            timeout=E2E_TIMEOUT_MS / 1000,
        )
        response.raise_for_status()
        return response.json()

    def close(self) -> None:
        self.session.close()

    def _login(self) -> None:
        token = self._bootstrap_csrf()
        response = self.session.post(
            self._url("/login"),
            data={
                "username": self.username,
                "password": self.password,
                "_csrf": token,
            },
            allow_redirects=False,
            timeout=E2E_TIMEOUT_MS / 1000,
        )
        if response.status_code not in (302, 303) or response.headers.get("Location") != "/index.html":
            raise RuntimeError(
                f"Operator login failed with status {response.status_code}: {response.text}"
            )

    def _csrf_headers(self) -> dict[str, str]:
        return {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": self._bootstrap_csrf(),
        }

    def _bootstrap_csrf(self) -> str:
        response = self.session.get(
            self._url("/csrf"),
            timeout=E2E_TIMEOUT_MS / 1000,
        )
        response.raise_for_status()
        token = response.json().get("token")
        if not token:
            raise RuntimeError("CSRF bootstrap did not return a token.")
        return token

    def _url(self, path: str) -> str:
        return f"{self.base_url}{path}"


def pytest_runtest_makereport(item: pytest.Item, call: pytest.CallInfo[object]) -> None:
    outcome = yield
    report = outcome.get_result()
    setattr(item, f"rep_{report.when}", report)


pytest_runtest_makereport = pytest.hookimpl(hookwrapper=True)(pytest_runtest_makereport)


@pytest.fixture(scope="session")
def base_url() -> str:
    return os.getenv("STREAMCUT_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


@pytest.fixture(scope="session")
def test_username() -> str:
    return os.getenv("STREAMCUT_TEST_USER", DEFAULT_TEST_USER)


@pytest.fixture(scope="session")
def test_password() -> str:
    return os.getenv("STREAMCUT_TEST_PASSWORD", DEFAULT_TEST_PASSWORD)


@pytest.fixture(scope="session", autouse=True)
def clean_artifacts_dir() -> Iterator[Path]:
    if ARTIFACTS_DIR.exists():
        shutil.rmtree(ARTIFACTS_DIR)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    yield ARTIFACTS_DIR


@pytest.fixture(scope="session")
def playwright_instance() -> Iterator[Playwright]:
    with sync_playwright() as playwright:
        yield playwright


@pytest.fixture(scope="session")
def browser(playwright_instance: Playwright) -> Iterator[Browser]:
    browser = playwright_instance.chromium.launch(headless=True)
    yield browser
    browser.close()


@pytest.fixture
def context(browser: Browser, base_url: str) -> Iterator[BrowserContext]:
    context = browser.new_context(base_url=base_url)
    yield context
    context.close()


@pytest.fixture
def page(
    context: BrowserContext,
    clean_artifacts_dir: Path,
    request: pytest.FixtureRequest,
) -> Iterator[Page]:
    page = context.new_page()
    page.set_default_timeout(E2E_TIMEOUT_MS)
    yield page
    capture_failure_artifacts(page, clean_artifacts_dir, request)
    page.close()


@pytest.fixture
def api_client(base_url: str, test_username: str, test_password: str) -> Iterator[OperatorApiClient]:
    client = OperatorApiClient(base_url, test_username, test_password)
    yield client
    client.close()


@pytest.fixture(autouse=True)
def reset_test_state(api_client: OperatorApiClient) -> None:
    api_client.reset()


@pytest.fixture
def authenticated_page(
    page: Page,
    test_username: str,
    test_password: str,
) -> Page:
    page.goto("/login.html")
    page.wait_for_function(
        "() => document.getElementById('csrf-token')?.value?.length > 0"
    )
    page.get_by_label("Username").fill(test_username)
    page.get_by_label("Password").fill(test_password)
    page.get_by_role("button", name="Sign in").click()
    page.wait_for_url(re.compile(r".*/index\.html$"))
    page.wait_for_selector("h1")
    return page


@pytest.fixture
def unique_url_factory() -> callable:
    def factory(label: str = "job") -> str:
        return f"https://example.com/e2e/{label}/{uuid.uuid4()}"

    return factory


def capture_failure_artifacts(page: Page, artifacts_dir: Path, request: pytest.FixtureRequest) -> None:
    report = getattr(request.node, "rep_call", None)
    if report is None or not report.failed:
        return

    target_dir = artifacts_dir / sanitized_name(request.node.nodeid)
    target_dir.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(target_dir / "failure.png"), full_page=True)
    (target_dir / "page.html").write_text(page.content(), encoding="utf-8")
    (target_dir / "url.txt").write_text(page.url, encoding="utf-8")


def sanitized_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_.-]+", "_", value)
