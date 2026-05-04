# URL Shortener

## 📝 Описание

URL Shortener — это REST API сервис для сокращения длинных URL-адресов. Сервис принимает оригинальный URL, генерирует для него короткий уникальный код и предоставляет возможность редиректа на исходную ссылку по короткому коду.

**Основные возможности:**

- ✂️ Сокращение длинных URL до коротких 6-символьных кодов
- 🔁 Идемпотентность: повторное сокращение того же URL возвращает уже существующую короткую ссылку
- ⏳ Опциональный срок действия (TTL) короткой ссылки в днях
- 📊 Подсчёт количества переходов по каждой короткой ссылке (`click_count`)
- 🗑️ Автоматическое удаление просроченных ссылок при первом обращении после истечения срока
- ↩️ HTTP 302 редирект на оригинальный URL

---

## 🛠 Технологический стек

| Категория | Технология |
|-----------|-----------|
| Язык | Java 17 |
| Фреймворк | Spring Boot 3.5.7 |
| Web | Spring Web (MVC, REST) |
| Persistence | Spring Data JPA, Hibernate |
| База данных | PostgreSQL 15 |
| Миграции | Liquibase |
| Валидация | Spring Boot Starter Validation (Jakarta Validation) |
| Утилиты | Lombok |
| Сборка | Maven (Maven Wrapper) |
| Тестирование | JUnit 5, Mockito, Spring Boot Test, Testcontainers (PostgreSQL) |
| Контейнеризация | Docker, Docker Compose |

---
## 🚀 Установка

1. Клонируйте репозиторий:

   ```bash
   git clone <repository-url>
   cd url-shortener
   ```

2. Соберите проект:

   - Linux/macOS:
     ```bash
     ./mvnw clean package
     ```
   - Windows:
     ```cmd
     mvnw.cmd clean package
     ```

   После сборки JAR будет в `target/url-shortener-0.0.1-SNAPSHOT.jar`.

