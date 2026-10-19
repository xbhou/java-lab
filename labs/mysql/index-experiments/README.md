# MySQL 索引实验 / MySQL Index Experiments

## 目标 / Goal

中文：

这个 Lab 不以“背 B+Tree 定义”为目标，而是用同一张 20 万行订单表，连续观察：

- 没有二级索引时发生什么
- 单列索引能解决什么、不能解决什么
- 联合索引为什么与查询访问模式有关
- 最左前缀到底是什么意思
- 覆盖索引为什么能减少回表
- Index Condition Pushdown 如何减少无效回表
- 为什么函数包裹索引列会让普通范围访问变差
- 为什么“有索引”不代表优化器一定选择索引
- 索引为什么同时带来空间和写入成本

English:

This lab uses the same 200,000-row orders table to compare no secondary index, single-column indexes, composite indexes, leftmost-prefix behavior, covering indexes, Index Condition Pushdown, sargable predicates, and cost-based optimizer choices.

## 版本 / Version

本实验固定在 MySQL 8.4 LTS 语义上。

Docker：

~~~text
mysql:8.4
~~~

## 实验数据 / Dataset

表：

~~~sql
orders(
    id,
    tenant_id,
    user_id,
    status,
    created_at,
    amount,
    currency,
    note
)
~~~

数据量：

~~~text
200,000 rows
100 tenants
~~~

status 大致分布：

~~~text
PAID       50%
PENDING    25%
CANCELLED  15%
REFUNDED   10%
~~~

设计这个分布是为了同时观察：

~~~text
tenant_id = 42
-> relatively selective

status = 'PAID'
-> low selectivity
~~~

索引是否有价值不仅取决于“有没有”，还取决于：

~~~text
selectivity
rows to scan
rows to fetch
sort cost
covering
statistics
buffer pool state
optimizer cost model
~~~

## 目录 / Structure

~~~text
index-experiments/
├── docker-compose.yml
├── scripts/
│   ├── run-all.sh
│   └── reset.sh
└── sql/
    ├── 00_setup.sql
    ├── 01_baseline_no_secondary_index.sql
    ├── 02_single_column_index.sql
    ├── 03_composite_index.sql
    ├── 04_leftmost_prefix.sql
    ├── 05_covering_index.sql
    ├── 06_index_condition_pushdown.sql
    ├── 07_sargable_predicates.sql
    └── 08_low_selectivity_and_cost.sql
~~~

## 运行 / Run

需要：

~~~text
Docker
Docker Compose
~~~

运行全部实验：

~~~bash
cd labs/mysql/index-experiments
bash scripts/run-all.sh
~~~

只重新生成数据：

~~~bash
bash scripts/reset.sh
~~~

连接数据库：

~~~bash
docker compose exec mysql   mysql -uroot -proot index_lab
~~~

停止容器：

~~~bash
docker compose down
~~~

本 Lab 暴露：

~~~text
localhost:3307
~~~

避免占用本机默认 3306。

## 先学会读 EXPLAIN ANALYZE

不要一看到 EXPLAIN 就只盯着 key。

建议按这个顺序观察：

1. access path 是 Table scan、Index lookup 还是 Index range scan？
2. estimated rows 与 actual rows 差多少？
3. 实际循环次数 loops 是多少？
4. Filter 在哪里发生？
5. Sort 是否出现？
6. LIMIT 前扫描了多少行？
7. 执行时间主要花在哪里？
8. 加索引后扫描行数是否真的下降？

核心思想：

> 索引优化的目标不是让 EXPLAIN 里出现一个 index name，而是减少实际工作量。

## Experiment 1：无二级索引 / No Secondary Index

SQL：

~~~text
01_baseline_no_secondary_index.sql
~~~

查询：

~~~sql
SELECT id, user_id, amount, created_at
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID'
  AND created_at >= '2026-01-01'
ORDER BY created_at DESC
LIMIT 20;
~~~

此时只有 Primary Key。

通常会观察到：

~~~text
Table scan
      |
Filter
      |
Sort
      |
Limit
~~~

虽然最终只需要 20 行，但 MySQL 可能必须检查大量行后才能确定结果。

> LIMIT 20 不等于只需要读取 20 行。

## Experiment 2：单列索引 / Single-column Index

新增：

~~~sql
CREATE INDEX idx_tenant
ON orders(tenant_id);
~~~

现在 MySQL 可以先定位 tenant_id = 42，而不用从 20 万行中全部过滤。

但仍需要继续判断：

~~~text
status
created_at
~~~

并且 ORDER BY created_at 仍可能需要额外排序。

> 单列索引解决了一个过滤维度，不代表整个 SQL 已经最优。

## Experiment 3：联合索引 / Composite Index

新增：

~~~sql
CREATE INDEX idx_tenant_status_created
ON orders(
    tenant_id,
    status,
    created_at DESC
);
~~~

查询访问模式是：

~~~text
tenant_id = constant
status    = constant
created_at >= range
ORDER BY created_at DESC
~~~

因此这个索引布局与查询高度匹配：

~~~text
tenant_id
    ↓
status
    ↓
