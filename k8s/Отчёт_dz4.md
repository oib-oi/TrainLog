# Метрики TrainLog в VictoriaMetrics

Метрики приложения и базы собирает VictoriaMetrics single-node, установленная из helm-чарта
в namespace `monitoring`. Используется pull-модель: VictoriaMetrics сама ходит к подам, у которых
есть аннотации `prometheus.io/*`, и забирает метрики в формате Prometheus/OpenMetrics.

## Стек

| Что | Чем | Где |
|---|---|---|
| Приложение | Spring Boot 4.1, Java 25 | `backend/` |
| Метрики приложения | Micrometer + `micrometer-registry-prometheus`, эндпоинт actuator `/actuator/prometheus` | `pom.xml`, `application.yml` |
| Бизнес-метрики | счётчики Micrometer в `TrainlogMetrics` | `backend/src/main/java/ru/trainlog/common/TrainlogMetrics.java` |
| Метрики PostgreSQL | `postgres-exporter` v0.17.1, сайдкар в поде `db-0`, порт 9187 | `k8s/02-db.yaml` |
| Хранение и сбор | VictoriaMetrics single (чарт `vm/victoria-metrics-single` 0.49.0, VM v1.154.0) со встроенным скрейпером | `k8s/monitoring/vm-values.yaml` |

## Схема

```
 namespace trainlog                                       namespace monitoring
┌─────────────────────────────────────────┐              ┌────────────────────────────────────────────┐
│ backend  :8080/actuator/prometheus      │◄── scrape ───┤ vm-single-victoria-metrics-single-server-0 │
│                                         │              │ ├ kubernetes_sd (role: pod), раз в 15 с    │
│ db-0  ┌ postgres          :5432         │              │ ├ хранилище: PVC 2Gi, 14 дней              │
│       └ postgres-exporter :9187/metrics │◄── scrape ───┤ └ HTTP API и vmui на :8428                 │
└─────────────────────────────────────────┘              └────────────────────────────────────────────┘
```

Отдельный vmagent не ставился: в чарте single-server включён встроенный скрейпер
(`server.scrape.enabled: true`), он работает так же, как vmagent, и пишет сразу в локальное хранилище.
Статус скрейпинга смотрится на самом сервере: `/targets` и `/api/v1/targets`.

## Service discovery

В `vm-values.yaml` описан job `kubernetes-pods` с `kubernetes_sd_configs: role: pod`.
Чарт сам создаёт ServiceAccount и ClusterRole с правами на чтение подов, поэтому VictoriaMetrics
видит все поды кластера. Дальше relabeling:

1. оставляет только поды с `prometheus.io/scrape: "true"` в фазе `Running`;
2. для каждого порта контейнера SD создаёт отдельный таргет; `keep_if_equal` оставляет только тот,
   номер которого совпадает с `prometheus.io/port`. Так у пода `db-0` скрейпится экспортер,
   а не сам Postgres на 5432;
3. путь берётся из `prometheus.io/path`;
4. метки пода (`app` и др.), namespace, имя пода и контейнера переносятся в метки рядов.

Аннотации в манифестах:

| Под | `prometheus.io/port` | `prometheus.io/path` |
|---|---|---|
| backend | `8080` | `/actuator/prometheus` |
| db | `9187` | `/metrics` |

Новые реплики бэкенда подхватываются автоматически: после `kubectl -n trainlog scale deploy/backend --replicas=3`
в `up{app="backend"}` будет три ряда.

## Метрики рантайма и фреймворка

Их отдаёт Spring Boot Actuator через Micrometer, ничего писать не пришлось. Основные группы:

| Префикс | Что |
|---|---|
| `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*`, `jvm_classes_*` | память по областям, сборки мусора, потоки, классы |
| `process_cpu_*`, `system_cpu_*`, `process_uptime_seconds` | CPU процесса и узла, аптайм |
| `http_server_requests_seconds_*` | число и время HTTP-запросов с метками `uri`, `method`, `status` |
| `hikaricp_connections_*` | пул соединений с базой: активные, ожидающие, таймауты |
| `tomcat_sessions_*`, `executor_*`, `logback_events_total` | сессии, пулы потоков, число записей в лог по уровням |

