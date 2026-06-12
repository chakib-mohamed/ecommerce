# ADR-0001: Observability — tracing & metrics stack

- **Status:** Accepted
- **Date:** 2026-06-09
- **Deciders:** CHAKIB Mohamed
- **Related:** `docs/specs/observability.md`, `docs/tasks/observability-plan.md`

## Context

The platform is 7 backend services (1 Spring Cloud Gateway + 6 Quarkus) communicating over HTTP
(through the gateway) and Kafka. Before this decision, the only correlation mechanism was a
hand-rolled `X-Request-ID`: the gateway minted a UUID per request, forwarded it as a header, and
each Quarkus service stamped it on log lines via MDC. This has real limits:

- It is **not a span tree** — no parent/child timing, no service map, no latency breakdown.
- It **breaks on outbound calls** — orders-service → products/price-service did not forward the id
  (no client filter existed), so correlation was lost across service boundaries.
- It **breaks at Kafka** — no context was attached to messages.
- There were **no metrics** exported anywhere, and **no observability backend** in the stack. The
  gateway carried inert `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` dependencies
  that were never configured.

We need true distributed tracing (one connected trace per request, across HTTP and Kafka) plus
per-service metrics, with a local backend developers can run.

## Decision drivers

- One correlation identity that spans every hop, **including Kafka**, with minimal hand-written code.
- Vendor-neutral, cross-language wire format (services are JVM today, but we want no lock-in).
- Keep local/dev infrastructure lean — avoid components we don't yet need.
- Prefer the most mature path for each observability signal in the Spring/Quarkus ecosystem.

## Decision

Adopt **OpenTelemetry (OTel) for tracing** and **Micrometer + Prometheus for metrics**,
visualized in **Grafana**, with **Jaeger** as the trace backend. Concretely:

1. **OpenTelemetry + OTLP is the tracing standard.** Every service emits spans via OTel and exports
   them over **OTLP**. OTel's default **W3C `traceparent`** propagation and auto-instrumentation
   (JAX-RS, MicroProfile Rest Client, JDBC/Mongo, SmallRye Reactive Messaging/Kafka) close the
   outbound-HTTP and Kafka correlation gaps **without** bespoke client filters or producer/consumer
   header code.

2. **Jaeger is the trace backend, over Zipkin.** Jaeger ingests OTLP **natively** (gRPC 4317 /
   HTTP 4318). Zipkin does not — it would require translation or non-OTLP per-service exporters.

3. **Route traces through an OpenTelemetry Collector, for tail sampling.** Services do not export to
   Jaeger directly; they export OTLP to a central `otel-collector` (the **contrib** distro, which
   ships the `tail_sampling` processor), which forwards to Jaeger. The Collector earns its keep
   through **tail sampling**: it buffers the whole trace before deciding, so it keeps 100% of error
   and slow traces and samples only ~10% of routine ones. Head sampling at the source cannot do this
   — it drops blindly, errors included. Consequently services sample at `always_on` (the Collector
   sees every span and owns the keep/drop decision). The Collector takes the host OTLP ports
   (4317/4318); Jaeger's OTLP listener stays internal (`jaeger:4317`) for the Collector→Jaeger hop.
   The Collector is config-only (no application code) and also leaves room for later routing /
   multi-exporter without touching services.

4. **Split observability by signal.** **Micrometer → Prometheus** for metrics
   (`micrometer-registry-prometheus` on the gateway via Actuator;
   `quarkus-micrometer-registry-prometheus` on Quarkus); **OTel** for traces. On the **gateway**,
   Spring's tracing API is Micrometer-Tracing, so we keep that API but swap its implementation from
   Brave/Zipkin to the **OTel SDK** via `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`,
   so its spans converge with the Quarkus services' OTel spans in one Jaeger trace.

5. **Retire `X-Request-ID`; standardize on `traceId`.** OTel places `traceId`/`spanId` in the MDC of
   *every* request regardless of sampling (sampling governs only export, not id generation), so the
   hand-rolled `requestId` is redundant for internal correlation and — unlike `requestId` — links
   into Jaeger. We remove the `requestId` MDC plumbing from the 6 Quarkus filters and the gateway
   `RequestIdGlobalFilter`, surface `traceId`/`spanId` in the log format, and (if a client-facing
   handle is wanted) echo `traceId` back in a gateway response header.

## Considered options

| Area | Chosen | Rejected | Why rejected |
|---|---|---|---|
| Tracing API/standard | OpenTelemetry + OTLP | Brave/Sleuth (Zipkin-native) | No cheap Kafka propagation; JVM/Spring-centric; not the cross-language standard |
| Trace backend | Jaeger (native OTLP) | Zipkin | No native OTLP ingest → would force a Collector or non-OTLP exporters |
| Collector | OTel Collector (contrib) in front of Jaeger | None (direct OTLP→Jaeger) | Direct export forces head sampling at the source, which drops error traces blindly; the Collector enables tail sampling |
| Sampling | Tail sampling at the Collector (`always_on` at source) | Head-based ratio at the source | Head sampling decides before the trace completes, so it cannot guarantee keeping errors/slow traces |
| Metrics | Micrometer + Prometheus | OTel metrics | Micrometer/Prometheus is the more mature path in Spring/Quarkus today; richer Grafana ecosystem |
| Correlation id | `traceId` only | Keep `X-Request-ID` in parallel | Redundant once `traceId` is in MDC for all requests; two ids add log noise and upkeep |

## Consequences

**Positive**
- One connected trace per request across HTTP **and** Kafka; the two prior correlation gaps close
  via auto-instrumentation rather than maintained glue code.
- `traceId`/`spanId` on every log line, pivotable to Jaeger.
- Per-service metrics in Prometheus + Grafana; the previously inert gateway tracing deps are put to use.
- OTLP keeps us portable — swapping Jaeger for Grafana Tempo later is a compose change, not code.
- Tail sampling at the Collector keeps every error and slow trace while sampling routine ones, so the
  traces most worth having are never dropped; the Collector is also a seam for future routing/exporters.

**Negative / costs**
- New infra to run locally (OTel Collector, Jaeger, Prometheus, Grafana) — placed under an
  `observability` compose profile so `make infra` stays lean.
- The Collector is one more component to run and configure, and it buffers traces in memory for
  `decision_wait` before exporting (a small added latency before a trace is visible in Jaeger).
- Services must export at `always_on` so the Collector sees every span; tail sampling needs all spans
  of a trace on one Collector instance — fine for the single Collector here, but scaling out later
  would require a load-balancing exporter in front.
- Removing `requestId` touches all 6 service filters and the gateway; any external consumer that
  relied on `X-Request-ID` must move to the echoed `traceId` response header.
- Two libraries in play (Micrometer for metrics, OTel for traces) — accepted as the mature-per-signal
  trade-off; the only contact point is the gateway's Micrometer-Tracing→OTel bridge.
