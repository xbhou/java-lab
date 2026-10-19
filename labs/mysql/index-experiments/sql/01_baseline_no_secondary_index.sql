USE index_lab;

SELECT '01 baseline: no secondary index' AS experiment;

EXPLAIN ANALYZE
SELECT
    id,
    user_id,
    amount,
    created_at
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID'
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 20;
