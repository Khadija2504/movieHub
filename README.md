# MovieHub

Streaming catalog, digital purchases/rentals and cinema ticket booking.

| Part | Folder | Stack |
|---|---|---|
| Backend | `MOVIEHUB/` | Java 17 · Spring Boot 4 · Spring Security · JPA · Flyway |
| Frontend | `moviehub-front/` | Angular 21 · Tailwind CSS 4 · Vitest |
| Database | docker-compose | PostgreSQL 16 (host port **5433**) + pgAdmin |

## Getting started

```bash
# 1. Environment
cp .env.example .env          # adjust values if needed

# 2. Database (+ pgAdmin on http://localhost:5050)
docker compose up -d

# 3. Backend → http://localhost:8080
cd MOVIEHUB
./gradlew bootRun

# 4. Frontend → http://localhost:4200
cd moviehub-front
npm install
npm start
```

The backend reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` and `TMDB_API_KEY`
from the environment. Defaults in `application.yml` match `docker-compose.yml`, so no setup is needed for local dev.

## Useful URLs

| URL | What |
|---|---|
| http://localhost:8080/api/health | API + DB health check |
| http://localhost:8080/swagger-ui.html | API documentation |
| http://localhost:5050 | pgAdmin (server: host `db`, port `5432`) |

## Conventions

- **Database schema** is managed by Flyway only (`MOVIEHUB/src/main/resources/db/migration`).
  Add a new `V<n>__description.sql` file; never edit an applied migration.
- **Backend packages**: `com.moviehub.<feature>` (controller, service, repository, entity, dto per feature),
  shared code in `com.moviehub.common` and `com.moviehub.config`.
- **Errors** are returned as `ApiError` JSON (`timestamp, status, error, message, path, fieldErrors`).
- **Frontend folders**: `core/` (singletons: services, guards, interceptors, models), `shared/` (reusable UI),
  `layouts/` (public / user / admin shells), `features/<area>/<page>/` (lazy-loaded pages).
- **Styling**: Tailwind with design tokens in `src/styles.css` (`bg-surface`, `text-muted`, `btn btn-primary`, `card`, `input`...).
