# Frontend Redesign Handoff (для Claude Design)

Подготовка к редизайну фронта через **Claude Design** (claude.ai/design).
Цель: поменять внешний вид, не сломав поведение.

## 0. Главное про архитектуру (читать первым)

Фронт — статика из Spring Boot (`src/main/resources/static/`), 6 файлов:

| Файл | Что это |
|------|---------|
| `index.html`, `job.html` | **Пустые оболочки.** Только topbar + `<div data-page-root>`. Вся разметка вставляется из JS. |
| `login.html` | Самодостаточная страница со **встроенными** стилями и скриптом. Редизайнится отдельно и целиком. |
| `app.js` (~3000 строк) | Логика + **вся разметка страниц `jobs`/`job`** в виде template-строк. |
| `styles.css` (~2400 строк) | Все стили `jobs`/`job`. |

**Следствие:** редизайн `jobs`/`job` = правка `styles.css` (+ при смене классов — `app.js`). HTML-файлы трогать почти не надо.

**Самый ленивый и безопасный путь:** если новый дизайн сохраняет ту же DOM-структуру и имена классов — меняется **только `styles.css`**, `app.js` не трогаем вообще. Любое изменение разметки тянет за собой правку template-строк в `app.js`.

## 1. Рабочий процесс с Claude Design

1. Делать **по одной странице**: `login` → `jobs` → `job` (не «весь фронт одним промптом» — пережжёшь токены, у Claude Design это известная проблема).
2. В промпт класть **раздел 3 (токены)** этого файла + импортировать design-систему, чтобы он не уезжал со стиля.
3. Claude Design выдаёт статичный HTML/CSS. Из него берём **CSS** и переносим в `styles.css`, сохраняя имена классов из раздела 4.
4. Если Claude Design переименовал классы/структуру — либо переименовать обратно под контракт, либо синхронно поправить template-строки в `app.js`.
5. **Нельзя** отдавать `app.js` в Claude Design на «перегенерацию разметки» вслепую — там завязана логика (polling, превью-плеер, модерация, экспорт).

## 2. Что НЕЛЬЗЯ ломать — контракт хуков

### 2a. `data-*` атрибуты (поведенческие, сохранять ДОСЛОВНО)

`app.js` ищет элементы по ним. Удалишь/переименуешь — отвалится функция.

- Роутинг: `body[data-page]` = `"jobs"` | `"job"`; контейнер `data-page-root`
- Общее: `data-status-banner`, `data-live-indicator`
- Jobs-страница: `data-jobs-refresh`, `data-url-job-form`, `data-url-job-message`, `data-upload-job-form`, `data-upload-job-message`, `data-delete-job`, `data-complete-job`, `data-job-id`
- Bulk-удаление: `data-bulk-actions`, `data-bulk-select-all`, `data-bulk-delete`, `data-bulk-status`, `data-bulk-select`
- Job-страница: `data-page-refresh`, `data-job-control` (retry/cancel/force-fail/complete/delete)
- Кандидаты: `data-candidate-card`, `data-candidate-id`, `data-candidate-action` (approve/reject/download), `data-candidate-message`, `data-export-ready`, `data-shortcut`
- Превью-плеер: `data-preview-shell`, `data-preview-state`, `data-preview-video`, `data-preview-start-sec`, `data-preview-end-sec`, `data-preview-controls`, `data-preview-play`, `data-preview-play-icon`, `data-preview-scrubber`, `data-preview-time`, `data-preview-note`
- Пагинация кандидатов: `data-candidate-page-target`
- Лента событий: `data-event-filters`, `data-event-feed`, `data-event-filter-control`, `data-active`, `data-event-item`, `data-event-categories`
- Диалог подтверждения: `data-confirm-cancel`, `data-confirm-ok`

### 2b. Классы/селекторы, которые JS тоже использует (сохранять или править JS)

- `.pill` — перерисовывается при approve/reject
- `.candidate-summary`, `.candidate-meta` (+ `span:first-child` внутри неё)
- `.runtime-state`, `.runtime-progress` — заменяются при смене статуса экспорта
- `.confirm-overlay`, `.confirm-backdrop`, `.confirm-panel`
- `.upload-hint`
- `.is-selected` (toggle на карточке кандидата с клавиатуры)
- Селекторы по имени: `input[name='file']`, `input[name='url']`, `button[type='submit']`
- login.html (свой скрипт): id `csrf-token`, `status-message`, `login-form`, `username-input`, `password-input`, `remember-me-input`; поле `_csrf`; форма `action="/login"`

