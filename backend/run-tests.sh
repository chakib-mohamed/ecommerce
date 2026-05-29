#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

SERVICES=(
  authenticate-service
  featured-products-service
  price-service
  orders-service
  products-service
  ecommerce-api-gateway
)

declare -A RESULTS
declare -A STATUSES

for svc in "${SERVICES[@]}"; do
  echo ""
  echo "=========================================="
  echo "  Testing: $svc"
  echo "=========================================="
  start=$(date +%s)
  if ./mvnw test -pl "$svc" -B --no-transfer-progress 2>&1; then
    STATUSES[$svc]="PASS"
  else
    STATUSES[$svc]="FAIL"
  fi
  end=$(date +%s)
  RESULTS[$svc]=$(( end - start ))
  echo "  Done in ${RESULTS[$svc]}s — ${STATUSES[$svc]}"
done

echo ""
echo "============================================"
echo "  RESULTS SUMMARY"
echo "============================================"
printf "%-35s %-8s %s\n" "SERVICE" "STATUS" "TIME"
printf "%-35s %-8s %s\n" "-------" "------" "----"
total=0
for svc in "${SERVICES[@]}"; do
  t=${RESULTS[$svc]}
  total=$(( total + t ))
  printf "%-35s %-8s %ds\n" "$svc" "${STATUSES[$svc]}" "$t"
done
printf "%-35s %-8s %s\n" "-----------------------------" "--------" "--------"
printf "%-35s %-8s %ds\n" "TOTAL" "" "$total"
