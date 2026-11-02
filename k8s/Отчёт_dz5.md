# SLI, SLO и SLA для TrainLog

Доступность считается по метрикам из ДЗ №4, которые уже собирает VictoriaMetrics, и по новому проберу
(blackbox-exporter). Используются оба подхода:

- **RBSLO** (Request Based) — доля хороших запросов среди всех: доступность и латентность API;
- **TBSLO** (Time Based) — доля хороших минут: доступность PostgreSQL и проверки пробера.

SLI считаются recording-правилами vmalert раз в минуту и пишутся обратно в VictoriaMetrics, графики
показаны на дашборде прямо в vmui, без Grafana.

## Что добавлено

| Что | Файл | Зачем |
|---|---|---|
| Бакеты гистограммы `http.server.requests`: 100 мс, 300 мс, 1 с | `backend/src/main/resources/application.yml` | считать долю запросов быстрее порога |
| Эндпоинт `/actuator/faults` | `backend/.../common/FaultInjection.java` | включать на лету ошибки и задержки в `/api/**` для скринкаста |
| Генератор нагрузки `loadgen` | `k8s/08-loadgen.yaml` | постоянный поток запросов, чтобы у RBSLO был знаменатель |
| Пробер: blackbox-exporter | `k8s/monitoring/blackbox.yaml`, job `blackbox` в `vm-values.yaml` | проверка сервиса снаружи, работает и когда бэкенд лежит |
| vmalert `vmalert-slo`: recording-правила SLI и алерты на сжигание бюджета | `k8s/monitoring/slo.yaml` | готовые ряды для SLO/SLA |
| Дашборд vmui | `k8s/monitoring/vmui-dashboards.yaml`, `extraArgs`/`extraVolumes` в `vm-values.yaml` | графики SLI, SLO, бюджета ошибок и SLA |

## Схема

```
 namespace trainlog                                  namespace monitoring
┌────────────────────────────────────┐              ┌──────────────────────────────────────────────┐
│ loadgen ──► frontend ──► backend   │◄── scrape ───┤ VictoriaMetrics single                       │
│  (curl)     (nginx)      :8080     │              │  ├ /actuator/prometheus, postgres-exporter   │
│                            │       │              │  ├ blackbox: /probe?target=...               │
│ db-0 ┌ postgres  ◄─────────┘       │              │  └ vmui + дашборд TrainLog: SLI, SLO, SLA    │
│      └ postgres-exporter :9187     │◄── scrape ───┤        ▲ remote write      │ запросы          │
└────────────────────────────────────┘              │        │                   ▼                  │
          ▲                                         │   vmalert-slo: recording-правила trainlog:*, │
          └──────── HTTP-проверки ──────────────────┤   алерты *BudgetBurn                         │
                                                    │   blackbox-exporter :9115                    │
                                                    └──────────────────────────────────────────────┘
```

## Выбранные метрики

| Метрика | Источник | Что показывает |
|---|---|---|
| `http_server_requests_seconds_count` | Micrometer в бэкенде (ДЗ №4) | число HTTP-запросов с метками `uri`, `method`, `status`, `outcome` |
| `http_server_requests_seconds_bucket` | то же, бакеты включены в этом ДЗ | сколько запросов уложилось в `le="0.1"`, `"0.3"`, `"1.0"` секунд |
| `pg_up` | postgres-exporter (ДЗ №4) | 1, если экспортер смог выполнить запрос к PostgreSQL |
| `probe_success` | blackbox-exporter (новый пробер) | 1, если HTTP-проверка вернула 2xx |

Запросы к `/actuator/*` (пробы kubelet, скрейпинг) из SLI исключены: это не пользовательский трафик.

### Пробер

Метрики бэкенда видят только те запросы, которые до бэкенда дошли. Если бэкенд упал, ошибок
в `http_server_requests` не будет вовсе: счётчики просто пропадут, и SLI по запросам останется
«зелёным». Поэтому нужен внешний пробер.

Blackbox-exporter по запросу VictoriaMetrics каждые 15 секунд проверяет два адреса модулем `http_2xx`:

| Цель | Что покрывает |
|---|---|
| `http://frontend.trainlog.svc.cluster.local/` | nginx отдаёт фронтенд |
| `http://backend.trainlog.svc.cluster.local:8080/actuator/health` | бэкенд жив и видит базу: общий health включает проверку `db`, при недоступной БД отвечает 503 |

Цели заданы в `vm-values.yaml`, relabeling переносит адрес цели в параметр `target`, а запрос
отправляет на `blackbox-exporter:9115/probe`.

## SLI

Все SLI считаются recording-правилами в `k8s/monitoring/slo.yaml` (группа `trainlog-sli`, интервал 1 минута).

