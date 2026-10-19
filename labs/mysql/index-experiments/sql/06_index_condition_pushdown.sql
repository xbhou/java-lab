USE index_lab;

SELECT '06 index condition pushdown' AS experiment;

SET SESSION optimizer_switch = 'index_condition_pushdown=on';

SELECT 'A: ICP enabled' AS query_case;
EXPLAIN
SELECT *
FROM orders FORCE INDEX (idx_tenant_status_created)
WHERE tenant_id = 42
  AND status LIKE '%A%'
  AND note LIKE '%vip%';

EXPLAIN ANALYZE
SELECT *
FROM orders FORCE INDEX (idx_tenant_status_created)
WHERE tenant_id = 42
  AND status LIKE '%A%'
  AND note LIKE '%vip%';

SET SESSION optimizer_switch = 'index_condition_pushdown=off';

SELECT 'B: ICP disabled' AS query_case;
EXPLAIN
SELECT *
FROM orders FORCE INDEX (idx_tenant_status_created)
WHERE tenant_id = 42
  AND status LIKE '%A%'
  AND note LIKE '%vip%';

EXPLAIN ANALYZE
SELECT *
FROM orders FORCE INDEX (idx_tenant_status_created)
WHERE tenant_id = 42
  AND status LIKE '%A%'
  AND note LIKE '%vip%';

SET SESSION optimizer_switch = 'index_condition_pushdown=on';
