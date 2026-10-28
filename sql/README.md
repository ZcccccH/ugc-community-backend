# Database SQL

| File | Purpose |
| :--- | :--- |
| `schema.sql` | Table structure only (30 core tables, no data) |
| `seed-demo.sql` | Fake demo data: 3 users, tags, works, comments, a message and a placeholder app identity |
| `../src/main/resources/db/migration/*.sql` | Incremental migrations (sandbox module, indexes, new columns) |

## Setup

```bash
# 1. Create the schema (uses your own MySQL account; never commit credentials)
mysql -u "$DB_USERNAME" -p < sql/schema.sql

# 2. Apply migrations in filename order
for f in src/main/resources/db/migration/*.sql; do
  mysql -u "$DB_USERNAME" -p ugc_community < "$f"
done

# 3. (Optional) load demo data
mysql -u "$DB_USERNAME" -p ugc_community < sql/seed-demo.sql
```

On startup, `AdminSchemaInitializer` and `SandboxSchemaInitializer` also add
missing tables/columns idempotently, so a partially migrated database is repaired automatically.

## Demo accounts

`seed-demo.sql` creates `alice`, `bob` and `carol`, all with the password
`demo-password-change-me`. Phones (`+1-555-01xx`) and emails (`@example.com`) are fictional.
The `app_identity` row uses `REPLACE_WITH_*` placeholders; generate your own AppSecret
and RSA key pair before enabling request signing.