### SLI-1. Доступность API (RBSLO)

Хороший запрос — ответ не 5xx. Ошибки клиента (400, 401, 404, 409) считаются хорошими: сервис отработал правильно.

```
trainlog:api_requests:increase1m    = sum(increase(http_server_requests_seconds_count{application="trainlog", uri!~"/actuator/.*"}[1m]))
trainlog:api_requests_ok:increase1m = sum(increase(http_server_requests_seconds_count{application="trainlog", uri!~"/actuator/.*", status!~"5.."}[1m]))
```

```
SLI₁(T) = Σ api_requests_ok / Σ api_requests  за окно T
        = sum_over_time(trainlog:api_requests_ok:increase1m[T]) / sum_over_time(trainlog:api_requests:increase1m[T])
```

### SLI-2. Латентность API (RBSLO)

Хороший запрос — обработан быстрее 300 мс. Порог совпадает с бакетом `le="0.3"`, поэтому SLI считается
точно, без интерполяции квантилей.

```
trainlog:api_requests_fast:increase1m = sum(increase(http_server_requests_seconds_bucket{application="trainlog", uri!~"/actuator/.*", le="0.3"}[1m]))

SLI₂(T) = sum_over_time(trainlog:api_requests_fast:increase1m[T]) / sum_over_time(trainlog:api_requests:increase1m[T])
```

### SLI-3. Доступность PostgreSQL (TBSLO)

Минута хорошая, если на всех скрейпах за минуту `pg_up` был равен 1.

```
trainlog:db_up:minute = min(min_over_time(pg_up[1m])) or on() sum(max_over_time(up{app="backend"}[1m])) * 0

SLI₃(T) = хорошие минуты / все минуты = avg_over_time(trainlog:db_up:minute[T])
```

Вторая часть выражения нужна для случая, когда под `db-0` удалён: вместе с ним пропадает экспортер
и ряд `pg_up`, и без неё такие минуты просто не попали бы в расчёт. Если ряда `pg_up` нет, а бэкенд
в эту минуту скрейпился, минута записывается как 0. Опорой взят именно бэкенд, а не, например,
`up{job="victoriametrics"}`: бэкенд и экспортер находит один и тот же service discovery, поэтому сразу
после старта VictoriaMetrics они появляются одновременно и ложного нуля не бывает.

### SLI-4. Доступность по проберу (TBSLO)

Минута хорошая, если за минуту прошли все проверки обеих целей.

```
trainlog:probe_ok:minute = min(min_over_time(probe_success{job="blackbox"}[1m])) or on() sum(max_over_time(up{job="blackbox"}[1m])) * 0

SLI₄(T) = avg_over_time(trainlog:probe_ok:minute[T])
```

Если сам blackbox-exporter недоступен, скрейп падает (`up{job="blackbox"} = 0`), `probe_success` не пишется,
и минута тоже считается плохой.

## SLO

Окно — скользящие 30 дней (43 200 минут). Бюджет ошибок — `1 − SLO`.

| SLO | Цель | Бюджет ошибок за 30 дней |
|---|---|---|
| SLO-1 доступность API | SLI₁ ≥ **99,5 %** | 0,5 % запросов с ответом 5xx |
| SLO-2 латентность API | SLI₂ ≥ **95 %** запросов быстрее 300 мс | 5 % медленных запросов |
| SLO-3 доступность БД | SLI₃ ≥ **99,9 %** хороших минут | 43 минуты |
| SLO-4 доступность по проберу | SLI₄ ≥ **99,5 %** хороших минут | 216 минут (3 ч 36 мин) |

Остаток бюджета ошибок, в процентах:

```
budget_left = 100 × (1 − (1 − SLI(30d)) / (1 − SLO))
```

100 — бюджет не тронут, 0 — израсходован полностью, отрицательное значение — SLO нарушен.

### Алерты на сжигание бюджета

Группа `trainlog-slo` в том же vmalert. Алерт срабатывает, когда бюджет за последний час и за последние
5 минут расходуется в 14,4 раза быстрее равномерного (при такой скорости 2 % месячного бюджета уходит
за час). Короткое окно нужно, чтобы алерт гас сразу после устранения проблемы.

```
1 − SLI(1h) > 14.4 × (1 − SLO)  and  1 − SLI(5m) > 14.4 × (1 − SLO)
```

| Алерт | Порог доли плохих событий |
|---|---|
| `ApiAvailabilityBudgetBurn` | 7,2 % ответов 5xx |
| `ApiLatencyBudgetBurn` | 72 % запросов медленнее 300 мс |
| `DatabaseBudgetBurn` | 1,44 % плохих минут БД |
| `ProbeBudgetBurn` | 7,2 % плохих минут пробера |

## SLA

