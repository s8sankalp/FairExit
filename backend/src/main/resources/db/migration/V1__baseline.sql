-- Baseline migration. Real tables arrive module by module (users in V2, and so on).
-- Keeping a V1 proves Flyway runs on startup and in tests.
SELECT 1;
