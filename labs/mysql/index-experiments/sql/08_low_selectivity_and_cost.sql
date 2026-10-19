USE index_lab;

SELECT '08 low selectivity and optimizer cost' AS experiment;

CREATE INDEX idx_status
    ON orders(status);

ANALYZE TABLE orders;

SELECT 'A: SELECT * with low-selectivity status' AS query_case;
EXPLAIN ANALYZE
SELECT *
FROM orders
WHERE status = 'PAID';

SELECT 'B: narrow projection on the same predicate' AS query_case;
EXPLAIN ANALYZE
SELECT id
FROM orders
WHERE status = 'PAID';

SELECT
    table_name,
    table_rows,
    ROUND(data_length / 1024 / 1024, 2) AS data_mib,
    ROUND(index_length / 1024 / 1024, 2) AS index_mib
FROM information_schema.tables
WHERE table_schema = 'index_lab'
  AND table_name = 'orders';

SHOW INDEX FROM orders;