SLA — внешнее обязательство перед пользователями, поэтому оно мягче внутренних SLO: между ними остаётся
запас на реакцию команды. Считается за 30 дней (в договоре это был бы календарный месяц).

| Компонент | Формула | Порог |
|---|---|---|
| Доступность сервиса | SLI₄(30d), хорошие минуты по проберу | ≥ 99 % (не более 7 ч 12 мин простоя) |
| Успешные запросы | SLI₁(30d), доля ответов не 5xx | ≥ 99 % |

**Общий SLA** для набора: месяц считается выполненным, если выполнены оба компонента. Это то же самое,
что худший из компонентов не ниже 99 %:

```
SLA = min(SLI₄(30d), SLI₁(30d)) ≥ 99 %

100 * min(label_set(avg_over_time(trainlog:probe_ok:minute[30d]), "sli", "probe")
       or label_set(sum_over_time(trainlog:api_requests_ok:increase1m[30d]) / sum_over_time(trainlog:api_requests:increase1m[30d]), "sli", "api"))
```

`label_set` нужен, чтобы у двух рядов были разные метки, иначе `or` оставит только первый.

Пример формулировки для пользователей: «Сервис доступен не менее 99 % времени в месяц, и не менее 99 %
запросов к API завершаются без ошибки сервера. При нарушении — продление подписки на 10 % месяца».

Латентность и доступность БД в SLA не входят: медленная, но работающая страница не нарушает обещание
«сервис доступен», а недоступность БД пользователь видит через пробер и ошибки API.

## Графики

Дашборд `TrainLog: SLI, SLO, SLA` подключён к vmui флагом `-vmui.customDashboardsPath`, JSON лежит
в ConfigMap `vmui-dashboards`. Открывается на вкладке **Dashboards**:
http://localhost:8428/vmui/#/dashboards.

| Строка | Панели |
|---|---|
| SLI сейчас (5 минут) | доступность API, доля быстрых запросов, `probe_success` по целям, хорошие минуты БД |
| SLO (30 дней) | четыре SLI за 30 дней с линией цели |
| Бюджет ошибок | остаток бюджета по всем четырём SLO на одном графике |
| SLA (30 дней) | компоненты SLA с линией 99 % и общий SLA (худший из компонентов) |

Так как кластер учебный и истории за 30 дней нет, окно 30 дней фактически покрывает время с момента
установки vmalert-slo. Поэтому на свежем стенде даже несколько минут сбоя заметно двигают 30-дневные графики.

Алерты vmalert-slo видны в vmui на вкладке **Alerts** (флаг `-vmalert.proxyURL`) и в интерфейсе самого
vmalert.

## Изменения приложения для скринкаста

### Инъекция сбоев

`FaultInjection` — эндпоинт actuator и `HandlerInterceptor` для `/api/**` в одном классе. Настройки
хранятся в памяти пода и меняются без перезапуска:

| Параметр | Что делает |
|---|---|
| `errorRate` | доля запросов, на которые сразу возвращается `503 Service Unavailable` (0…1) |
| `delayMs` | задержка перед обработкой каждого запроса, мс |

Интерсептор работает после выбора обработчика, поэтому в метриках у искусственных ошибок правильная
метка `uri`, а сам ответ — обычный `ProblemDetail` через `ApiExceptionHandler`. Эндпоинт, как и
`/actuator/prometheus`, недоступен снаружи: nginx проксирует только `/api/`. После перезапуска пода
сбои выключаются.

```powershell
kubectl -n trainlog port-forward deploy/backend 8081:8080
```

В другом окне:

```powershell
$faults = 'http://localhost:8081/actuator/faults'
Invoke-RestMethod $faults
Invoke-RestMethod $faults -Method Post -ContentType 'application/json' -Body '{"errorRate": 0.2}'
Invoke-RestMethod $faults -Method Post -ContentType 'application/json' -Body '{"delayMs": 400}'
Invoke-RestMethod $faults -Method Post -ContentType 'application/json' -Body '{"errorRate": 0, "delayMs": 0}'
```

### Генератор нагрузки

`loadgen` — под с `curl`, который в цикле ходит в API через nginx, как браузер: входит под служебным
пользователем `loadgen@trainlog.local` (при первом запуске регистрирует его), читает список тренировок
и справочник, создаёт тренировку с подходом, читает подходы и удаляет тренировку. Получается 5–6
запросов в секунду. Каждый ответ ждёт до 40 секунд, чтобы при недоступной БД запрос доходил до таймаута
пула соединений и попадал в метрики как 500.

### Сценарий скринкаста

Перед записью открыть дашборд в vmui, выставить период **Last 30 minutes** и подождать 5–10 минут
нормальной работы, чтобы все графики были у 100 %. Новые точки появляются с задержкой 1–2 минуты:
правило считается раз в минуту, и ещё 30 секунд VictoriaMetrics не отдаёт свежие данные.

