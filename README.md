# Parsesite Search Platform (MVP)

Distributed crawler pipeline optimized for low-memory hosts.

## Stack
- Linux (Ubuntu/Debian)
- OpenJDK 8 + Spring Boot 2.7
- Docker + Docker Compose
- Elasticsearch + Kibana
- RabbitMQ
- PostgreSQL

## Runtime Modes
- **Lightweight default (2 GB RAM):** `scheduler-service`, `crawler-service`, `rabbitmq`, `postgres`.
- **Full profile:** adds `elasticsearch`, `kibana`, `indexer-service`, `analytics-service`, `api-service`, `prometheus`.

## Quick Start (2 GB RAM)
1) Prebuild only required jars:
```bash
./ops/prebuild.sh
```
2) Start lightweight stack:
```bash
docker compose up -d
```

Endpoints (lightweight):
- Crawler debug basicGet: `http://localhost:8082/debug/basic-get`
- RabbitMQ UI: `http://localhost:15672`
- PostgreSQL: `localhost:5432`

## Full Stack (optional)
```bash
docker compose --profile full up -d --build
```
Endpoints (full):
- API/UI: `http://localhost:8080`
- Kibana: `http://localhost:5601`
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
- Lightweight mode keeps only required services for TЗ 1/2.
- Backups: `ops/backup.sh` for PostgreSQL dump and Elasticsearch snapshot.
- Restore: `ops/restore_postgres.sh`.

## RabbitMQ mode requirements
- Consumer mode: `basicConsume` with manual ack.
- Pull mode: `basicGet` debug endpoint in crawler: `GET /debug/basic-get`.
