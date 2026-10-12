# TrainLog в Kubernetes (minikube)

TrainLog — дневник силовых тренировок. Приложение состоит из трёх сервисов и одной разовой задачи:

| Компонент | Образ | Что делает |
|---|---|---|
| frontend | `trainlog-frontend:1.0.0` (nginx) | отдаёт статику (HTML/CSS/JS) и проксирует `/api/*` на бэкенд |
| backend | `trainlog-backend:1.0.0` (Spring Boot) | REST API, бизнес-логика, сессии пользователей |
| db | `postgres:18.4-alpine` | хранит все данные и HTTP-сессии |
| migrate | `trainlog-migrate:1.0.0` (Flyway) | применяет SQL-миграции к БД и завершается |

Все манифесты лежат в каталоге `k8s/`, Dockerfile — в `backend/`, `frontend/`, `db/`.

## Схема развёртывания

```
                         кластер minikube, namespace trainlog
                ┌──────────────────────────────────────────────────────────────┐
 браузер ──────►│ Service frontend ──► Pod frontend (nginx)                    │
  (minikube     │  NodePort 30080        │  /api/*                             │
   service)     │                        ▼                                     │
                │                 Service backend ──► Pod backend ×N           │
                │                  ClusterIP 8080      ├ init: migrate (Flyway)│
                │                                      └ backend (Spring Boot) │
                │                                              │               │
                │                                              ▼               │
                │                 Service db (headless) ──► Pod db-0 ◄── PVC   │
                │                                          (StatefulSet)  1Gi  │
                └──────────────────────────────────────────────────────────────┘
```

## Использованные абстракции Kubernetes

| Абстракция | Файл | Зачем |
|---|---|---|
| **Namespace** `trainlog` | `00-namespace.yaml` | отделяет ресурсы приложения от остального кластера; всё удаляется одной командой `kubectl delete ns trainlog` |
| **ConfigMap** `trainlog-config` | `01-config.yaml` | несекретные настройки: имя БД, JDBC-адрес, адрес бэкенда для nginx |
| **Secret** `trainlog-db` | `01-config.yaml` | логин и пароль БД; попадают в контейнеры как переменные окружения |
| **StatefulSet** `db` | `02-db.yaml` | PostgreSQL. В отличие от Deployment у пода стабильное имя `db-0` и свой постоянный диск, который переживает пересоздание пода |
| **PersistentVolumeClaim** (`volumeClaimTemplates`) | `02-db.yaml` | заявка на диск 1 Gi для данных PostgreSQL; в minikube её выполняет встроенный `storage-provisioner` |
| **Service** `db` (headless, `clusterIP: None`) | `02-db.yaml` | обязательный для StatefulSet сервис; даёт DNS-имя `db`, по которому бэкенд находит базу |
| **Deployment** `backend` | `03-backend.yaml` | описывает, какой образ запускать и в скольких копиях; сам следит, чтобы нужное число подов всегда работало |
| **ReplicaSet** | создаётся Deployment'ом | низкоуровневый объект, который непосредственно держит N одинаковых подов; при обновлении образа Deployment создаёт новый ReplicaSet и плавно переключается на него |
| **Pod** | создаётся ReplicaSet'ом | минимальная единица запуска: один или несколько контейнеров с общей сетью |
| **initContainer** `migrate` | `03-backend.yaml` | запускается в поде бэкенда до основного контейнера и применяет миграции Flyway. Бэкенд стартует, только когда схема БД готова |
| **Service** `backend` (ClusterIP) | `03-backend.yaml` | постоянный внутренний адрес и балансировка запросов между всеми репликами бэкенда |
| **Deployment** `frontend` | `04-frontend.yaml` | nginx со статикой |
| **Service** `frontend` (NodePort 30080) | `04-frontend.yaml` | открывает фронтенд наружу кластера |
| **Probes** (startup, liveness, readiness) | `02`–`04` | проверки здоровья: пока проба не прошла, под не получает трафик; если «живость» пропала, Kubernetes перезапускает контейнер |
| **Resources** (requests/limits) | `02`–`04` | сколько CPU и памяти поду гарантировано и сколько максимум он может занять |

### Как это соотносится с Docker Compose

| Docker Compose | Kubernetes |
|---|---|
| `services.backend` | Deployment + Service |
| `deploy.replicas` / `--scale` | `spec.replicas` / `kubectl scale` |
| `environment`, `.env` | ConfigMap + Secret |
| `volumes: db-data` | PersistentVolumeClaim |
| `healthcheck` | readiness/liveness/startup probes |
| `depends_on: service_completed_successfully` | initContainer |
| `ports: "8080:80"` | Service типа NodePort |
| `restart: unless-stopped` | поведение по умолчанию: Deployment сам пересоздаёт упавшие поды |

## Как сервисы взаимодействуют

1. **Браузер → frontend.** Пользователь открывает адрес, который выдаёт `minikube service frontend -n trainlog`.
   Запрос приходит на NodePort 30080 узла minikube, Service `frontend` передаёт его в под с nginx.
