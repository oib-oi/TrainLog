# TrainLog

Дневник тренировок: справочник упражнений, тренировки и подходы в них.
| Документ | О чём |
|---|---|
| [Отчёт_dz1.md](Отчёт_dz1.md) | предметная область и соответствие 12 факторам |
| [k8s/Отчёт_dz2.md](k8s/Отчёт_dz2.md) | развёртывание в Kubernetes (minikube) |
| [k8s/Отчёт_dz3.md](k8s/Отчёт_dz3.md) | сбор логов в VictoriaLogs, LogsQL-запросы, алерты |
| [k8s/Отчёт_dz4.md](k8s/Отчёт_dz4.md) | метрики в VictoriaMetrics: рантайм, PostgreSQL, бизнес-метрики |

## Требования

- Docker с Docker Compose v2
- Для запуска бэкенда вне Docker: JDK 25 и Maven 3.9+ (или IntelliJ IDEA со встроенным Maven)
- Для Kubernetes: minikube и kubectl
- Для VictoriaMetrics (ДЗ №4): Helm 3, установка в Windows: `winget install Helm.Helm`

## Структура

```
backend/                 Spring Boot приложение (REST API)
db/migration/            SQL-миграции Flyway
frontend/html/           статический фронтенд
frontend/nginx.conf.template
k8s/                     манифесты Kubernetes: приложение (00–04) и логирование (05–07)
k8s/monitoring/          values для helm-чарта VictoriaMetrics
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

Запуск в Kubernetes — в разделе [Kubernetes (minikube)](#kubernetes-minikube).

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
Необязательная `APP_ENV` (по умолчанию `local`) попадает в логи как `service.environment`.

## Логи

Бэкенд и nginx пишут в stdout по одному JSON-объекту на строку, имена полей взяты из
Elastic Common Schema: `@timestamp`, `log.level`, `message`, `service.name`, `request.id`,
`http.response.status_code` и другие. На каждый HTTP-запрос пишется одна строка access-лога.
nginx генерирует `request.id` и передаёт его бэкенду в заголовке `X-Request-Id`, поэтому
запрос можно проследить через оба сервиса.

```bash
docker compose logs -f backend
```

Полное описание полей — в [k8s/Отчёт_dz3.md](k8s/Отчёт_dz3.md#формат-логов).

## Kubernetes (minikube)

Приложение разворачивается в namespace `trainlog`, сбор логов — в namespace `logging`:

| Файл | Что создаёт |
|---|---|
| `00-namespace.yaml`, `01-config.yaml` | namespace `trainlog`, ConfigMap и Secret |
| `02-db.yaml` | PostgreSQL (StatefulSet + PVC) |
| `03-backend.yaml` | бэкенд, миграции запускаются в initContainer |
| `04-frontend.yaml` | nginx, Service NodePort 30080 |
| `05-victorialogs.yaml` | VictoriaLogs (StatefulSet + PVC) в namespace `logging` |
| `06-vector.yaml` | Vector (DaemonSet): читает логи подов и отправляет в VictoriaLogs |
| `07-vmalert.yaml` | vmalert: алерты по LogsQL-запросам |

### Запуск

1. Запустить кластер с Docker-рантаймом, чтобы работал `minikube docker-env`:

   ```bash
   minikube start --driver=docker --container-runtime=docker --cpus=2 --memory=3072
   ```

2. Собрать образы сразу в Docker-демоне minikube:

   ```bash
   eval $(minikube docker-env)                                                  # bash
   & minikube -p minikube docker-env --shell powershell | Invoke-Expression    # PowerShell

   docker compose -f docker-compose.yml build
   ```

   У образов фиксированный тег `1.0.0`, поэтому Kubernetes берёт их из локального кэша узла.

3. Развернуть:

   ```bash
   kubectl apply -f k8s/
   kubectl -n trainlog rollout restart deploy/backend deploy/frontend
   ```

   `rollout restart` нужен, если приложение уже было развёрнуто и образы пересобраны: тег не
   изменился, поэтому сам `kubectl apply` поды не перезапустит.

4. Дождаться, пока все поды станут `Running` и `1/1` (30–60 секунд):

   ```bash
   kubectl get pods -n trainlog
   kubectl get pods -n logging
   ```

5. Открыть интерфейсы. Каждый port-forward держит терминал, поэтому каждую команду нужно запускать
   в отдельном окне:

   ```bash
   kubectl -n trainlog port-forward svc/frontend 8080:80        # приложение
   kubectl -n logging port-forward svc/victorialogs 9428:9428   # vmui
   kubectl -n logging port-forward svc/vmalert 8880:8880        # vmalert
   ```

   | Что | Адрес |
   |---|---|
   | приложение | http://localhost:8080 |
   | логи (vmui) | http://localhost:9428/select/vmui/ |
   | алерты | http://localhost:8880/vmalert/alerts |

   Приложение можно открыть и без port-forward: `minikube service frontend -n trainlog`.
   На Windows с драйвером docker эта команда держит туннель, поэтому терминал тоже нужно оставить открытым.

   Ошибка `bind: Only one usage of each socket address` означает, что порт уже занят, обычно
   port-forward, запущенным раньше. Если адрес открывается, ничего делать не нужно.

Логи появляются в vmui через 5–10 секунд после обращений к приложению. Примеры LogsQL-запросов —
в [k8s/Отчёт_dz3.md](k8s/Отчёт_dz3.md#logsql-запросы). Те же запросы можно выполнить через API.
В PowerShell нужно писать `curl.exe`, потому что `curl` там — псевдоним `Invoke-WebRequest`:

```bash
curl.exe http://localhost:9428/select/logsql/query --data-urlencode "query=_time:5m"
```

### Масштабирование

```bash
kubectl -n trainlog scale deploy/backend --replicas=3
kubectl -n trainlog get endpointslices -l kubernetes.io/service-name=backend
```

Сессии хранятся в PostgreSQL, поэтому вход не теряется, на какую бы реплику ни попал запрос.
БД не масштабируется.

### Полезные команды

```bash
kubectl -n trainlog logs deploy/backend              # логи бэкенда
kubectl -n trainlog logs deploy/backend -c migrate   # логи миграций
kubectl -n trainlog exec -it db-0 -- psql -U trainlog -d trainlog
kubectl -n logging logs ds/vector                    # ошибки доставки логов
kubectl delete ns trainlog logging                   # удалить приложение и логирование
kubectl delete clusterrole,clusterrolebinding vector
minikube stop
```

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

1. Добавить файл в `db/migration` со следующим номером версии: `V7__add_something.sql`.
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
