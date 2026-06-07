# products-service/CLAUDE.md

Quarkus 3.17.6 service. Handles products, categories, promotions, and image uploads.

## Prerequisites

The `product-images` S3 bucket is created automatically by `StorageService.@PostConstruct` on first startup. No manual bucket creation needed.

## Database Migrations

Liquibase runs automatically at startup (`quarkus.liquibase.migrate-at-start=true`). Never apply schema changes manually — always add a changeset to `src/main/resources/db/changelog/db.changelog-master.xml`.

## JSON Naming

The ORM uses `CamelCaseToUnderscoresNamingStrategy` for column names (e.g., `percentageOff` → `percentage_off`). JSON serialization uses Jakarta JSON-B with `LOWER_CASE_WITH_UNDERSCORES` — field names are snake_case in the API (e.g., `image_key`, `percentage_off`).

## Kafka

This service is a **producer** on two topics:
- `product-updated` — emitted on any product update
- `product-deleted` — emitted on product deletion

Serializer: `io.quarkus.kafka.client.serialization.JsonbSerializer`. Dev bootstrap: `localhost:9092`.
The serializer uses the CDI-managed `Jsonb`, so event payloads are **snake_case on the wire**
(`productId` → `product_id`), same as the HTTP API.

## Image Storage

Uses AWS SDK v2 (`software.amazon.awssdk:s3:2.42.27`) via `StorageService`, pointed at **LocalStack** (`http://localstack:4566` in Docker, `http://localhost:4566` in dev mode). Config prefix `products.storage.*`. Bucket name is `product-images`. Images are served via the gateway at `/api/products/images/{filename}`.

Tests use `StorageTestResource` which starts a `LocalStackContainer` (Testcontainers) with S3 enabled and injects the endpoint/credentials at runtime. No MinIO dependency remains.
