> Status: historical planning context
>
> This file is preserved for earlier planning context.
> It should not be used as the active source of truth for current backlog, roadmap, architecture, or runtime behavior.
> In particular, the top-N / sliding-window scoring model described below has been superseded: the current
> highlight pipeline is a recall-oriented LLM detector feeding an arithmetic fusion of orthogonal voters,
> rank-all (no top-N cut). The authoritative reference is `Documentation/STREAMCUT_ARCHITECTURE_BRIEF.md`.

Отлично. Значит не распыляемся.

## Выбор зафиксирован

**Формат: self-hosted web app**  
**Подход: backend orchestration + async worker pipeline**  
**Разработка: агентная, но под жёстким контролем архитектуры**

И это правильный ход. Десктоп сейчас был бы лишним жиром. Веб даёт тебе:

- локальный запуск через Docker
    
- путь к VPS
    
- нормальную модульность
    
- возможность потом превратить это в сервис
    

---

# 1. Что именно мы строим

## Рабочее название

**VOD Highlight Assistant**

## Цель MVP

Сервис не делает “магические вирусные клипы”.  
Он делает **предварительный отбор кандидатов** из длинного VOD.

## Что умеет MVP

1. принимает VOD-ссылку или файл
    
2. скачивает или сохраняет видео
    
3. извлекает аудио
    
4. делает транскрипцию
    
5. определяет тишину
    
6. считает метрики по скользящим окнам
    
7. формирует кандидаты на клипы
    
8. даёт вручную модерировать кандидаты
    
9. экспортирует выбранный фрагмент
    

## Что НЕ делает MVP

- не считает virality score
    
- не делает умный reframe как Opus
    
- не определяет юмор
    
- не собирает клип из нескольких разрозненных кусков
    
- не публикует в TikTok/Reels автоматически
    

Вот это важно. Иначе агент тебе радостно нагенерит космолёт без двигателя.

---

# 2. Продуктовые роли агентов

С агентами главная ошибка — дать им “сделай сервис”.  
Тогда они размазывают архитектуру, плодят сущности и начинают устраивать карнавал из overengineering.

Тебе нужен **разделённый контур ответственности**.

## Набор агентных ролей

### 1. Architect Agent

Отвечает только за:

- bounded contexts
    
- API contracts
    
- data flow
    
- правила интеграции между Java и Python
    

Ему нельзя давать писать всё подряд.

---

### 2. Backend Agent

Отвечает за:

- Spring Boot API
    
- job orchestration
    
- PostgreSQL entities
    
- статус обработки
    
- модерацию кандидатов
    
- экспорт клипов
    

---

### 3. Worker Agent

Отвечает за:

- Python pipeline
    
- ffmpeg integration
    
- whisper/faster-whisper
    
- silence detection
    
- scoring metrics
    
- candidate generation
    

---

### 4. Frontend Agent

Отвечает за:

- минимальный web UI
    
- job list
    
- job details
    
- candidate moderation
    
- clip preview/download
    

---

### 5. QA/Review Agent

Отвечает за:

- acceptance checklist
    
- edge cases
    
- API contract consistency
    
- non-happy path
    
- smoke tests
    

---

### 6. Refactor Agent

Только после фичи.  
Его задача:

- убрать мусор
    
- выровнять naming
    
- упростить код
    
- не добавлять “улучшения ради улучшений”
    

---

# 3. Архитектура системы

## High-level

```text
Web UI
  ->
Spring Boot API
  ->
PostgreSQL

Spring Boot API
  ->
Job Queue / Task Dispatch
  ->
Python Worker

Python Worker
  ->
FFmpeg
  ->
Whisper/Faster-Whisper
  ->
Metrics + Candidate Generation

Artifacts
  ->
Local storage / MinIO
```

---

# 4. Архитектурный стиль

## Основа

**Modular monolith + external worker**

Это лучший вариант для тебя.

### Почему не микросервисы

Потому что микросервисы на старте — это способ заняться DevOps-ролёвкой вместо продукта.

### Почему не один процесс

Потому что Python-аудио/видео стек и Java orchestration лучше развести.

---

# 5. Технологический стек

## Backend

- Java 21
    
- Spring Boot
    
- Spring Web
    
- Spring Data JPA
    
- PostgreSQL
    
- Liquibase
    
- Validation
    
- OpenAPI/Swagger
    
- MapStruct
    
- Lombok
    

## Worker

- Python 3.11+
    
- FastAPI или просто worker entrypoint
    
- faster-whisper
    
- ffmpeg
    
- pydantic
    
