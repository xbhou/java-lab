USE index_lab;

SELECT '07 sargable vs function-wrapped predicate' AS experiment;

CREATE INDEX idx_created_at
    ON orders(created_at);

ANALYZE TABLE orders;

SELECT 'A: sargable range predicate' AS query_case;
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM orders
WHERE created_at >= '2026-01-15 00:00:00'
  AND created_at <  '2026-01-16 00:00:00';

SELECT 'B: function wrapped indexed column' AS query_case;
EXPLAIN ANALYZE
SELECT COUNT(*)
FROM orders
WHERE DATE(created_at) = '2026-01-15';
