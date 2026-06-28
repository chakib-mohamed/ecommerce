// Seeds the authenticate-service MongoDB database (database: authenticate, collection: user)
// with two test accounts: an admin and a retail (regular shopper) user.
//
// Passwords are stored as jBCrypt-compatible bcrypt hashes ($2a$10$...), the same format
// UserService produces via BCrypt.hashpw — so these accounts log in through the normal
// /api/users/authenticate flow. Plaintext credentials (test-only):
//
//   admin@ecommerce.test  / Admin123!   roles: ["admin"]
//   retail@ecommerce.test / Retail123!  roles: ["customer"]
//
// Upserts by email, so it is safe to run repeatedly and on an already-populated volume
// without clobbering or duplicating accounts.

db = db.getSiblingDB('authenticate');

const users = [
    {
        email: "admin@ecommerce.test",
        password: "$2a$10$t1HDTfJ2Z06Mi5G7F9mAxOADE2YnujFtSXP2LWlaZ5ud.mJk1vKoi",
        roles: ["admin"]
    },
    {
        email: "retail@ecommerce.test",
        password: "$2a$10$hOCBYkXkRjk78Vuri2q0gO3ISkrKBCrKJLQlO6AsAgpkOmptWT7Wi",
        roles: ["customer"]
    }
];

users.forEach(function (u) {
    db.user.updateOne(
        { email: u.email },
        { $set: { password: u.password, roles: u.roles } },
        { upsert: true }
    );
});

print("authenticate: seeded " + db.user.countDocuments() + " users");
