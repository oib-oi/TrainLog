# TrainLog

Дневник тренировок: справочник упражнений, тренировки и подходы в них.
Описание предметной области и соответствия 12 факторам — в [Отчёт.md](Отчёт.md).

## Требования

- Docker с Docker Compose v2
- Для запуска бэкенда вне Docker: JDK 25 и Maven 3.9+ (или IntelliJ IDEA со встроенным Maven)

## Структура

```
backend/                 Spring Boot приложение (REST API)
db/migration/            SQL-миграции Flyway
frontend/html/           статический фронтенд
frontend/nginx.conf.template
docker-compose.yml             основное описание сервисов
docker-compose.override.yml    дополнения для локальной разработки
.env.example             шаблон конфигурации
```

Сервисы Compose:

| Сервис | Назначение | Порт |
|---|---|---|
| `db` | PostgreSQL 18 | 5432 (наружу только в dev-режиме) |
| `migrate` | применяет миграции Flyway и завершается | — |
| `backend` | REST API на Spring Boot | 8080 внутри сети Docker |
| `frontend` | nginx: статика + проксирование `/api` на бэкенд | `APP_PORT` (по умолчанию 8080) |

## Быстрый старт

```bash
cp .env.example .env
docker compose up --build -d
```

Приложение: http://localhost:8080

