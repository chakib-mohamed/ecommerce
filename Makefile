# Canonical build/run entrypoint for the ecommerce platform.
# All Docker work goes through `docker compose` (V2); tiers are selected via the
# `infra` / `backend` / `frontend` Compose profiles defined in docker-compose.yml.

.DEFAULT_GOAL := help

.PHONY: help \
        infra observability backend front up down logs \
        build build-api build-front \
        dev-front dev-gateway \
        dev-authenticate dev-products dev-featured dev-orders dev-price

## help: list available targets
help:
	@echo "Run targets:"
	@echo "  make infra            infra containers only (db/kafka/etc.)"
	@echo "  make observability    tracing/metrics stack (jaeger/prometheus/grafana)"
	@echo "  make backend          infra + backend services"
	@echo "  make front            full stack (infra + backend + frontend)"
	@echo "  make up               full stack (infra + backend + frontend)"
	@echo "  make down             stop & remove everything"
	@echo "  make logs             tail logs for all services"
	@echo ""
	@echo "Build targets:"
	@echo "  make build            mvn package (skip tests) + all Docker images"
	@echo "  make build-api        package backend jars, then build service images"
	@echo "  make build-front      build the frontend image"
	@echo ""
	@echo "Dev (hot-reload, foreground) targets:"
	@echo "  make dev-front        Vite dev server on :3000"
	@echo "  make dev-gateway      API gateway (Spring dev profile) on :8080"
	@echo "  make dev-authenticate quarkus:dev on :8081"
	@echo "  make dev-products     quarkus:dev on :8082"
	@echo "  make dev-featured     quarkus:dev on :8083"
	@echo "  make dev-orders       quarkus:dev on :8084"
	@echo "  make dev-price        quarkus:dev on :8085"

# ----------------------------------------------------------------------------
# Run (detached) — profile-driven tiers
# ----------------------------------------------------------------------------

## infra: bring up infra containers only
infra:
	docker compose --profile infra up -d

## observability: bring up the tracing/metrics stack only (jaeger/prometheus/grafana)
observability:
	docker compose --profile observability up -d

## backend: bring up infra + backend services
backend:
	docker compose --profile infra --profile backend up -d

## front: bring up the full stack (incl. observability)
front:
	docker compose --profile infra --profile backend --profile frontend --profile observability up -d

## up: bring up the full stack (incl. observability)
up:
	docker compose --profile infra --profile backend --profile frontend --profile observability up -d

## down: stop & remove every container across all profiles
down:
	docker compose --profile "*" down

## logs: tail logs across all profiles
logs:
	docker compose --profile "*" logs -f

# ----------------------------------------------------------------------------
# Build — package jars first, then build images (ordering matters: the JVM
# Dockerfiles copy a prebuilt jar)
# ----------------------------------------------------------------------------

## build: build backend images then the frontend image
build: build-api build-front

## build-api: package all backend jars, then build the service images
# `--profile "*"` activates every profile so each service's `depends_on`
# targets (e.g. infra's kafka) resolve while building the backend tier.
build-api:
	mvn package -DskipTests -f ./backend/pom.xml
	docker compose --profile "*" build \
		products-service authenticate-service orders-service \
		featured-products-service price-service api-gateway

## build-front: build the frontend image (single in-image build)
build-front:
	docker compose --profile "*" build ecommerce-front

# ----------------------------------------------------------------------------
# Dev — foreground, hot reload, one terminal each. Quarkus services override
# their in-container port (8080) to match the gateway dev routes (8081-8085).
# Pair with `make infra` for dependencies and `make dev-gateway` for routing.
# ----------------------------------------------------------------------------

## dev-front: Vite dev server (:3000)
dev-front:
	npm --prefix ./frontend run dev

## dev-gateway: API gateway with the Spring `dev` profile (:8080 → localhost services)
dev-gateway:
	mvn -f backend/ecommerce-api-gateway/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev

## dev-authenticate: authenticate-service hot reload (:8081)
dev-authenticate:
	cd backend && ./mvnw quarkus:dev -pl authenticate-service -Dquarkus.http.port=8081

## dev-products: products-service hot reload (:8082)
dev-products:
	cd backend && ./mvnw quarkus:dev -pl products-service -Dquarkus.http.port=8082

## dev-featured: featured-products-service hot reload (:8083)
dev-featured:
	cd backend && ./mvnw quarkus:dev -pl featured-products-service -Dquarkus.http.port=8083

## dev-orders: orders-service hot reload (:8084)
dev-orders:
	cd backend && ./mvnw quarkus:dev -pl orders-service -Dquarkus.http.port=8084

## dev-price: price-service hot reload (:8085)
dev-price:
	cd backend && ./mvnw quarkus:dev -pl price-service -Dquarkus.http.port=8085
