# Architecture Decision Records

This directory holds **Architecture Decision Records (ADRs)** — short documents that capture a
significant architectural choice, the context that forced it, the options considered, and the
consequences accepted.

## Conventions

- **One decision per file**, named `NNNN-<kebab-slug>.md` (zero-padded sequence, e.g. `0001-...`).
- **Numbers are immutable** — never renumber or delete an ADR. To reverse a decision, write a new
  ADR with status `Accepted` and mark the old one `Superseded by ADR-NNNN`.
- **Status** is one of `Proposed`, `Accepted`, `Superseded by ADR-NNNN`, `Deprecated`.
- Keep it short and decision-focused. Implementation detail belongs in `docs/specs/` and
  `docs/tasks/`; coding rules belong in `docs/conventions/`. An ADR records *why we chose this*.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-observability-tracing-and-metrics-stack.md) | Observability: tracing & metrics stack | Accepted |
| [0002](0002-transactional-outbox-and-mongo-replica-set.md) | Transactional outbox for event publishing (+ Mongo replica set) | Accepted |
| [0003](0003-java-21-lts-baseline.md) | Java 21 LTS as the backend baseline | Accepted |
| [0004](0004-testcontainers-over-dev-services.md) | Testcontainers over Quarkus Dev Services | Accepted |
| [0005](0005-trivy-dependency-scan-quality-gate.md) | Trivy dependency scanning as a CI quality gate | Accepted |
| [0006](0006-make-and-compose-profiles-build-interface.md) | Make + Docker Compose profiles as the build/run interface | Accepted |
| [0007](0007-log-aggregation-loki.md) | Log aggregation: Grafana Loki, fed OTLP through the Collector | Accepted |
| [0008](0008-e2e-target-localhost-81.md) | E2E suite drives the built frontend container, not the Vite dev server | Accepted |
| [0009](0009-playwright-e2e-suite-and-ci-gating.md) | Playwright for end-to-end browser testing, gated in CI | Accepted |
| [0010](0010-analytics-service-event-sourced-warehouse.md) | Analytics as a dedicated service over an event-sourced read-model warehouse | Proposed |