Ко всем метрикам приложения добавляется метка `application="trainlog"` (`management.metrics.tags`).

Эндпоинт открыт без авторизации (`SecurityConfig`), но наружу не виден: nginx проксирует в бэкенд
только `/api/`, поэтому `/actuator/prometheus` доступен лишь изнутри кластера.

## Метрики PostgreSQL

`postgres-exporter` запущен вторым контейнером в поде `db-0`. Контейнеры пода делят сетевое
пространство, поэтому экспортер подключается к базе по `localhost:5432` с теми же логином и паролем
из Secret `trainlog-db`. Отдельный Service не нужен: VictoriaMetrics ходит прямо на IP пода.

Примеры метрик: `pg_up`, `pg_database_size_bytes`, `pg_stat_database_xact_commit`,
`pg_stat_database_tup_inserted`, `pg_stat_activity_count`, `pg_locks_count`, `pg_settings_*`.

## Бизнес-метрики

Все счётчики собраны в одном классе `TrainlogMetrics`, сервисы вызывают его после успешной операции.
Если операция отклонена (почта занята, упражнение не найдено и т.п.), счётчик не меняется.

| Метрика | Метки | Где увеличивается | Зачем |
|---|---|---|---|
| `trainlog_registrations_total` | — | `AuthService.register` | приток новых пользователей |
| `trainlog_logins_total` | `result="success"\|"failure"` | `AuthController.login` | доля неудачных входов; всплеск `failure` — подбор пароля |
| `trainlog_workouts_total` | — | `WorkoutService.create` | основная активность: сколько тренировок записывают |
| `trainlog_sets_total` | — | `WorkoutSetService.create` | насколько подробно заполняют тренировки |
| `trainlog_exercises_total` | — | `ExerciseService.create` | пользуются ли своим справочником упражнений |
| `trainlog_lifted_weight_kg_total` | — | `WorkoutSetService.create` | суммарный тоннаж: повторения × вес по всем подходам |

В коде счётчики названы `trainlog.workouts` и т.д.: Micrometer переводит точки в `_` и добавляет
суффикс `_total`, единица `kg` попадает в имя `trainlog_lifted_weight_kg_total`.

Примеры запросов MetricsQL:

```
sum(increase(trainlog_workouts_total[1h]))                         # тренировок за час
sum(rate(trainlog_logins_total{result="failure"}[5m])) * 60        # неудачных входов в минуту
sum(increase(trainlog_logins_total{result="failure"}[1h]))
  / sum(increase(trainlog_logins_total[1h]))                       # доля неудачных входов
sum(increase(trainlog_lifted_weight_kg_total[1d]))                 # тоннаж за сутки
sum(increase(trainlog_sets_total[1d])) / sum(increase(trainlog_workouts_total[1d]))  # подходов на тренировку
```

## Развёртывание

Все команды даны для PowerShell и выполняются из корня репозитория.

### 0. Установка Helm

VictoriaMetrics устанавливается из helm-чарта, поэтому сначала нужно установить сам Helm.
Без него команды из шага 2 завершатся ошибкой `Имя "helm" не распознано как имя командлета`.
Устанавливается один раз:

```powershell
winget install Helm.Helm
```

После установки нужно открыть новое окно PowerShell, чтобы обновился `PATH`. Проверка: `helm version`.

### 1. Приложение

Кластер запускается так же, как в ДЗ №2:

```powershell
minikube start
```

В этом ДЗ изменились бэкенд и манифесты `02-db.yaml`, `03-backend.yaml`. Образ собирается в Docker
minikube, как в предыдущих ДЗ:

```powershell
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker compose -f docker-compose.yml build backend
kubectl apply -f k8s/
kubectl -n trainlog rollout restart deploy/backend
```

После применения у `db-0` два контейнера: `kubectl get pods -n trainlog` показывает `2/2`.

### 2. VictoriaMetrics

