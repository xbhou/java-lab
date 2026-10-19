USE index_lab;

SELECT '04 leftmost-prefix behavior' AS experiment;

SELECT 'A: tenant_id only' AS query_case;
EXPLAIN
SELECT id
FROM orders
WHERE tenant_id = 42;

SELECT 'B: tenant_id + status' AS query_case;
EXPLAIN
SELECT id
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID';

SELECT 'C: status without tenant_id' AS query_case;
EXPLAIN
SELECT id
FROM orders
WHERE status = 'PAID'
  AND created_at >= '2026-01-01';

SELECT 'D: tenant_id + created_at, skipping status' AS query_case;
EXPLAIN
SELECT id
FROM orders
WHERE tenant_id = 42
  AND created_at >= '2026-01-01';
