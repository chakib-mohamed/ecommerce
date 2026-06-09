# ADR-0003: Java 21 LTS as the backend baseline

- **Status:** Accepted
- **Date:** 2026-06-03
- **Deciders:** CHAKIB Mohamed

## Context

The backend (the Spring Cloud Gateway plus the six Quarkus services) targeted **Java 17**, with the
compiler level set per-module and the runtime Docker images on a 17 JRE. Java 21 is the current LTS,
and both Quarkus 3.x and Spring Boot 3.x fully support it. Staying on 17 left newer language and JVM
features unused and pushed the eventual upgrade further into the future, where it would compound with
other changes.

## Decision drivers

- Run on a **current LTS** so the platform stays on a supported, security-patched baseline.
- Centralize the version so all modules move together — no per-module drift.
- Surface compatibility breakage **once, deliberately**, rather than discovering it piecemeal later.

## Decision

Adopt **Java 21 (LTS)** as the backend baseline:

1. Centralize `maven.compiler.source`/`target=21` in the backend **parent POM** and drop the
   per-module 17 overrides, so every Quarkus module inherits it. Bump the gateway's `java.version`
   (it sits under the separate Spring Boot parent) and move the six JVM Dockerfiles to
   `eclipse-temurin:21-jre-alpine`. CI builds on JDK 21 to match the compiler target.
2. **Upgrade Drools 7.60 → 8.44** in price-service as a forced consequence: Drools 7.x bundled
   `mvel2` 2.4.x, whose `AbstractParser` referenced `java.lang.Compiler` — a class **removed in JDK
   21** — causing `NoClassDefFoundError` at rule-compilation time. Drools 8.44 ships `mvel2` 2.5.x,
   which dropped that reference. The classic `KieServices`/`KieBuilder` API is unchanged.

The native (`ubi8`) Dockerfiles were left untouched in this change.

## Considered options

| Option | Decision | Why |
|---|---|---|
| Java 21 (LTS) | **Chosen** | Current LTS; supported by Quarkus 3.x and Spring Boot 3.x; one forced dep upgrade (Drools) |
| Stay on Java 17 | Rejected | Still LTS but a generation behind; defers an upgrade that only gets larger |
| Java 22+ (non-LTS) | Rejected | Shorter support window; not a stable baseline for a platform |

## Consequences

**Positive**
- Supported current-LTS baseline across the whole backend; the version lives in one place.
- Newer JVM/language features available uniformly.

**Negative / costs**
- Forced the Drools upgrade in price-service (verified: `verify` passes on Java 21 — tests,
  Checkstyle, SpotBugs, JaCoCo). Any future library still referencing JDK-removed APIs will surface
  the same way and must be upgraded.
