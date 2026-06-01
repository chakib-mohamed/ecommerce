// Seeds the featured-products-service MongoDB database (database: products, collection: product).
// Mirrors the 12 products from Liquibase changeset 004-rich-dev-data.sql.
//
// UUIDs use UUID("...") which creates BSON binary subtype 4, matching
// quarkus.mongodb.uuid-representation = standard in featured-products-service.
//
// NOTE: LocalDate fields (activeFrom, activeTo) are stored as BSON dates via ISODate(...).
// Quarkus Panache MongoDB maps LocalDate to BSON DATE_TIME, so plain ISO-8601 strings
// fail to decode ("expected 'DATE_TIME' BsonType but got 'STRING'").
//
// This script runs once when the mongodb_data Docker volume is first created.
// To re-seed: docker-compose down -v && docker-compose up

db = db.getSiblingDB('products');

db.product.drop();

db.product.insertMany([
    {
        productID: UUID("a0000000-0000-0000-0000-000000000001"),
        title: "Marble Dining Table",
        description: "Elegant marble top dining table for 6",
        price: 899.99,
        image: null,
        promotions: [
            { label: "Summer Sale", percentageOff: 15.0, activeFrom: ISODate("2025-06-01"), activeTo: ISODate("2025-08-31") }
        ],
        categories: [
            { id: NumberLong(20), label: "Dining Tables" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000002"),
        title: "Oak Dining Table",
        description: "Solid oak dining table with natural finish",
        price: 649.00,
        image: null,
        promotions: [
            { label: "Summer Sale", percentageOff: 15.0, activeFrom: ISODate("2025-06-01"), activeTo: ISODate("2025-08-31") }
        ],
        categories: [
            { id: NumberLong(20), label: "Dining Tables" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000003"),
        title: "Velvet Dining Chair",
        description: "Set of 2 velvet upholstered dining chairs",
        price: 129.99,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(21), label: "Dining Chairs" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000004"),
        title: "Walnut Sideboard",
        description: "Walnut wood sideboard with 3 drawers",
        price: 749.00,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(22), label: "Sideboards" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000005"),
        title: "L-Shape Corner Sofa",
        description: "L-shape corner sofa in grey fabric",
        price: 1199.00,
        image: null,
        promotions: [
            { label: "New Arrivals", percentageOff: 10.0, activeFrom: ISODate("2025-05-01"), activeTo: ISODate("2025-12-31") }
        ],
        categories: [
            { id: NumberLong(23), label: "Sofas" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000006"),
        title: "Glass Coffee Table",
        description: "Tempered glass top coffee table",
        price: 249.99,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(24), label: "Coffee Tables" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000007"),
        title: "Industrial TV Stand",
        description: "Industrial style TV stand with open shelves",
        price: 319.00,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(25), label: "TV Stands" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000008"),
        title: "King Size Platform Bed",
        description: "King size platform bed with headboard",
        price: 999.00,
        image: null,
        promotions: [
            { label: "New Arrivals", percentageOff: 10.0, activeFrom: ISODate("2025-05-01"), activeTo: ISODate("2025-12-31") }
        ],
        categories: [
            { id: NumberLong(26), label: "Beds" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000009"),
        title: "Solid Oak Nightstand",
        description: "Solid oak nightstand with 2 drawers",
        price: 189.00,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(27), label: "Nightstands" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000010"),
        title: "Teak Garden Table",
        description: "Teak outdoor garden dining table",
        price: 449.00,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(28), label: "Garden Tables" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000011"),
        title: "Folding Garden Chair",
        description: "Lightweight folding garden chair",
        price: 89.99,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(29), label: "Garden Chairs" }
        ]
    },
    {
        productID: UUID("a0000000-0000-0000-0000-000000000012"),
        title: "Rattan Coffee Table",
        description: "Natural rattan wicker coffee table",
        price: 299.00,
        image: null,
        promotions: [],
        categories: [
            { id: NumberLong(24), label: "Coffee Tables" }
        ]
    }
]);

print("featured-products: seeded " + db.product.countDocuments() + " products");
