# products-service/CLAUDE.md

Quarkus 3.17.6 service. Handles products, categories, promotions, and image uploads.

## Prerequisites

MinIO bucket `product-images` must exist before the service starts. The service will crash on first image upload if the bucket is absent.

## Database Migrations

Liquibase runs automatically at startup (`quarkus.liquibase.migrate-at-start=true`). Never apply schema changes manually — always add a changeset to `src/main/resources/db/changelog/db.changelog-master.xml`.

## JSON Naming

The ORM uses `CamelCaseToUnderscoresNamingStrategy` for column names (e.g., `percentageOff` → `percentage_off`). JSON serialization uses Jakarta JSON-B with no custom strategy — field names are camelCase in the API.

## Kafka

This service is a **producer** on two topics:
- `product-updated` — emitted on any product update
- `product-deleted` — emitted on product deletion

Serializer: `io.quarkus.kafka.client.serialization.JsonbSerializer`. Dev bootstrap: `localhost:9092`.

## Image Storage

Uses Quarkus S3 extension pointing at MinIO (`http://minio:9000` in Docker, configured via `quarkus.s3.*`). Bucket name is `product-images`. Images are served via the gateway at `/api/products/images/{filename}`.
