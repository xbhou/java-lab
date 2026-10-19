# MySQL

## 中文

可重复运行的 MySQL 实验，重点通过真实数据和执行计划理解索引、事务、锁与并发行为。

### Labs

#### 索引实验 / Index Experiments

路径：`index-experiments/`

重点：

- EXPLAIN / EXPLAIN ANALYZE
- 无二级索引 vs 单列索引
- 联合索引
- 最左前缀
- 覆盖索引
- Index Condition Pushdown
- Sargable Predicate
- 低选择性与 Cost-based Optimizer
- 索引空间 / 写入成本

实验使用 MySQL 8.4 LTS 和 20 万行确定性订单数据。

后续覆盖：

- Transaction
- Lock
- Isolation Level
- 并发更新与一致性问题

---

## English

Reproducible MySQL experiments focused on real data, execution plans, indexing, transactions, locks, and concurrency behavior.

### Labs

#### Index Experiments / 索引实验

Path: `index-experiments/`

Focus:

- EXPLAIN / EXPLAIN ANALYZE
- no secondary index vs single-column index
- composite indexes
- leftmost-prefix behavior
- covering indexes
- Index Condition Pushdown
- sargable predicates
- low selectivity and the cost-based optimizer
- index storage and write costs

The lab uses MySQL 8.4 LTS and a deterministic 200,000-row orders dataset.

Planned next topics:

- transactions
- locking
- isolation levels
- concurrent updates and consistency behavior
