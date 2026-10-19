USE index_lab;

SELECT '02 single-column index: tenant_id' AS experiment;

CREATE INDEX idx_tenant
    ON orders(tenant_id);

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
