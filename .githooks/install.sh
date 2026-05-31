#!/usr/bin/env sh
# One-time activation of the repo's version-controlled git hooks.
# Run once per clone: sh .githooks/install.sh
set -e

git config core.hooksPath .githooks
echo "Git hooks activated (core.hooksPath -> .githooks)."
echo "The pre-push hook now runs Checkstyle + SpotBugs before every push."
