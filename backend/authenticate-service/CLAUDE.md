# authenticate-service/CLAUDE.md

Quarkus 3.17.6 service. Handles user registration, login, and JWT issuance.

## Storage

Uses **Quarkus MongoDB Panache** (not JPA/Hibernate). The `User` entity extends `PanacheMongoEntity`. No SQL, no Liquibase. Database: `authenticate` on `mongodb://mongodb:27017`.

## Passwords

Passwords are hashed with **jBCrypt** (`org.mindrot.jbcrypt`). Never store or log plaintext passwords.

## JWT

Tokens are generated with `io.jsonwebtoken` (JJWT 0.11.5). Expiration is controlled by `security.jwt.expiration` in `application.properties` (value in minutes; default: `60`). The secret key must be set as an env var or in properties before running — check `application.properties` for the property name.

## Dev Profile

MongoDB connection in dev (`%dev`): `localhost:27017`.
