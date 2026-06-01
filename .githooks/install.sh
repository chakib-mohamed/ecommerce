#!/usr/bin/env sh
# One-time activation of the repo's version-controlled git hooks.
# Run once per clone: sh .githooks/install.sh
set -e

git config core.hooksPath .githooks
echo "Git hooks activated (core.hooksPath -> .githooks)."
echo "  pre-merge-commit -> Checkstyle + SpotBugs + backend unit tests before a merge commit into main."