- numpy/pandas по необходимости
    

## Frontend

Для MVP:

- **обычный server-side rendered UI или очень тонкий frontend**
    
- Thymeleaf **можно**
    
- либо простейший React/Vite фронт, если хочешь отделить UI
    

### Мой совет

На MVP бери:

- либо Thymeleaf
    
- либо простой React без цирка
    

Если хочешь быстрее и проще — **Thymeleaf нормален**.  
Если хочешь чуть современнее и потом удобнее развивать — **React**.

Но не делай фронт отдельной религией.

## Infra

- Docker Compose
    
- Nginx позже
    
- MinIO опционально
    
- локальная папка для артефактов на MVP
    

---

# 6. Модули backend

## 1. `vod-job`

Ответственность:

- создание job
    
- статусы
    
- чтение списка job-ов
    
- получение деталей job
    

## 2. `vod-source`

Ответственность:

- source type
    
- URL/file metadata
    
- download request info
    

## 3. `transcript`

Ответственность:

- transcript segments
    
- transcript persistence
    
- search/excerpt building
    

## 4. `audio-analysis`

Ответственность:

- silence segments
    
- speech metrics
    
- density metrics
    

## 5. `clip-candidate`

Ответственность:

- scoring
    
- candidate persistence
    
- approve/reject/manual notes
    

## 6. `clip-export`

Ответственность:

- экспорт финального клипа
    
- статус экспорта
    
- ссылки на файл
    

## 7. `storage`

Ответственность:

- where files live
    
- path resolution
    
- safe deletion policy
    

---

# 7. Сущности БД

## `vod_job`

- id
    
- source_type
    
- source_url
    
- original_filename
    
- status
    
- created_at
    
- updated_at
    
- started_at
    
- finished_at
    
- error_message
    
- duration_sec
    
- language
    
- storage_video_path
    
- storage_audio_path
    

## `transcript_segment`

- id
    
- job_id
    
- start_sec
    
- end_sec
    
- text
    
- word_count
    

## `silence_segment`

- id
    
- job_id
    
- start_sec
    
- end_sec
    
- duration_sec
    

## `analysis_window`

- id
    
- job_id
    
- start_sec
    
- end_sec
    
- speech_density
    
- silence_ratio
    
- emotion_hits
    
- continuity_score
    
- total_score
    

## `clip_candidate`

- id
    
- job_id
    
- start_sec
    
- end_sec
    
- score
    
- transcript_excerpt
    
- moderation_status
    
- moderator_note
    
- exported_clip_path
    

## `job_event`

- id
    
- job_id
    
- event_type
    
- message
    
- created_at
    

---

# 8. Статусы job

Нужна простая, жёсткая state machine.

## `JobStatus`

- `NEW`
    
- `QUEUED`
    
- `DOWNLOADING`
    
- `EXTRACTING_AUDIO`
    
- `TRANSCRIBING`
    
- `DETECTING_SILENCE`
    
- `ANALYZING_WINDOWS`
    
- `GENERATING_CANDIDATES`
    
- `READY_FOR_REVIEW`
    
- `EXPORTING_CLIP`
    
- `COMPLETED`
    
- `FAILED`
    

Без бардака. Без “почти готово”. Машина состояний должна быть прозрачной.

---

# 9. Pipeline обработки

## Шаг 1. Ingest

- принять URL или файл
    
- создать `vod_job`
    
- поставить в очередь
    

## Шаг 2. Download

- скачать видео или сохранить загруженный файл
    
- определить duration
    
- сохранить путь
    

## Шаг 3. Extract Audio

- ffmpeg вытаскивает audio track
    

## Шаг 4. Transcribe

- faster-whisper
    
- сохранить transcript segments
    

## Шаг 5. Detect Silence

- ffmpeg silencedetect
    
- сохранить silence segments
    

## Шаг 6. Sliding Window Analysis

- окно 20–30 секунд
    
- шаг 5 секунд
    
- считаются метрики
    

## Шаг 7. Candidate Scoring

- сортировка окон
    
- дедупликация пересекающихся окон
    
- формирование top-N кандидатов
    

## Шаг 8. Review

- UI показывает кандидатов
    
- approve/reject
    
- заметки
    

## Шаг 9. Export

- ffmpeg вырезает финальный клип
    
- отдача файла
    

---

# 10. Формула scoring

Для MVP без нейросетевой шизы.

## Базовые сигналы

- `speech_density = words / seconds`
    
- `silence_ratio = silence_duration / window_duration`
    
- `emotion_hits = keyword/emphasis hits`
    