Эта команда запускает приложение в dev-режиме (см. [Разработка](#разработка)).
Запуск без dev-настроек, как в эксплуатации:

```bash
docker compose -f docker-compose.yml up --build
```

Остановка: `docker compose down`. Остановка с удалением данных БД: `docker compose down -v`.

## Конфигурация

Все настройки задаются переменными окружения в `.env`:

| Переменная | Описание |
|---|---|
| `DB_NAME` | имя базы данных |
| `DB_USER` | пользователь БД |
| `DB_PASSWORD` | пароль БД |
| `APP_PORT` | порт, на котором доступно приложение |
| `DB_PORT` | порт БД на хосте в dev-режиме (по умолчанию 5432) |
| `APP_VERSION` | версия релиза, ею помечаются собранные образы (по умолчанию `dev`) |

Бэкенд читает `DB_URL`, `DB_USER`, `DB_PASSWORD` и `PORT`; в Docker их выставляет `docker-compose.yml`.

## Разработка

`docker compose` автоматически применяет `docker-compose.override.yml` поверх основного файла.
Override публикует порт БД на хост и монтирует `frontend/html` в контейнер nginx,
образы и конфигурация приложения остаются теми же.

Чтобы запустить без override, основной файл указывается явно: `docker compose -f docker-compose.yml ...`.

### Фронтенд

```bash
docker compose up -d --build
```

Файлы из `frontend/html` смонтированы в контейнер, изменения видны сразу после обновления страницы.
Пересобирать образ нужно только при изменении `nginx.conf.template`:

```bash
docker compose up -d --build frontend
```

Фронтенд — PWA: `manifest.webmanifest`, `sw.js` и иконки в `frontend/html/icons`. Установка на телефон
требует HTTPS (на `localhost` работает и по HTTP). Файлы и ответы API запрашиваются из сети, кэш
используется только без соединения, поэтому новая версия видна сразу после обновления страницы.
При добавлении файлов в приложение допишите их в `SHELL` в `sw.js` и поднимите `VERSION`.

### Бэкенд в Docker

После изменения Java-кода пересобрать и перезапустить только бэкенд:

```bash
docker compose up -d --build backend
docker compose logs -f backend
```

### Бэкенд из IDE

Поднять только БД и миграции:

```bash
docker compose up -d db migrate
```

Запустить приложение локально с переменными окружения:

```bash
cd backend
DB_URL=jdbc:postgresql://localhost:5432/trainlog DB_USER=trainlog DB_PASSWORD=change-me mvn spring-boot:run
```

В IntelliJ IDEA: Run Configuration для `TrainlogApplication`, в поле *Environment variables*
указать `DB_URL=jdbc:postgresql://localhost:5432/trainlog;DB_USER=trainlog;DB_PASSWORD=change-me`.

API будет доступно на http://localhost:8080/api. Если одновременно запущен контейнер `frontend`,
он тоже займёт порт 8080 — остановите его (`docker compose stop frontend`) или задайте бэкенду `PORT=8081`.

### Тесты

```bash
cd backend
./mvnw test      # модульные тесты сервисов и контроллеров
./mvnw verify    # плюс интеграционные (*IT) на PostgreSQL в Testcontainers, нужен запущенный Docker
```

`mvnw` — Maven Wrapper, сам скачивает Maven нужной версии, достаточно JDK. В PowerShell: `.\mvnw.cmd test`.

Интеграционные тесты накатывают миграции из `db/migration`, поэтому запускаются из каталога `backend`.
При сборке образа (`mvn package`) выполняются только модульные тесты.

### Миграции БД

Схема БД меняется только через миграции, Hibernate её лишь проверяет (`ddl-auto: validate`).

1. Добавить файл в `db/migration` со следующим номером версии: `V3__add_something.sql`.
   Уже применённые миграции не редактировать.
2. Применить:

```bash
docker compose up --build migrate
```

3. Обновить JPA-сущности, иначе бэкенд не стартует из-за несовпадения схемы.

Состояние миграций: `docker compose run --rm migrate info`.

### Работа с БД

```bash
docker compose exec db psql -U trainlog -d trainlog
```

В dev-режиме к БД можно подключиться любым клиентом (DBeaver, IDEA Database) по `localhost:5432`.

Сбросить БД к начальному состоянию:

```bash
docker compose down -v
docker compose up -d --build
```

### Релизы и откат

Образы собираются с тегом `APP_VERSION`: `trainlog-backend:1.0.0`, `trainlog-frontend:1.0.0`,
`trainlog-migrate:1.0.0`. Новый релиз — поднять версию в `.env` и выполнить `docker compose up -d --build`.
Откат на уже собранную версию без пересборки:

```bash
APP_VERSION=1.0.0 docker compose up -d --no-build
```

### Масштабирование

```bash
docker compose up -d --scale backend=2
```

nginx резолвит имя `backend` через DNS Docker и обновляет адреса каждые 10 секунд, поэтому
перезапускать frontend после изменения числа реплик не нужно.

## API

| Метод | Путь | Действие |
|---|---|---|
| POST | `/api/auth/register` | регистрация (`email`, `password`) и вход |
| POST | `/api/auth/login` | вход |
| POST | `/api/auth/logout` | выход |
| GET | `/api/auth/me` | текущий пользователь |
| GET / POST | `/api/exercises` | список / создание упражнения |
| GET / PUT / DELETE | `/api/exercises/{id}` | чтение / изменение / удаление |
| GET / POST | `/api/workouts` | список / создание тренировки |
| GET / PUT / DELETE | `/api/workouts/{id}` | чтение / изменение / удаление |
| GET / POST | `/api/workouts/{id}/sets` | подходы тренировки / добавление подхода |
| PUT / DELETE | `/api/sets/{id}` | изменение / удаление подхода |

Список тренировок отдаётся постранично: `GET /api/workouts?page=0&size=20` (`size` от 1 до 100).
В ответе `content`, `page`, `size`, `totalElements`, `totalPages`.

Все запросы, кроме регистрации и входа, требуют cookie `SESSION`, без неё API отвечает `401`.
Сессии хранятся в PostgreSQL, поэтому вход не теряется при перезапуске и работает с несколькими репликами бэкенда.

Тренировки и подходы видит только их владелец. Упражнения из начального справочника общие и доступны
всем только для чтения, свои упражнения пользователь добавляет и меняет сам.

Пример:

```bash
curl -c cookies.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"me@example.com","password":"correct horse"}'

curl -b cookies.txt -X POST http://localhost:8080/api/workouts \
  -H "Content-Type: application/json" \
  -d '{"performedOn":"2026-09-29","title":"Ноги","durationMinutes":60}'
```

Тренировки, созданные до появления аккаунтов, остаются в БД без владельца. Передать их пользователю:

```bash
docker compose exec db psql -U trainlog -d trainlog \
  -c "update workout set account_id = (select id from account where email = 'me@example.com') where account_id is null"
```