2. **frontend → backend.** Статику nginx отдаёт сам, а запросы `/api/*` проксирует на
   `http://backend.trainlog.svc.cluster.local:8080`. Это DNS-имя Service `backend`: его разрешает
   CoreDNS кластера, а kube-proxy распределяет соединения между всеми готовыми (ready) подами бэкенда.
   Фронтенд и API работают на одном адресе, поэтому CORS не нужен.
3. **backend → db.** Бэкенд подключается к `jdbc:postgresql://db:5432/trainlog`. Имя `db` разрешается
   через headless Service в IP пода `db-0`. Логин и пароль приходят из Secret.
4. **migrate → db.** При старте каждого пода бэкенда initContainer запускает `flyway migrate`.
   Первый под применяет миграции, следующие видят, что схема актуальна, и сразу завершаются.
   Параллельный запуск безопасен: Flyway берёт блокировку в PostgreSQL. Пока БД ещё стартует,
   Flyway повторяет подключение (`FLYWAY_CONNECT_RETRIES`).
5. **Сессии.** HTTP-сессии хранятся в PostgreSQL (Spring Session JDBC), а не в памяти процесса.
   Поэтому бэкенд можно масштабировать до любого числа реплик: запрос пользователя может попасть
   в любой под, и вход при этом не теряется.

Порядок запуска не нужно задавать вручную. Все объекты создаются одновременно, а зависимости
выстраиваются сами: initContainer ждёт базу, readiness-проба не пускает трафик на бэкенд,
пока Spring не стартовал.

### Что пришлось поменять в приложении

- **nginx: DNS-резолвер.** Раньше в конфиге был `resolver 127.0.0.11`, а это адрес DNS-сервера,
  который есть только внутри Docker. Теперь в образе включена опция официального образа nginx
  `NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1`, и адрес подставляется из `/etc/resolv.conf` контейнера.
  В Docker это по-прежнему `127.0.0.11`, в Kubernetes — адрес CoreDNS (`10.96.0.10`).
  Директива `resolver` в nginx не использует поисковые домены, поэтому в `BACKEND_URL` указано
  полное имя `backend.trainlog.svc.cluster.local`.
- **Health-пробы.** В `application.yml` включены `/actuator/health/liveness` и
  `/actuator/health/readiness`, а в Spring Security они открыты без авторизации.

## Запуск

Нужны Docker и minikube. Кластер запускается с Docker-рантаймом, чтобы работал `minikube docker-env`:

```bash
minikube start --driver=docker --container-runtime=docker --cpus=2 --memory=3072
```

### Сборка образов

Вариант 1. Собрать сразу в Docker-демоне minikube, тогда загружать образы не нужно:

```bash
eval $(minikube docker-env)                                  # bash
& minikube -p minikube docker-env --shell powershell | Invoke-Expression   # PowerShell

docker compose -f docker-compose.yml build
```

Вариант 2. Собрать локально и загрузить в minikube:

```bash
docker compose -f docker-compose.yml build
minikube image load trainlog-migrate:1.0.0
minikube image load trainlog-backend:1.0.0
minikube image load trainlog-frontend:1.0.0
minikube image ls | grep trainlog
```

У образов фиксированный тег `1.0.0`, поэтому Kubernetes берёт их из локального кэша узла
(`imagePullPolicy: IfNotPresent`) и не пытается скачать из интернета.

### Развёртывание

```bash
kubectl apply -f k8s/
kubectl -n trainlog get pods -w
```

Через 30–60 секунд все поды должны быть в состоянии `Running` и `1/1`.

Открыть приложение:

```bash
minikube service frontend -n trainlog
```

На Windows с драйвером docker команда держит туннель, поэтому терминал нужно оставить открытым.
Другой вариант: `kubectl -n trainlog port-forward svc/frontend 8080:80` и адрес http://localhost:8080.

### Масштабирование

```bash
kubectl -n trainlog scale deploy/backend --replicas=3
kubectl -n trainlog scale deploy/frontend --replicas=2
kubectl -n trainlog get pods -o wide
kubectl -n trainlog get endpointslices -l kubernetes.io/service-name=backend
```

В EndpointSlice видно, что Service `backend` теперь ведёт на три IP. Перезапускать nginx не нужно.
БД не масштабируется: PostgreSQL в одном экземпляре не умеет работать в несколько реплик.

### Полезные команды

```bash
kubectl -n trainlog get all                     # все объекты приложения
kubectl -n trainlog describe pod <имя>          # подробности и события пода
kubectl -n trainlog logs deploy/backend         # логи бэкенда
kubectl -n trainlog logs deploy/backend -c migrate   # логи миграций
kubectl -n trainlog exec -it db-0 -- psql -U trainlog -d trainlog
kubectl delete ns trainlog                      # удалить приложение целиком
minikube stop                                   # остановить кластер
```

Пароль БД в `01-config.yaml` учебный. В реальном окружении Secret не хранят в git:
его создают отдельно (`kubectl create secret ...`) или через менеджер секретов.
