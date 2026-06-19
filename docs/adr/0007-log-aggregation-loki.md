# ADR-0007: Log aggregation — Grafana Loki, fed OTLP through the Collector

- **Status:** Accepted
- **Date:** 2026-06-14
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/specs/log-aggregation.md`, `docs/tasks/log-aggregation-plan.md`,
  `docs/adr/0001-observability-tracing-and-metrics-stack.md`

## Context

ADR-0001 gave the platform distributed tracing (Jaeger) and metrics (Prometheus + Grafana)
through an OpenTelemetry Collector, and put `traceId`/`spanId` on every log line. It explicitly
left **log aggregation out of scope** — logs stay on container **stdout**, and the only way to
search them is `grep` across `make logs`. That has clear limits:

- **No central store / retention.** Container stdout is ephemeral; logs vanish on restart.
- **No real search.** No query by service, level, time window, or structured field; no UI.
- **No log↔trace click-through.** A log line carries a `traceId`, but nothing turns that into a
  one-click jump to the trace in Jaeger (or back).

We have 6 backend services (1 Spring Boot gateway + 5 Quarkus) already exporting OTLP to a
central `otel-collector`, and Grafana already running with Prometheus + Jaeger datasources. We
want centralized, queryable logs that plug into that existing setup with minimal new surface.

## Decision drivers

- **One pane of glass.** Reuse Grafana rather than introduce a second visualization UI.
- **Keep local/dev infra lean** — minimize new components and resource footprint.
- **Structured trace correlation** — `traceId`/`spanId` should be queryable fields and pivot to
  Jaeger, not text we re-parse.
- **Reuse the existing OTLP pipeline** rather than maintain a parallel log-shipping path.
- **A clear scale-up path** that does not require re-architecting the application side later.

## Decision

Adopt **Grafana Loki** as the log backend, fed **OTLP logs through the existing OTel
Collector**, visualized in Grafana beside Prometheus and Jaeger. Concretely:

1. **Loki over ELK.** Loki indexes only **labels** and stores compressed log chunks in object
   storage; Grafana queries it via LogQL. Because Grafana already runs, Loki is one more
   datasource — no second UI (Kibana). Loki is far lighter than Elasticsearch's JVM +
   full-content indexing, which matters for a local Docker stack, and our logs are structured
   key=value, scoped by `service`/`trace_id`/time — Loki's sweet spot. ELK's edge is heavy
   free-text search across unstructured content, which our usage does not need.

2. **OTLP-native shipping, through the Collector we already run.** Services emit logs over OTLP
   to `otel-collector:4317`; the Collector gains a **logs pipeline** that exports to Loki's
   native OTLP endpoint via the `otlphttp` exporter (`http://loki:3100/otlp`). This reuses the
   exact pipeline pattern ADR-0001 set up for traces, and `traceId`/`spanId` arrive as
   **structured fields** (Loki structured metadata) with no regex parsing. Per service:
   Quarkus turns on `quarkus.otel.logs.enabled=true` (reusing the existing OTLP exporter
   endpoint); the gateway uses Spring Boot 3.4's OTLP logging auto-config + the OpenTelemetry
   Logback appender.

3. **stdout stays on.** OTLP log export is **additive** — the console handler is untouched, so
   `make logs` and local console debugging keep working exactly as before.

4. **Trace correlation both ways.** The Grafana Loki datasource gets a `derivedFields` rule
   mapping `trace_id` → the Jaeger datasource (log → trace); the Jaeger datasource gets
   `tracesToLogsV2` → the Loki datasource (trace → logs).

5. **Single-binary now; scale-up is config-only.** Ship Loki in monolithic single-binary mode
   with filesystem storage and the **TSDB** schema (`allow_structured_metadata` on). Keep
   `storage_config`/`schema_config` isolated so growing to the Simple Scalable Deployment (or
   microservices) topology and swapping `filesystem` → `s3` is a localized config change — the
   application side (services → Collector → Loki) never changes.

## Considered options

| Area | Chosen | Rejected | Why rejected |
|---|---|---|---|
| Log backend | Grafana Loki | Elasticsearch + Kibana (ELK) | Second heavy UI; JVM + full-content indexing is overkill for a local stack and for our structured, label-scoped logs |
| Log backend | Grafana Loki | Grafana Tempo / others | Tempo is traces, not logs; Loki is the log store in the Grafana stack |
| Shipping path | OTLP-native via the existing Collector | Alloy/Promtail scraping container stdout | Needs pipeline stages to re-parse `traceId` from text and adds a component; Promtail is also being retired in favour of Alloy |
| Collector → Loki | `otlphttp` exporter → Loki native OTLP | Deprecated `loki` exporter | The Collector's `loki` exporter is deprecated/removed; Loki 3.x ingests OTLP natively |
| Gateway logs | Spring Boot 3.4 OTLP logging + Logback appender | Hand-rolled OTLP log appender wiring | Spring Boot 3.4 auto-configures it; only the appender bridge dependency is needed |
| Topology | Single-binary, filesystem, TSDB | Simple Scalable / S3 from day one | More moving parts + heavier footprint than a local stack needs; scale-up is config-only later |

## Consequences

**Positive**
- Centralized, queryable logs (LogQL) with retention surviving restarts (the `loki_data` volume).
- `traceId`/`spanId` as structured fields → one-click log↔trace pivot with Jaeger, in the same
  Grafana the team already uses.
- Reuses the existing OTLP pipeline — no parallel log-shipping agent to maintain; the only app
  change is one property per Quarkus service plus the gateway's OTLP logging config + one dep.
- stdout is unaffected, so existing console/`make logs` workflows are preserved.
- Portable: emitting OTLP keeps us free to swap Loki for another OTLP-capable log store later
  without touching services.

**Negative / costs**
- One more container to run locally (`loki`) under the `observability` profile.
- Single-binary Loki scales vertically only; high-volume production needs the Simple Scalable /
  microservices topology and object storage (config-only change, but real ops work when it comes).
- Loki is weak at heavy free-text search across unstructured content — acceptable because our
  logs are structured and queried by label/`trace_id`/time; if that need ever arises, ELK would
  be the better tool for that slice.
- Two log destinations now (stdout + Loki) — accepted; stdout is the local-debug fallback and
  costs nothing.

### Scaling path

Loki scales to very high volume (it backs Grafana Cloud at petabyte/day); whether it scales is a
function of **deployment topology**, and the **application side never changes**:

| Mode | Storage | Scales | Use |
|------|---------|--------|-----|
| Monolithic / single-binary (this ADR) | filesystem | vertical | dev + modest prod |
| Simple Scalable (read/write/backend) | object store (S3/GCS) | horizontal | prod up to ~TB/day |
| Microservices | object store | horizontal, fine-grained | very large scale |

Growing up means: swap the single binary for SSD targets (or the Loki Helm chart in the existing
`kubernetes/` manifests) and the `filesystem` store for `s3` (LocalStack already provides local
S3) — localized to `loki-config.yaml`. The OTel Collector is the scaling seam: it batches and can
fan out / load-balance to a clustered Loki and drop noisy logs, all without touching any service.
