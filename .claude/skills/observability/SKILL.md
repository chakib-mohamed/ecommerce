---
name: observability
description: The local tracing, metrics, and log-aggregation stack (OTel Collector, Jaeger, Prometheus, Loki, Grafana) — dashboard URLs, how traces and logs correlate, sampling behavior, and which meters each service records. Use when working on tracing, metrics, logging, or the observability Compose profile, or when debugging a request across services.
---

# Observability

Distributed tracing, metrics, and logs across all 8 backend services. The local stack — **OTel
Collector + Jaeger + Prometheus + Loki + Grafana** — runs under the `observability` Docker Compose
profile (`make observability`, also folded into `make up`).

## UIs

| UI | URL | Purpose |
|----|-----|---------|
| Jaeger | http://localhost:16686 | traces |
| Prometheus | http://localhost:9090 | metrics + targets |
| Loki | http://localhost:3100 | log store (query via Grafana Explore; `/ready` health) |
| Grafana | http://localhost:3000 | dashboards + log search (anon admin; *Ecommerce Overview* + *Ecommerce Business KPIs* auto-provisioned) |

## How the signals fit together

- Every service exports OTLP to `otel-collector:4317`; the Collector **tail-samples** (keeps all
  error/slow traces, ~10% of the rest) and forwards to Jaeger. One request is one connected trace
  across the gateway, downstream HTTP calls, **and** Kafka (including outbox-published events).
- Metrics are scraped by Prometheus from each service (gateway `/actuator/prometheus`, Quarkus
  `/q/metrics`) — config in `observability/prometheus.yml`; Grafana provisioning in
  `observability/grafana/provisioning/`.
- Logs ship over **OTLP** to the Collector, which forwards them to **Loki** (Quarkus
  `quarkus.otel.logs.enabled=true`; gateway via Spring Boot OTLP logging + the Logback appender).
  `traceId`/`spanId` arrive as **structured metadata**, so Grafana pivots both ways — log → trace
  (Loki `derivedFields` → Jaeger) and trace → log (Jaeger `tracesToLogsV2` → Loki). Console/stdout
  logging is unchanged (`make logs` still works). **`X-Request-ID` is retired** — the gateway echoes
  the trace id back as an `X-Trace-Id` response header. Spec: `docs/specs/log-aggregation.md`.
- Beyond the auto-instrumented RED/JVM/Kafka signals, the business-owning Quarkus services
  (`authenticate`, `products`, `orders`, `price`, `payment`) record curated **functional/business
  meters** in their control layer (orders/revenue, auth success/failure, catalog mutations,
  pricing/discounts, captures/declines/gateway faults), surfaced on the *Ecommerce Business KPIs*
  dashboard. Full catalog: `docs/specs/functional-metrics.md`.
- **Alerting rules** live in `observability/rules/*.rules.yml`, loaded via `rule_files` in
  `observability/prometheus.yml` and validated in CI by the `observability-config` job. They are
  evaluated and visible in Prometheus and Grafana, but **there is no Alertmanager**, so nothing is
  delivered to a person. Rules are written against the *rendered* Prometheus names (dots become
  underscores, counters gain `_total`); `MetricNamesOnTheWireTest` in orders-service and
  payment-service pins those names, because an alert on a metric that does not exist never fires.

## Per-service wiring

All 7 Quarkus services use `quarkus-opentelemetry` (tracing) + `quarkus-micrometer-registry-prometheus`
(metrics); the gateway uses `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`.

- **Tracing** — OTLP endpoint `http://otel-collector:4317`, sampler `quarkus.otel.traces.sampler=always_on`
  (the Collector tail-samples). Auto-instrumentation covers JAX-RS, Rest Client, JDBC/Mongo, and
  SmallRye Reactive Messaging, so HTTP and Kafka spans join one trace with no hand-written propagation.
- **Logs** — `quarkus.log.console.format` stamps `traceId=%X{traceId} spanId=%X{spanId}` (OTel
  populates MDC automatically). `%test…sdk.disabled=true` silences OTLP export in tests.

## Related docs

Specs: `docs/specs/observability.md`, `docs/specs/functional-metrics.md`,
`docs/specs/log-aggregation.md`. Logging/correlation rules: `docs/conventions/logging-conventions.md`.