### 2c. Бэкенд-контракт (не трогать)

- API-эндпоинты: `/api/jobs*`, `/api/candidates/{id}/{approve|reject|export}`, `/api/exports/{id}*`, `/csrf`, `/login`, `/health`
- Видео-превью тянет `/api/jobs/{id}/source/stream`, скачивание — `/api/exports/{id}/file`
- POST/DELETE требуют заголовок `X-XSRF-TOKEN` (логика уже в `app.js`, не трогать)

## 3. Дизайн-токены (вставлять в промпт Claude Design)

Источник истины: `styles.css` `:root`. Linear-подобная тёмная тема, шрифт Inter, `font-feature-settings: "cv01","ss03"`.

```css
--bg:#08090a; --bg-panel:#0f1011; --bg-elevated:#191a1b; --bg-hover:rgba(255,255,255,.04);
--surface:rgba(255,255,255,.02); --surface-strong:rgba(255,255,255,.04); --surface-stronger:rgba(255,255,255,.05);
--text:#f7f8f8; --text-secondary:#d0d6e0; --text-muted:#8a8f98; --text-faint:#62666d;
--accent:#5e6ad2; --accent-bright:#7170ff; --accent-hover:#828fff;
--success:#10b981; --success-strong:#27a644; --warning:#d0a45c; --danger:#f06a6a;
--line-subtle:rgba(255,255,255,.05); --line:rgba(255,255,255,.08); --line-strong:#23252a;
--shadow-focus:0 0 0 1px rgba(113,112,255,.45),0 0 0 4px rgba(113,112,255,.14);
--radius-sm:6px; --radius-md:8px; --radius-lg:12px; --radius-xl:22px; --radius-pill:9999px;
```

Фон body: сетка 40×40 + два радиальных свечения accent сверху и слева-сверху. Кнопки: `.action-button` с модификаторами `-primary`/`-approve`/`-reject`/`-neutral`/`-export`. Статусы — `.pill.status-<lowercase>` с иконкой.

## 4. Компонентный инвентарь (что предстоит перерисовать)

**login** (отдельная страница): eyebrow-бейдж, h1, форма (username/password/remember-me), notice-баннер (tone warning/error), footer.

**jobs**: topbar; page-header + header-card (Queue Summary + Refresh); `stat-grid` (4 stat-card: Jobs/Active/Awaiting Review/Finished, у Active live-dot); panel «Job List» → таблица (`renderJobsTable`) с bulk-actions-bar, статус-pill, компактный прогресс-бар в ячейке; panel «Create Job» (URL-форма + upload-форма с drag&drop).

**job**: page-status баннер; page-header (live-indicator + status-pill + header-card); failure-summary (если FAILED/CANCELED); `job-console-grid` = main + side; main: panel «Job Details» (info-grid), `worker-runtime-panel` (прогресс + heartbeat + micro-cards + кнопки управления), `execution-history-panel`; side (sticky): «Job Events» (event-hero, phase-grid, filter-bar, timeline, raw-events details); panel «Candidate Review» → карточки кандидатов (`<details>`: summary со score-bar + pill; body с встроенным видео-превью-плеером, excerpt, runtime-state, кнопки approve/reject/download); пагинация кандидатов.

Состояния-классы для стилизации: `.is-active`, `.is-live`, `.is-selected`, `.is-latest`; превью `data-preview-state`=loading/ready/playing/paused/error; heartbeat `worker-heartbeat-<fresh|stale|stalled|missing|idle>`; тона `tone-success|accent|danger|muted`.

## 5. Чек после переноса

Минимальная проверка, что контракт цел (по одной на каждую критичную фичу):
- jobs: создать URL-job, создать upload-job (drag&drop), bulk-select + delete, live-indicator «дышит»
- job: approve/reject кандидата (pill меняется без перезагрузки), превью играет и не выходит за окно клипа, export & download, retry/cancel/complete/delete по статусу, фильтры событий
- login: неверный пароль → notice error; logout → notice warning
