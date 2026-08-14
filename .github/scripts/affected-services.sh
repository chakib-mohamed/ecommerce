#!/usr/bin/env bash
#
# Maps changed repository paths to the backend services CI must verify.
#
# Reads changed paths on stdin (one per line), writes the affected service names to
# stdout (one per line, sorted). Empty output means no backend service is affected.
#
# The mapping mirrors each service's pom: a change to a shared module has to re-verify
# every service that depends on it, because those services compile against it.
#
# Bias: when a path is ambiguous or unrecognised under backend/, select everything.
# The failure mode of a filter is silently under-testing, so it errs toward doing more
# work rather than less.
#
# Test it without CI:
#   printf 'backend/outbox-common/pom.xml\n' | .github/scripts/affected-services.sh
set -euo pipefail

ALL_SERVICES=(
  authenticate-service
  featured-products-service
  price-service
  payment-service
  orders-service
  products-service
  analytics-service
  ecommerce-api-gateway
)

# Shared module -> services that depend on it. Derived from the <dependency> blocks in
# each service's pom.xml; keep in sync when a service starts or stops using one.
dependents_of() {
  case "$1" in
    products-api)  echo "featured-products-service price-service orders-service products-service analytics-service" ;;
    orders-api)    echo "price-service payment-service orders-service products-service analytics-service" ;;
    outbox-common) echo "price-service payment-service orders-service products-service" ;;
    *)             echo "" ;;
  esac
}

selected=()

add() {
  local svc
  for svc in $1; do
    [ -n "$svc" ] || continue
    case " ${selected[*]:-} " in
      *" $svc "*) ;;                       # already selected
      *) selected+=("$svc") ;;
    esac
  done
}

is_service() {
  case " ${ALL_SERVICES[*]} " in
    *" $1 "*) return 0 ;;
    *) return 1 ;;
  esac
}

while IFS= read -r path; do
  [ -n "$path" ] || continue
  case "$path" in
    # Reactor-wide inputs: the parent pom, the wrapper, or this workflow's own
    # definition can change how every module builds. Verify all of them.
    backend/pom.xml|backend/mvnw|backend/mvnw.cmd|backend/.mvn/*|\
    .github/workflows/ci.yml|.github/scripts/affected-services.sh)
      add "${ALL_SERVICES[*]}"
      break
      ;;
    backend/*)
      module="${path#backend/}"
      module="${module%%/*}"
      if is_service "$module"; then
        add "$module"
      else
        deps="$(dependents_of "$module")"
        if [ -n "$deps" ]; then
          add "$deps"
        else
          # An unrecognised top-level directory under backend/ — a new module, or a
          # file directly in backend/. Don't guess; verify everything.
          add "${ALL_SERVICES[*]}"
          break
        fi
      fi
      ;;
    *)
      # Outside backend/ (frontend, e2e, docs, compose) — no backend impact.
      ;;
  esac
done

if [ ${#selected[@]} -eq 0 ]; then
  exit 0
fi

printf '%s\n' "${selected[@]}" | sort
