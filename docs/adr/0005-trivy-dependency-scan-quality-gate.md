# ADR-0005: Trivy dependency scanning as a CI quality gate

- **Status:** Accepted
- **Date:** 2026-06-03
- **Deciders:** CHAKIB Mohamed
- **Related:** `.github/workflows/ci.yml`

## Context

The platform pulls a large transitive dependency tree across a Maven backend (seven services) and an
npm frontend. Nothing in CI checked those dependencies for known vulnerabilities, so a CVE in a
third-party library could ship unnoticed. We wanted automated dependency scanning wired into the
existing CI quality gate, without standing up a separate security service.

## Decision drivers

- Cover **both** ecosystems (Maven `pom.xml` and npm `package-lock.json`) in one pass.
- Make findings visible where the team already looks — the GitHub Security tab.
- **Fail the build** on the most serious issues without making the gate so noisy it gets ignored.

## Decision

Add a **Trivy filesystem scan** as a job in the CI quality gate (`.github/workflows/ci.yml`):

1. A single `trivy fs` scan over the repo covers the Maven backend and the npm frontend together.
2. **All severities** are reported as **SARIF** to the GitHub Security tab for visibility.
3. The build **fails only on `CRITICAL`** vulnerabilities — lower severities are surfaced but
   non-blocking, keeping the gate actionable rather than noisy.

The CRITICAL findings flagged on introduction were remediated as part of the same effort.

## Considered options

| Option | Decision | Why |
|---|---|---|
| Trivy `fs` scan, fail on CRITICAL, SARIF to Security tab | **Chosen** | One tool covers Maven + npm; native SARIF for GitHub Security; no account/key; fast |
| OWASP Dependency-Check | Rejected | Maven/Java-centric (no first-class npm pass in the same run); slow NVD database sync; noisier false-positive rate than Trivy for this stack |
| Snyk | Rejected | Best-in-class data but requires an account, an auth token in CI, and a paid plan for private/at-scale use — external-service dependency we didn't want for a single gate |
| GitHub Dependabot alerts only | Rejected | Surfaces alerts but isn't a build-failing gate; no single SARIF artifact spanning both ecosystems on each run |
| No dependency scanning | Rejected | CVEs in transitive deps ship undetected |
| Fail on HIGH+ (or all) | Rejected | Too noisy for the current stage; would erode trust in the gate and get bypassed |
| Separate per-ecosystem scanners | Rejected | More moving parts than a single `fs` scan that handles both |

## Consequences

**Positive**
- Known-vulnerable dependencies are caught in CI before merge; findings live in the GitHub Security
  tab.
- One scanner covers both ecosystems — minimal CI surface.

**Negative / costs**
- Only `CRITICAL` blocks today; HIGH/MEDIUM findings rely on someone reviewing the Security tab. The
  threshold should be tightened as the dependency surface is cleaned up.
- Adds a step to every CI run and depends on the upstream vulnerability database freshness.