```powershell
helm repo add vm https://victoriametrics.github.io/helm-charts/
helm repo update
helm upgrade --install vm-single vm/victoria-metrics-single -n monitoring --create-namespace -f k8s/monitoring/vm-values.yaml
```

`upgrade --install` ставит релиз, если его ещё нет, и обновляет, если он уже установлен. Ту же команду
нужно повторить после изменения `vm-values.yaml`.

## Проверка

```powershell
kubectl -n monitoring get pods
kubectl -n monitoring get svc
kubectl -n monitoring port-forward svc/vm-single-victoria-metrics-single-server 8428:8428
```

port-forward занимает окно, поэтому запросы выполняются в другом окне PowerShell по одному,
без строк-комментариев: при вставке сразу нескольких строк консоль PowerShell может склеить или обрезать их. Вместо `curl`
используется `Invoke-RestMethod`: он сам разбирает JSON, и не нужно экранировать кавычки в запросе.

```powershell
$vm = 'http://localhost:8428/api/v1'

# число имён метрик
(Invoke-RestMethod "$vm/label/__name__/values").data.Count

# таргеты: victoriametrics, backend, postgres-exporter; у всех значение 1
(Invoke-RestMethod "$vm/query" -Body @{ query = 'up' }).data.result |
  ForEach-Object { '{0} {1} {2}' -f $_.metric.job, $_.metric.container, $_.value[1] }

# статус скрейпинга
(Invoke-RestMethod "$vm/targets").data.activeTargets | Format-Table scrapeUrl, health, lastError

# бизнес-метрики
(Invoke-RestMethod "$vm/query" -Body @{ query = '{__name__=~"trainlog_.*"}' }).data.result |
  ForEach-Object { '{0} {1} {2}' -f $_.metric.__name__, $_.metric.result, $_.value[1] }

# PostgreSQL
(Invoke-RestMethod "$vm/query" -Body @{ query = 'pg_up' }).data.result.value[1]
```

В браузере: http://localhost:8428/targets — список таргетов, http://localhost:8428/vmui — графики.

Чтобы бизнес-метрики выросли, нужно поработать с приложением: зарегистрироваться, войти
(в том числе с неверным паролем), добавить тренировку с подходами и своё упражнение.
Приложение открывается на http://localhost:8080 после команды:

```powershell
kubectl -n trainlog port-forward svc/frontend 8080:80
```

Новые значения появляются в ответах не сразу, а примерно через 30–45 секунд: интервал скрейпинга 15 секунд,
и ещё 30 секунд VictoriaMetrics по умолчанию не отдаёт самые свежие точки (`-search.latencyOffset`).

Ниже не запрос, а пример того, что вернули запросы `up` и `{__name__=~"trainlog_.*"}` на тестовом трафике
(регистрация, вход, неудачный вход, тренировка из трёх подходов по 80 кг, своё упражнение):

```
up{job="kubernetes-pods", pod="backend-…",  container="backend"}            1
up{job="kubernetes-pods", pod="db-0",       container="postgres-exporter"}  1
up{job="victoriametrics"}                                                   1

trainlog_registrations_total                   1
trainlog_logins_total{result="success"}        1
trainlog_logins_total{result="failure"}        1
trainlog_workouts_total                        1
trainlog_sets_total                            3
trainlog_exercises_total                       1
trainlog_lifted_weight_kg_total             1920
```

Значения метрик напрямую из пода, без VictoriaMetrics:

```powershell
kubectl -n trainlog exec deploy/backend -c backend -- curl -s localhost:8080/actuator/prometheus | Select-String '^trainlog'
```

Счётчики хранятся в памяти бэкенда, поэтому после перезапуска пода они начинаются с нуля.
Для подсчёта за период это не мешает: `increase()` и `rate()` в MetricsQL учитывают сброс счётчика.

## Полезные команды

```powershell
kubectl -n monitoring logs vm-single-victoria-metrics-single-server-0      # ошибки скрейпинга
kubectl -n trainlog logs db-0 -c postgres-exporter                         # ошибки подключения экспортера
kubectl get cm vm-single-victoria-metrics-single-server-scrapeconfig -n monitoring -o yaml
helm uninstall vm-single -n monitoring
```
