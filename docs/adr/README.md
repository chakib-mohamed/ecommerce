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
| [0002](0002-transactional-outbox-and-mongo-replica-set.md) | Transactional outbox for event publishing (+ Mongo replica set) | Accepted |
