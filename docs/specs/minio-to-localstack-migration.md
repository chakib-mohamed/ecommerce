# Spec: Replace MinIO with LocalStack + Quarkus S3 Extension

## Status
Draft

## Problem

`products-service` stores product images using the AWS SDK v1 (`com.amazonaws:aws-java-sdk-s3:1.12.445`)
pointing at a MinIO container. Both are no longer viable:

- **AWS SDK v1** reached EOL in July 2024 — no security patches, no bug fixes.
- **MinIO** licensing restrictions make it unsuitable for self-hosted projects.

## Goal

Replace the MinIO container and the AWS SDK v1 dependency with a fully-supported, self-hosted
S3-compatible stack. The HTTP API of `products-service` must not change — this is a pure
internal migration.

## Chosen Stack

| Layer | Before | After |
|-------|--------|-------|
| Object store container | `minio/minio:latest` | `localstack/localstack:4` |
| Java SDK | `aws-java-sdk-s3:1.12.445` (SDK v1, EOL) | `quarkus-amazon-s3` (SDK v2, Quarkus-managed) |
| Test resource | `MinioTestResource` (generic Testcontainers) | `StorageTestResource` (Testcontainers LocalStack module) |

**Why LocalStack over Garage:**
Garage requires a post-startup cluster layout initialization step (`garage layout assign`), which
makes a one-shot Docker Compose startup unreliable. LocalStack starts fully ready, has first-class
Testcontainers support via `org.testcontainers:localstack`, and is Apache 2.0.

## Scope

Only `products-service` is affected. No other service touches object storage.

## No API Changes

- `GET /products/images/{key}` — unchanged
- `POST /products` (with image) — unchanged
- `PUT /products/{id}` (with image) — unchanged
- `DELETE /products/{id}` — unchanged

No new OpenAPI spec needed.

## Files to Change

### Production code

| File | Change |
|------|--------|
| `products-service/pom.xml` | Remove `aws-java-sdk-s3:1.12.445`; add `quarkus-amazon-s3`, `url-connection-client` |
| `control/MinioService.java` | Rename to `StorageService.java`; rewrite using CDI-injected `S3Client` |
| `control/ProductService.java` | Update injection point: `MinioService` → `StorageService` |
| `boundary/ProductsResource.java` | Update injection point: `MinioService` → `StorageService` |
| `resources/application.properties` | Replace `products.minio.*` with `quarkus.s3.*` |
| `docker-compose.yml` | Replace `minio` service with `localstack` service |

### Test code

| File | Change |
|------|--------|
| `products-service/pom.xml` | Add `testcontainers:localstack` (test scope) |
| `test/.../MinioTestResource.java` | Rename to `StorageTestResource.java`; use `LocalStackContainer` |
| `test/.../ProductsResourceTest.java` | Update `@QuarkusTestResource` reference |
| `test/.../PriceChangedConsumerTest.java` | Update `@QuarkusTestResource` reference |

### Docs

| File | Change |
|------|--------|
| `products-service/CLAUDE.md` | Update storage section: MinIO → LocalStack, `quarkus.s3.*` config |

## Configuration Mapping

### application.properties (new)

```properties
# Object storage (LocalStack in Docker, real S3 in production)
quarkus.s3.endpoint-override=http://localstack:4566
quarkus.s3.aws.region=us-east-1
quarkus.s3.aws.credentials.type=static
quarkus.s3.aws.credentials.static-provider.access-key-id=test
quarkus.s3.aws.credentials.static-provider.secret-access-key=test
quarkus.s3.path-style-access=true

products.images.bucket=product-images
```

Dev profile overrides `quarkus.s3.endpoint-override` to `http://localhost:4566`.

### docker-compose.yml (new localstack service)

```yaml
localstack:
  image: localstack/localstack:4
  ports:
    - 4566:4566
  environment:
    - SERVICES=s3
    - DEFAULT_REGION=us-east-1
  networks:
    - backend
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:4566/_localstack/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

`products-service` `depends_on.localstack.condition: service_healthy` replaces the MinIO dep.

## StorageService API (unchanged from MinioService)

```java
String uploadImage(byte[] data)      // returns object key (UUID)
byte[] downloadImage(String key)
void   deleteImage(String key)
```

Bucket creation on startup moves from `@PostConstruct` (manual SDK call) to a
`@PostConstruct` using `S3Client.createBucket(...)` from SDK v2 — or a startup event if
the bucket is pre-created by an init script.

## Test Strategy

`StorageTestResource` starts a `LocalStackContainer` with S3 enabled. It injects:

```
quarkus.s3.endpoint-override  → http://<host>:<port>
quarkus.s3.aws.region         → us-east-1
quarkus.s3.aws.credentials.*  → test / test
quarkus.s3.path-style-access  → true
quarkus.devservices.enabled   → false
```

Both `ProductsResourceTest` and `PriceChangedConsumerTest` keep their existing
`@QuarkusTestResource(StorageTestResource.class)` annotation.

## Acceptance Criteria

- [ ] `products-service` builds with zero references to `com.amazonaws.*`
- [ ] All existing tests pass against LocalStack
- [ ] `docker-compose up` starts cleanly with `localstack` replacing `minio`
- [ ] Image upload, download, and delete work end-to-end via LocalStack
- [ ] No MinIO references remain in code, config, or Docker Compose