- `continuity_score = longest continuous speech span / window_duration`
    

## Пример

```text
score =
  0.35 * normalized_speech_density +
  0.25 * (1 - silence_ratio) +
  0.20 * emotion_hits_normalized +
  0.20 * continuity_score
```

Потом можно крутить веса.

---

# 11. UI MVP

## Страницы

### 1. Job List

- список job-ов
    
- статус
    
- дата
    
- источник
    
- duration
    
- переход в детали
    

### 2. Create Job

- URL input
    
- file upload
    
- submit
    

### 3. Job Details

- общая инфа
    
- статус pipeline
    
- transcript preview
    
- candidates list
    
- ошибки/события
    

### 4. Candidate Review

Для каждого кандидата:

- start/end
    
- score
    
- transcript excerpt
    
- preview
    
- approve/reject
    
- export
    

### 5. Export Result

- ссылка на готовый клип
    
- статус экспорта
    

---

# 12. API MVP

## Jobs

- `POST /api/jobs/url`
    
- `POST /api/jobs/upload`
    
- `GET /api/jobs`
    
- `GET /api/jobs/{id}`
    

## Transcript

- `GET /api/jobs/{id}/transcript`
    

## Candidates

- `GET /api/jobs/{id}/candidates`
    
- `POST /api/candidates/{id}/approve`
    
- `POST /api/candidates/{id}/reject`
    

## Export

- `POST /api/candidates/{id}/export`
    
- `GET /api/exports/{id}`
    

## Events

- `GET /api/jobs/{id}/events`
    

---

# 13. Разработка по фазам

## Phase 0 — Foundation

Цель: скелет без магии

### Сделать

- Spring Boot project
    
- Python worker project
    
- Docker Compose
    
- PostgreSQL
    
- базовые Liquibase migrations
    
- health endpoints
    
- создание job
    
- job status storage
    

### Результат

Ты можешь создать job и увидеть его в UI/Swagger.

---

## Phase 1 — Ingest + Storage

### Сделать

- input URL/file
    
- file save
    
- metadata extraction
    
- local storage abstraction
    

### Результат

Видео попадает в систему и хранится предсказуемо.

---

## Phase 2 — Audio Extraction + Transcription

### Сделать

- ffmpeg extract audio
    
- faster-whisper integration
    
- persist transcript segments
    

### Результат

По job есть transcript.

---

## Phase 3 — Silence Detection + Metrics

### Сделать

- ffmpeg silencedetect
    
- persist silence segments
    
- words/sec calculation
    
- continuity score
    

### Результат

По job рассчитаны метрики.

---

## Phase 4 — Candidate Generation

### Сделать

- sliding windows
    
- scoring
    
- overlap deduplication
    
- top candidates selection
    

### Результат

Система отдаёт top-10 кандидатов.

---

## Phase 5 — Review UI

### Сделать

- candidate moderation page
    
- approve/reject
    
- transcript excerpt preview
    
- status updates
    

### Результат

Можно руками модерировать отобранные куски.

---

## Phase 6 — Export

### Сделать

- ffmpeg clip export
    
- download endpoint
    
- export status
    

### Результат

Можно скачать готовый клип.

---

## Phase 7 — Hardening

### Сделать

- error handling
    
- retries
    
- job events
    
- cleanup policy
    
- dockerized local run
    
- README
    

### Результат

Этим уже можно пользоваться без мата каждые 8 минут.

---

# 14. Как использовать агентную разработку правильно

Вот тут самая важная часть. Иначе агент превратит всё в тыкву.

## Главное правило

**Один агент — одна задача — один bounded context — один PR.**

Не:

> “сделай всю Phase 3”

А:

> “добавь сущность silence_segment, migration, repository, service contract, без интеграции ffmpeg”

---

## Формат работы с агентами

### Шаг 1. Ты задаёшь контракт

- цель
    
- scope
    
- ограничения
    
- out of scope
    
- acceptance criteria
    

### Шаг 2. Агент делает только это

Никаких “я ещё попутно улучшил архитектуру”.

### Шаг 3. Второй агент делает review

Отдельно.

### Шаг 4. Ты принимаешь

Только после проверки.

---

# 15. Шаблон задач для агента

Вот рабочий шаблон.

## Prompt template