created_at range/order
~~~

通常可以同时减少：

- scanned rows
- filtering
- sorting work

设计联合索引时，比“哪个字段最常出现”更重要的问题是：

> 查询到底如何访问数据？

## Experiment 4：最左前缀 / Leftmost Prefix

索引：

~~~text
(tenant_id, status, created_at)
~~~

### A

~~~sql
WHERE tenant_id = 42
~~~

可以利用最左列。

### B

~~~sql
WHERE tenant_id = 42
  AND status = 'PAID'
~~~

可以利用连续左前缀。

### C

~~~sql
WHERE status = 'PAID'
  AND created_at >= '2026-01-01'
~~~

缺少第一列 tenant_id，因此不能把联合索引简单当成：

~~~text
(status, created_at)
~~~

来理解。

现代优化器仍可能选择其它策略，例如扫描索引、skip-scan 或 table scan，所以不要把最左前缀背成“缺第一列就绝对看不到这个索引名字”。

真正的结构含义是：

> B-tree 首先按前导 key part 排序；缺失前导列后，后续列通常不能像独立前缀索引那样直接定位一个连续范围。

### D

~~~sql
WHERE tenant_id = 42
  AND created_at >= '2026-01-01'
~~~

tenant_id 可以作为左前缀。

但中间缺少 status，因此 created_at 通常不能继续形成完整的 tenant_id + status + created_at 定位。

不要把：

~~~text
index is used
~~~

和：

~~~text
all index key parts are fully used
~~~

混为一谈。

## Experiment 5：覆盖索引 / Covering Index

新增：

~~~sql
CREATE INDEX idx_cover
ON orders(
    tenant_id,
    status,
    created_at,
    user_id,
    amount,
    currency
);
~~~

查询 A 只读取索引中已有的列：

~~~sql
SELECT
    user_id,
    amount,
    currency,
    created_at
FROM orders
WHERE tenant_id = 42
  AND status = 'PAID'
  AND created_at >= '2026-01-01';
~~~

MySQL 有机会直接从 secondary index 返回结果，不需要再读取完整聚簇记录。

传统 EXPLAIN Extra 通常可能看到：

~~~text
Using index
~~~

第二个查询额外读取 note，而 note 不在覆盖索引中，因此需要读取完整行。

核心：

> Covering Index 不是一种新的索引结构，而是“这个查询需要的列刚好都能从索引中得到”。

## InnoDB 二级索引为什么会回表

简化理解：

~~~text
Secondary B+Tree
key
+
Primary Key
      |
      v
Clustered Primary Key B+Tree
      |
      v
full row
~~~

所以：

~~~text
secondary index hit
!=
full row already available
~~~

覆盖索引的价值之一就是减少二次访问。

## Experiment 6：Index Condition Pushdown

查询：

~~~sql
SELECT *
FROM orders FORCE INDEX (idx_tenant_status_created)
WHERE tenant_id = 42
  AND status LIKE '%A%'
  AND note LIKE '%vip%';
~~~

tenant_id 和 status 存在于 secondary index，但 note 只存在于完整行。

ICP 开启时，可以简化成：

~~~text
read secondary index tuple
      |
evaluate index-only condition
      |
condition false
      |
skip base-row lookup
~~~

只有满足索引条件的记录才进一步读取完整行检查 note。

传统 EXPLAIN 里通常会看到：

~~~text
Using index condition
~~~

脚本分别执行：

~~~text
index_condition_pushdown=on
index_condition_pushdown=off
~~~

并比较 EXPLAIN ANALYZE。

## Experiment 7：Sargable Predicate

先建立：

~~~sql
CREATE INDEX idx_created_at
ON orders(created_at);
~~~

查询 A：

~~~sql
WHERE created_at >= '2026-01-15 00:00:00'
  AND created_at <  '2026-01-16 00:00:00'
~~~

这是典型可以直接用于 B-tree range scan 的表达。

查询 B：

~~~sql
WHERE DATE(created_at) = '2026-01-15'
~~~

这里对索引列进行了函数计算。

普通 created_at B-tree 保存的是原始 datetime 值，而查询要求计算 DATE(created_at)，因此普通范围访问通常无法直接应用。

生产代码经常可以把：

~~~sql
DATE(created_at) = '2026-01-15'
~~~

改写为：

~~~sql
created_at >= '2026-01-15 00:00:00'
AND created_at < '2026-01-16 00:00:00'
~~~

这种能让索引直接用于搜索范围的表达，通常称为 sargable predicate。

## Experiment 8：低选择性与 Cost-based Optimizer

建立：

~~~sql
CREATE INDEX idx_status
ON orders(status);
~~~

PAID 大约占 50%。

查询：

~~~sql
SELECT *
FROM orders
WHERE status = 'PAID';
~~~

即使 status 有索引，优化器也可能认为：

~~~text
secondary index scan
+
many clustered lookups
~~~

比：

~~~text
table scan
~~~

更贵。

因此可能不选择 idx_status。

然后实验再执行：

~~~sql
SELECT id
FROM orders
WHERE status = 'PAID';
~~~

