USE index_lab;

SELECT '05 covering index' AS experiment;

CREATE INDEX idx_cover
    ON orders(
        tenant_id,
        status,
        created_at,
        user_id,
        amount,
        currency
    );

ANALYZE TABLE orders;

SELECT 'A: covered projection' AS query_case;
EXPLAIN
SELECT
    user_id,
    amount,
    currency,
    created_at
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID'
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 20;

SELECT 'B: note is not in the index' AS query_case;
EXPLAIN
SELECT
    user_id,
    amount,
    currency,
    created_at,
    note
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID'
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 20;