4. Запустите PostgreSQL (см. раздел [Запуск](#-запуск)).

---

## ⚙️ Конфигурация

Конфигурация задаётся в `src/main/resources/application.yml` и может быть переопределена через переменные окружения Spring Boot (через `SPRING_*` префиксы или Java system properties).

### Параметры подключения к БД

| Переменная окружения | Свойство YAML | Значение по умолчанию | Описание |
|----------------------|---------------|------------------------|----------|
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | `jdbc:postgresql://localhost:5432/url_shortener` | JDBC URL базы данных |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `postgres` | Имя пользователя БД |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | `postgres` | Пароль пользователя БД |

### Параметры приложения

| Переменная окружения | Свойство YAML | Значение по умолчанию | Описание |
|----------------------|---------------|------------------------|----------|
| `SERVER_PORT` | `server.port` | `8080` | HTTP-порт приложения |
| `APP_BASE_URL` | `app.base-url` | `http://localhost:8080` | Базовый URL для построения коротких ссылок в ответе API |
| `APP_SHORT_CODE_CHARACTERS` | `app.short-code.characters` | `A–Z, a–z, 0–9` | Алфавит для генерации кода |
| `APP_SHORT_CODE_LENGTH` | `app.short-code.length` | `6` | Длина короткого кода |
| `SPRING_LIQUIBASE_ENABLED` | `spring.liquibase.enabled` | `true` | Включение/выключение миграций Liquibase |

> 💡 Файл `.env.example` в проекте отсутствует — TODO: добавить шаблон для удобства настройки окружения.

---

## ▶️ Запуск

1. Запустите PostgreSQL отдельно (например, через Docker):

   ```bash
   docker run --name url-shortener-pg \
     -e POSTGRES_DB=url_shortener \
     -e POSTGRES_USER=postgres \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 -d postgres:15
   ```

2. Запустите приложение через Maven:

   ```bash
   ./mvnw spring-boot:run
   ```

   Liquibase автоматически применит миграции при старте.

### 🐳 Docker / Docker Compose

`docker-compose.yaml` поднимает связку PostgreSQL + приложение.

```bash
# Сначала собрать JAR (Dockerfile использует уже собранный артефакт)
./mvnw clean package

# Затем поднять контейнеры
docker-compose up --build
```

После запуска:

- Приложение: http://localhost:8080
- PostgreSQL: localhost:5432

---

## 🔌 API Документация

Все эндпоинты доступны по базовому URL: `http://localhost:8080`.
Аутентификация **не требуется** — API публичное. (TODO: при необходимости добавить авторизацию.)

### URL Shortener

#### 🔹 POST `/shorten` — Создать короткую ссылку

Принимает оригинальный URL и (опционально) срок действия в днях. Возвращает короткий код и полную короткую ссылку. Если URL уже сокращался ранее, возвращается существующая запись.

**Тело запроса (JSON):**

| Поле | Тип | Обязательное | Описание |
|------|-----|--------------|----------|
| `url` | string | ✅ да | Оригинальный URL для сокращения (не пустой) |
| `expirationDays` | long | ❌ нет | Срок действия в днях. Если не указан или ≤ 0 — ссылка бессрочная |

**Пример запроса:**

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{
        "url": "https://example.com/very/long/path?with=query",
        "expirationDays": 7
      }'
```

**Пример ответа `200 OK`:**

```json
{
  "shortUrl": "http://localhost:8080/aB3xY9",
  "originalUrl": "https://example.com/very/long/path?with=query",
  "shortCode": "aB3xY9"
}
```

**Коды ответа:**

| Код | Описание |
|-----|----------|
| `200 OK` | Короткая ссылка создана или возвращена существующая |
| `400 Bad Request` | Не прошла валидация (например, пустой `url`) |
| `500 Internal Server Error` | Внутренняя ошибка сервера (исключение в сервисе) |

---

#### 🔹 GET `/{shortCode}` — Редирект на оригинальный URL

Перенаправляет (HTTP 302) на оригинальный URL, привязанный к указанному короткому коду. При каждом обращении инкрементируется счётчик `click_count`. Если у ссылки истёк срок действия — она удаляется и возвращается 404.

**Path-параметры:**

| Параметр | Тип | Описание |
|----------|-----|----------|
| `shortCode` | string | Короткий код (по умолчанию 6 символов: `A–Z`, `a–z`, `0–9`) |

**Пример запроса:**

```bash
curl -i http://localhost:8080/aB3xY9
```

**Пример ответа `302 Found`:**

```
HTTP/1.1 302 Found
Location: https://example.com/very/long/path?with=query
Content-Length: 0
```

**Коды ответа:**

| Код | Описание |
|-----|----------|
| `302 Found` | Редирект на оригинальный URL, в заголовке `Location` |
| `404 Not Found` | Короткий код не найден или ссылка просрочена и удалена |

---

## 🧪 Тестирование

В проекте присутствуют:

- **Unit-тесты** сервиса (`UrlShortenerServiceTest`) — Mockito + JUnit 5
- **Базовый smoke-тест** контекста Spring Boot (`UrlShortenerApplicationTests`)
- Зависимость **Testcontainers** (PostgreSQL) подключена для интеграционных тестов (TODO: интеграционные тесты пока отсутствуют)

Запуск всех тестов:

```bash
./mvnw test
```

Запуск с генерацией отчёта и сборкой:

```bash
./mvnw clean verify
```

---

## 📦 Деплой

В проекте есть базовая Docker-инфраструктура:

- `Dockerfile` — образ на базе `eclipse-temurin:17-jre`, копирующий собранный JAR
- `docker-compose.yaml` — связка приложения и PostgreSQL для локального запуска

> ⚠️ **TODO по деплою:**
> - Нет CI/CD конфигурации (GitHub Actions / GitLab CI)
> - Нет Kubernetes манифестов / Helm chart
> - `Dockerfile` требует предварительной сборки JAR — целесообразно перейти на multi-stage build, чтобы сборка происходила внутри Docker
> - Нет healthcheck-эндпоинта (Spring Boot Actuator не подключён)
> - Нет конфигурации для production-профиля (`application-prod.yml`)
> - Не настроены секреты — пароли БД хранятся в открытом виде в `docker-compose.yaml`

---

## 📌 Прочие заметки и TODO

- [ ] Добавить `.env.example` с переменными окружения
- [ ] Подключить Spring Boot Actuator для healthcheck/metrics
- [ ] Добавить интеграционные тесты с Testcontainers (зависимость уже подключена)
- [ ] Подключить OpenAPI/Swagger для интерактивной документации
- [ ] Глобальный обработчик исключений (`@ControllerAdvice`) вместо общего `try/catch` в контроллере
- [ ] Логирование запросов и метрики (Micrometer)
- [ ] Rate limiting на `/shorten`
- [ ] Авторизация / API-ключи при необходимости
