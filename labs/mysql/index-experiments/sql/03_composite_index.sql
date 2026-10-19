USE index_lab;

SELECT '03 composite index: tenant_id, status, created_at' AS experiment;

CREATE INDEX idx_tenant_status_created
    ON orders(tenant_id, status, created_at DESC);

ANALYZE TABLE orders;

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
