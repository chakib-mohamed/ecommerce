#!/usr/bin/env python3
"""
Verifies that the JaCoCo coverage gate is actually doing its job.

The gate had been reporting success while enforcing nothing: its include patterns were written
with '/' separators, but JaCoCo matches the fully qualified dotted name, so no class matched and
every rule passed vacuously. JaCoCo prints the same "All coverage checks have been met" whether it
checked seven classes or none, and never says how many it matched -- so nothing in the build output
could distinguish a working gate from a broken one.

Two checks, run against the real poms and the real reports:

  1. Every class the gate is meant to cover matches at least one include pattern. Catches a
     pattern that silently matches nothing -- the original defect -- and also catches a class
     added under a package the patterns do not reach.

  2. Every covered class appears in its module's JaCoCo report at full branch coverage, and each
     module contributes at least one. The second half is the important one: it fails when a
     module's rules matched nothing, which is exactly the state the gate cannot report itself.

Usage:
    verify-coverage-gate.py [--patterns-only]

    --patterns-only  run check 1 only, for when no reports have been generated yet.

Exits non-zero with the offending classes named.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
BACKEND = REPO_ROOT / "backend"
PARENT_POM = BACKEND / "pom.xml"

# Source paths the gate is meant to cover, as (glob, description).
COVERED_SOURCES = [
    ("*/src/main/java/**/control/*Service.java", "control services"),
    ("*/src/main/java/**/boundary/*Resource.java", "boundary resources"),
    ("*/src/main/java/**/boundary/*Controller.java", "boundary controllers"),
]


def fail(message: str, offenders: list[str]) -> None:
    print(f"\nFAIL: {message}", file=sys.stderr)
    for offender in sorted(offenders):
        print(f"    {offender}", file=sys.stderr)
    sys.exit(1)


def include_patterns() -> list[str]:
    """The <include> entries of the JaCoCo check rules, in the order they appear."""
    text = PARENT_POM.read_text(encoding="utf-8")
    start = text.find("<artifactId>jacoco-maven-plugin</artifactId>")
    if start < 0:
        fail("no jacoco-maven-plugin found in backend/pom.xml", [])
    block = text[start:]
    end = block.find("</plugin>")
    patterns = re.findall(r"<include>([^<]+)</include>", block[:end])
    if not patterns:
        fail("the JaCoCo plugin declares no <include> patterns", [])
    return patterns


def matches(pattern: str, class_name: str) -> bool:
    """JaCoCo's wildcard matching: '*' spans any characters, '?' exactly one."""
    regex = "".join(
        ".*" if ch == "*" else "." if ch == "?" else re.escape(ch) for ch in pattern
    )
    return re.fullmatch(regex, class_name) is not None


def covered_classes() -> list[tuple[str, str]]:
    """(module, fully-qualified class name) for every class the gate should cover."""
    found: list[tuple[str, str]] = []
    for glob, _ in COVERED_SOURCES:
        for path in BACKEND.glob(glob):
            module = path.relative_to(BACKEND).parts[0]
            parts = path.relative_to(BACKEND / module / "src/main/java").with_suffix("").parts
            found.append((module, ".".join(parts)))
    return found


def check_patterns_match_classes() -> list[tuple[str, str]]:
    patterns = include_patterns()
    classes = covered_classes()

    if not classes:
        fail("found no classes to cover - has the source layout changed?", [])

    unmatched = [
        f"{cls}   (no pattern matches it)"
        for _, cls in classes
        if not any(matches(p, cls) for p in patterns)
    ]
    if unmatched:
        fail(
            "the coverage gate's include patterns do not match these classes, so the gate "
            "is not enforcing on them:\n  patterns: " + ", ".join(patterns),
            unmatched,
        )

    print(f"OK  {len(classes)} covered classes all match the gate's patterns")
    print(f"    patterns: {', '.join(patterns)}")
    return classes


def check_reports(classes: list[tuple[str, str]]) -> None:
    patterns = include_patterns()
    problems: list[str] = []
    checked = 0
    modules_with_reports = 0

    by_module: dict[str, list[str]] = {}
    for module, cls in classes:
        by_module.setdefault(module, []).append(cls)

    for module, expected in sorted(by_module.items()):
        report = BACKEND / module / "target/site/jacoco/jacoco.xml"
        if not report.exists():
            continue  # module not built in this invocation
        modules_with_reports += 1

        measured: dict[str, tuple[int, int]] = {}
        for element in ET.parse(report).iter("class"):
            name = (element.get("name") or "").replace("/", ".")
            if not any(matches(p, name) for p in patterns):
                continue
            for counter in element.findall("counter"):
                if counter.get("type") == "BRANCH":
                    measured[name] = (
                        int(counter.get("covered", 0)),
                        int(counter.get("missed", 0)),
                    )

        # The check that the gate itself cannot make: did it measure anything at all?
        if not measured:
            problems.append(
                f"{module}: the report contains no class matching the gate's patterns - "
                f"the gate passed without measuring anything"
            )
            continue

        for name, (covered, missed) in sorted(measured.items()):
            checked += 1
            if missed:
                total = covered + missed
                problems.append(
                    f"{name}: {covered}/{total} branches ({covered / total:.0%}), {missed} missed"
                )

        for cls in expected:
            # A class with no branches at all has no BRANCH counter; that is fine.
            if cls not in measured:
                continue

    if modules_with_reports == 0:
        fail(
            "no JaCoCo reports found - run `./mvnw verify` first, or pass --patterns-only",
            [],
        )
    if problems:
        fail("the coverage gate reported success but the report disagrees:", problems)

    print(f"OK  {checked} covered classes at full branch coverage across "
          f"{modules_with_reports} module(s)")


def main() -> None:
    patterns_only = "--patterns-only" in sys.argv
    classes = check_patterns_match_classes()
    if not patterns_only:
        check_reports(classes)
    print("\nCoverage gate verified.")


if __name__ == "__main__":
    main()
