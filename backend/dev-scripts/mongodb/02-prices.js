// Seeds the price-service MongoDB database (database: price-service, collection: prices).
// One price entry per product UUID from Liquibase changeset 004-rich-dev-data.sql.
//
// Price.productId is a String field (not UUID type), so plain string values are used.
//
// This script runs once when the mongodb_data Docker volume is first created.
// To re-seed: docker-compose down -v && docker-compose up

db = db.getSiblingDB('price-service');

db.prices.drop();

db.prices.insertMany([
    { productId: "a0000000-0000-0000-0000-000000000001", price: 899.99 },
    { productId: "a0000000-0000-0000-0000-000000000002", price: 649.00 },
    { productId: "a0000000-0000-0000-0000-000000000003", price: 129.99 },
    { productId: "a0000000-0000-0000-0000-000000000004", price: 749.00 },
    { productId: "a0000000-0000-0000-0000-000000000005", price: 1199.00 },
    { productId: "a0000000-0000-0000-0000-000000000006", price: 249.99 },
    { productId: "a0000000-0000-0000-0000-000000000007", price: 319.00 },
    { productId: "a0000000-0000-0000-0000-000000000008", price: 999.00 },
    { productId: "a0000000-0000-0000-0000-000000000009", price: 189.00 },
    { productId: "a0000000-0000-0000-0000-000000000010", price: 449.00 },
    { productId: "a0000000-0000-0000-0000-000000000011", price: 89.99 },
    { productId: "a0000000-0000-0000-0000-000000000012", price: 299.00 }
]);

print("price-service: seeded " + db.prices.countDocuments() + " prices");