| # | Действие | Что видно на графиках |
|---|---|---|
| 1 | `{"errorRate": 0.2}` на 5 минут | доступность API за 5 минут падает до ~80 %, остаток бюджета SLO-1 уходит в минус, SLA-компонент «успешные запросы» опускается ниже 99 %, срабатывает `ApiAvailabilityBudgetBurn`. Пробер и БД остаются на 100 %: health-эндпоинты не идут через `/api` |
| 2 | `{"errorRate": 0, "delayMs": 400}` на 5 минут | доступность снова 100 %, а доля быстрых запросов падает почти до 0, бюджет SLO-2 расходуется, срабатывает `ApiLatencyBudgetBurn` |
| 3 | `kubectl -n trainlog scale statefulset db --replicas=0` на 3–4 минуты | `pg_up` пропадает, `trainlog:db_up:minute` = 0, проба `/actuator/health` получает 503, SLI пробера падает, API отвечает 500 после таймаута пула (30 с), падают все четыре SLI и SLA. Вернуть: `--replicas=1` |
| 4 | `kubectl -n trainlog scale deploy/backend --replicas=0` на 3–4 минуты | ряды `http_server_requests` пропадают, панели API без данных — по метрикам бэкенда не видно, что сервис лежит. Видно только по проберу: проверка бэкенда 0, SLI-4 и SLA падают. Вернуть: `--replicas=1` |

Сценарий 4 показывает, зачем нужен пробер: RBSLO по метрикам самого сервиса не замечает его полного
отсутствия.

Если на стенде уже были сбои (например, при отладке), 30-дневные SLI и бюджет начнутся не со 100 %.
Записанные ряды SLI можно удалить, vmalert-slo начнёт их заново со следующей минуты:

```powershell
Invoke-RestMethod 'http://localhost:8428/api/v1/admin/tsdb/delete_series' -Method Post -Body @{ 'match[]' = '{__name__=~"trainlog:.*"}' }
```

## Развёртывание

Команды для PowerShell из корня репозитория. Кластер и VictoriaMetrics установлены по ДЗ №2–4.

```powershell
minikube start
& minikube -p minikube docker-env --shell powershell | Invoke-Expression
docker compose -f docker-compose.yml build backend
kubectl apply -f k8s/
kubectl -n trainlog rollout restart deploy/backend

kubectl apply -f k8s/monitoring/blackbox.yaml -f k8s/monitoring/vmui-dashboards.yaml -f k8s/monitoring/slo.yaml
helm upgrade --install vm-single vm/victoria-metrics-single -n monitoring --create-namespace -f k8s/monitoring/vm-values.yaml
```

ConfigMap с дашбордом применяется до `helm upgrade`, потому что под VictoriaMetrics монтирует его при старте.
`kubectl apply -f k8s/monitoring/` целиком не подходит: в каталоге лежит `vm-values.yaml`, который не является манифестом.

## Проверка

```powershell
kubectl get pods -n trainlog
kubectl get pods -n monitoring
kubectl -n monitoring port-forward svc/vm-single-victoria-metrics-single-server 8428:8428
```

В поде `trainlog` должен появиться `loadgen`, в `monitoring` — `blackbox-exporter` и `vmalert-slo`.

```powershell
$vm = 'http://localhost:8428/api/v1'

(Invoke-RestMethod "$vm/query" -Body @{ query = 'probe_success' }).data.result |
  ForEach-Object { '{0} {1}' -f $_.metric.instance, $_.value[1] }

(Invoke-RestMethod "$vm/query" -Body @{ query = '{__name__=~"trainlog:.*"}' }).data.result |
  ForEach-Object { '{0} {1}' -f $_.metric.__name__, $_.value[1] }

(Invoke-RestMethod "$vm/alerts").data.alerts | Format-Table name, state
```

| Что | Адрес |
|---|---|
| дашборд | http://localhost:8428/vmui/#/dashboards |
| алерты | http://localhost:8428/vmui/#/rules |
| таргеты, включая пробы | http://localhost:8428/targets |

## Полезные команды

```powershell
kubectl -n trainlog logs deploy/loadgen
kubectl -n monitoring logs deploy/vmalert-slo
kubectl -n monitoring logs deploy/blackbox-exporter
kubectl -n monitoring port-forward svc/blackbox-exporter 9115:9115
```

На http://localhost:9115 blackbox-exporter показывает последние проверки и их логи, а
`http://localhost:9115/probe?module=http_2xx&target=http://frontend.trainlog.svc.cluster.local/&debug=true`
выполняет проверку вручную и выводит её подробности.