这个 projection 更窄，secondary index 可能更有吸引力。

注意：

> Cost-based optimizer 的选择可能随 MySQL patch version、统计信息、数据分布、buffer pool 状态而变化。

所以真正应该观察：

- estimated cost
- actual rows
- actual time
- 是否需要回表
- 数据选择性

而不是死记“这条 SQL 一定走哪个索引”。

## 为什么有索引却没走并不一定是问题

常见原因：

- 条件选择性太低
- 查询需要返回大量行
- 回表成本太高
- 函数包裹索引列
- 隐式类型转换
- 联合索引前导列不匹配
- 统计信息不准确
- ORDER BY / GROUP BY 改变整体 cost
- 优化器认为 table scan 更便宜

所以排查不要只问：

> 为什么没走索引？

应该问：

> 优化器为什么认为另一条路径成本更低？

## B+Tree 应该理解到什么程度

简化模型：

~~~text
root
 |
 +-- internal pages
       |
       +-- leaf pages ordered by key
~~~

它擅长：

~~~text
=
IN
BETWEEN
>
>=
<
<=
ordered range scan
prefix-compatible ORDER BY
~~~

联合索引：

~~~text
(a, b, c)
~~~

本质上按：

~~~text
a
then b
then c
~~~

排序，而不是三个独立索引简单拼在一起。

## 索引不是免费的 / Indexes Are Not Free

每增加一个 secondary index：

~~~text
INSERT
UPDATE
DELETE
~~~

都可能需要维护额外 B-tree。

还会增加：

- disk usage
- buffer pool pressure
- redo / undo work
- page split / merge work
- optimizer candidate complexity

最后一个 SQL 会打印：

~~~text
data_mib
index_mib
SHOW INDEX
~~~

把索引成本也放进观察范围。

## 一个更接近后端工作的索引设计流程

以后遇到慢 SQL，可以按这个流程：

~~~text
1. 获取真实 SQL
       |
2. 获取参数分布
       |
3. EXPLAIN ANALYZE
       |
4. 找 actual expensive step
       |
5. 看 filter / join / order access pattern
       |
6. 设计候选索引
       |
7. 再跑 EXPLAIN ANALYZE
       |
8. 比较 rows / loops / time
       |
9. 检查写入和存储成本
       |
10. 上线后看真实指标
~~~

不要从：

~~~text
SQL 慢
↓
随便加几个 index
~~~

开始。

## EXPLAIN 里优先关注什么

传统 EXPLAIN：

- type
- possible_keys
- key
- key_len
- rows
- filtered
- Extra

EXPLAIN ANALYZE：

- actual time
- actual rows
- loops
- scan / lookup / range
- sort
- filter

其中 actual rows 和 actual time 通常比“key 字段有没有值”更值得关注。

## 实验注意事项 / Experimental Caveats

数据库 benchmark 非常容易受环境影响：

- 第一次执行可能是 cold cache
- 第二次可能已进入 buffer pool
- 容器 CPU 不同
- SSD / filesystem 不同
- background activity 不同
- statistics 不同

性能对比时建议：

1. 同一环境重复执行多次。
2. 不只比较一次 wall-clock time。
3. 优先比较执行计划和实际扫描工作量。
4. 不要拿教学数据直接推导生产绝对耗时。

## 当前验证边界 / Validation Boundary

当前执行环境没有 Docker、mysql client 或 mysqld，因此无法在这里真实启动 MySQL 8.4 执行这些 SQL。

本 Lab 的 SQL、EXPLAIN ANALYZE、联合索引和 ICP 语义已经按 MySQL 8.4 官方文档核对。

在自己的 Docker 环境执行：

~~~bash
bash scripts/run-all.sh
~~~

即可得到机器上的实际执行计划。

## 生产与面试问题 / Production & Interview Questions

1. Primary Index 和 Secondary Index 在 InnoDB 中有什么区别？
2. 为什么二级索引叶子节点保存 Primary Key？
3. 什么叫回表？
4. 什么叫覆盖索引？
5. 联合索引 (a,b,c) 为什么不能简单当成三个独立索引？
6. 最左前缀到底是数据结构限制还是 SQL 语法规则？
7. range condition 后面的索引列还能如何参与执行？
8. 什么是 Index Condition Pushdown？
9. Using index 和 Using index condition 有什么区别？
10. 为什么函数包裹索引列可能导致普通索引访问失效？
11. 为什么低选择性字段即使有索引也可能不走？
12. EXPLAIN estimated rows 与 actual rows 差很多说明什么？
13. 为什么 LIMIT 很小仍可能扫描大量数据？
14. 索引如何影响 INSERT / UPDATE / DELETE？
15. 慢 SQL 应该先加索引还是先看执行计划？

## 下一步 / Next Step

Phase 4 下一步进入：

> Redis Cache Patterns / Redis 缓存模式

重点可以做：

~~~text
cache-aside
cache miss
cache penetration
cache breakdown
cache avalanche
double delete
TTL jitter
logical expiration
single-flight rebuild
~~~

把“会用 Redis”升级成“知道缓存一致性和故障模式怎么设计”。
