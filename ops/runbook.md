# Operations Runbook

## Monitoring and alerts
- Scrape metrics from all services via Prometheus (`/actuator/prometheus`).
- Alert conditions (configure in Alertmanager/Grafana):
  - Service health endpoint not `UP` for 2+ minutes.
  - Queue lag growth on `crawl.queue`, `index.queue`, `analyze.queue`.
  - Elasticsearch indexing errors > 5/min.
  - PostgreSQL connection failures > 1/min.

## Data integrity checks
- Daily check: compare latest `raw_documents.content_hash` with indexed `contentHash`.
- If mismatch ratio > 1%, run reindex batch for affected IDs.
- Weekly check: sample query relevance and duplicate detection quality.

## Backup policy
- PostgreSQL dump every 6 hours, keep 14 days.
- Elasticsearch snapshot every 6 hours, keep 14 days.
- Test restore weekly in staging.

## Incident response
1. Stop scheduler from emitting new seeds.
2. Keep in-flight queue processing.
3. If ES unavailable, pause indexer/analytics and buffer in RabbitMQ.
4. Restore from latest backup if corruption detected.
5. Resume scheduler with reduced crawl rate.