```text
Task:
Implement [specific feature].

Context:
This is a self-hosted web app for processing VODs into clip candidates.
Architecture is modular monolith (Spring Boot) + external Python worker.
Do not introduce new frameworks or change architecture.

Scope:
- [explicit list]

Out of scope:
- [explicit list]

Requirements:
- Java 21
- Spring Boot
- PostgreSQL
- Liquibase
- Clean package naming
- No business logic in controllers
- Validation must be explicit
- DTOs must not expose entities directly

Deliverables:
- code
- migration
- tests
- short explanation of decisions

Acceptance criteria:
- [bullet list]
```

---

# 16. Какие задачи можно отдавать агентам

## Хорошо подходят

- DTO + controller + service for create job
    
- Liquibase migrations
    
- transcript segment persistence
    
- silence detection parser
    
- candidate scoring service
    
- review page
    
- export endpoint
    
- Docker Compose wiring
    
- README setup
    

## Плохо подходят

- “придумай всю архитектуру”
    
- “сделай AI like Opus”
    
- “улучши кодовую базу”
    
- “сделай красиво”
    
- “добавь всё нужное”
    

Это мусорные формулировки. Они рождают мусорный код.

---

# 17. Как бить backlog

## Epic 1. Core Platform

- project bootstrap
    
- docker compose
    
- db migration setup
    
- healthcheck
    
- job model
    

## Epic 2. Video Ingestion

- file upload
    
- URL submission
    
- local storage
    
- metadata extraction
    

## Epic 3. Transcript Pipeline

- audio extraction
    
- worker call
    
- transcript persistence
    
- transcript API
    

## Epic 4. Audio Analysis

- silence detection
    
- speech density calc
    
- continuity calc
    
- analysis windows
    

## Epic 5. Candidate Engine

- scoring formula
    
- overlap merge
    
- candidate persistence
    
- candidate API
    

## Epic 6. Review UI

- job list page
    
- job details page
    
- candidate moderation
    
- filtering/sorting
    

## Epic 7. Export

- export clip endpoint
    
- file generation
    
- download handling
    

## Epic 8. Stabilization

- retries
    
- cleanup
    
- logs
    
- job events
    
- docs
    

---

# 18. Что делать тебе лично, а не агенту

Есть вещи, которые лучше не отдавать.

## Делай сам

- финальную архитектуру
    
- naming модулей
    
- state machine
    
- scoring logic v1
    
- acceptance criteria
    
- review важных PR
    

Потому что если это отдашь агенту, он начнёт быть “креативным”, а креативный агент в архитектуре — это почти всегда вежливо оформленный хаос.

---

# 19. Реальный режим работы на неделю

С учётом агентной разработки.

## День 1

- зафиксировать архитектуру
    
- создать backlog
    
- bootstrap backend + worker + db + compose
    

## День 2

- ingest jobs
    
- create/list/get jobs
    
- simple UI skeleton
    

## День 3

- audio extraction
    
- transcript worker integration
    

## День 4

- silence detection
    
- metrics persistence
    

## День 5

- candidate scoring
    
- top-N generation
    

## День 6

- moderation UI
    
- approve/reject
    

## День 7

- export clip
    
- hardening
    
- smoke test end-to-end
    

Это агрессивно, но реально для MVP, если агентов не выпускать пастись в архитектурном поле.

---

# 20. Риски

## 1. Агент начнёт усложнять

Лечится:

- короткие задачи
    
- чёткий scope
    
- запрет на архитектурные отклонения
    

## 2. Python/Java контракт поплывёт

Лечится:

- фиксированный JSON schema
    
- versioned worker contract
    

## 3. Начнёшь добавлять “ещё одну умную метрику”

Лечится:

- freeze MVP scope
    
- только speech + silence + continuity + keywords
    

## 4. Утонешь в фронте

Лечится:

- тонкий UI
    
- без дизайн-паралича
    
- сначала работает, потом красиво
    

---

# 21. Мой жёсткий совет по implementation order

Вот прям в таком порядке:

1. job model
    
2. ingest
    
3. storage
    
4. worker invocation
    
5. transcript
    
6. silence detection
    
7. scoring
    
8. candidates
    
9. moderation
    
10. export
    

Не наоборот.  
Не “сначала красивый UI”.  
Не “сначала умный AI”.  
Сначала труба. Потом косметика.

---

# 22. Финальная формулировка проекта

## Одной строкой

**Self-hosted web app that ingests long VODs, transcribes audio, detects speech activity and silence, scores clip candidates, and provides a manual review workflow for exporting highlights.**

Вот это уже звучит как вменяемый продукт, а не фантазия под кофеином.

---

Если хочешь, следующим сообщением я могу сделать тебе уже **готовый backlog в формате для Obsidian Kanban**, чтобы ты просто вставил и начал раздавать задачи агентам.
