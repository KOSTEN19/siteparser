# Parsesite Search Platform (MVP)

Distributed search pipeline with crawler, scheduler, indexing, analytics and UI/API.

## Stack
- Linux (Ubuntu/Debian)
- OpenJDK 8 + Spring Boot 2.7
- Docker + Docker Compose
- Elasticsearch + Kibana
- RabbitMQ
- PostgreSQL

## Services
- `scheduler-service`: creates crawl tasks, retries failed URLs with exponential backoff.
- `crawler-service`: downloads pages, captures ETag/Last-Modified, stores raw payload, pushes indexing tasks.
- `indexer-service`: cleans HTML, detects language, extracts entities, writes searchable docs to Elasticsearch.
- `analytics-service`: computes sentiment/topic/summary/simhash and enriches indexed docs.
- `api-service`: protected search API + simple query UI and role-based access.

## Run
```bash
docker compose up --build
```

Endpoints:
- API/UI: `http://localhost:8080`
- Kibana: `http://localhost:5601`
- RabbitMQ UI: `http://localhost:15672`
- Prometheus: `http://localhost:9090`

## RBAC users
- `admin/admin` (admin)
- `analyst/analyst` (analytics endpoints)
- `viewer/viewer` (search only)

## Pipeline
1. Scheduler programmatically declares and publishes to `tasks.queue`.
2. Crawler consumes via RabbitMQ `basicConsume` (manual ack) with multi-thread workers.
3. Seed tasks parse `https://www.securityvision.ru/news/` and enqueue article links.
4. Article tasks parse `title`, `publishedAt`, `author`, `url`, `text`.
5. Crawler builds `documentId = sha256(publishedAt + "|" + url)` and skips duplicates.
6. Result is stored as `output/<id>.json` and published to `results.queue`.

## Reliability and operations
- Health and metrics: `/actuator/health`, `/actuator/prometheus`.
- Monitoring: Prometheus scrapes all services.
- Backups: `ops/backup.sh` for PostgreSQL dump and Elasticsearch snapshot.
- Restore: `ops/restore_postgres.sh`.
- Data integrity strategy: compare indexed `contentHash` with latest `raw_documents` hash and trigger reindex if mismatched.

## RabbitMQ mode requirements
- Consumer mode: `basicConsume` with manual ack.
- Pull mode: `basicGet` debug endpoint in crawler: `GET /debug/basic-get`.
