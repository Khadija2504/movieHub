-- Baseline migration. Tables are added in later migrations (V2__users.sql, ...), one per feature.
-- Never edit a migration once it has been applied; create a new one instead.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
