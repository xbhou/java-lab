# Redis 缓存模式实验 / Redis Cache Patterns Lab

## 中文

### 目标

这个 Lab 不把 Redis 只当成 GET / SET 工具，而是围绕真实缓存系统常见故障模式做可重复实验：

- Cache-Aside
- Cache Penetration / 缓存穿透
- Cache Breakdown / Hot-key Stampede / 热点 Key 击穿
- TTL Jitter / 缓存雪崩缓解
- Logical Expiration / 逻辑过期
- Single-flight Rebuild / 单飞重建
- DB / Cache Consistency / 数据库与缓存一致性

核心问题不是“Redis 命中有多快”，而是：

> 缓存 miss、热点并发、数据不存在、批量过期或 Redis 故障时，系统如何退化？

### 版本

~~~text
Redis Open Source 8.10
Jedis 7.5.3
Java 17 compiler baseline
~~~

Redis Server 使用 Redis Open Source 8.10。Jedis 固定为 7.5.3，是为了保持 Java 17 下简单稳定的 pooled client API；这只是实验依赖，不代表生产必须固定该版本。

### 架构

~~~text
Client
  |
  v
CacheAsideService
  |
  +----> Redis
  |
  +----> FakeDatabase
             |
             +-- read counter
             +-- artificial latency
~~~

数据库故意使用 FakeDatabase，而不是再引入 MySQL。它模拟 120ms 读取延迟并统计 read count，因此同样的请求流量可以直接比较不同缓存策略导致的 DB 回源次数。

### 运行

需要 JDK 17+、Maven、Docker 和 Docker Compose。

~~~bash
cd labs/redis/cache-patterns
bash scripts/run.sh
~~~

Redis 暴露在 localhost:6380，避免占用默认 6379。

手动运行：

~~~bash
docker compose up -d
mvn clean compile
mvn exec:java \
  -Dexec.mainClass=dev.xbhou.javalab.redis.cache.CachePatternsApp
~~~

结束：

~~~bash
docker compose down
~~~

---

## 场景 1：Cache-Aside

读取流程：

~~~text
request
  |
GET Redis
  |
  +-- hit -> return
  |
  +-- miss
        |
      DB read
        |
      SET Redis + TTL
        |
      return
~~~

实验连续读取 product:1 两次：

~~~text
first read  -> cache miss -> DB -> cache fill
second read -> cache hit
~~~

预期：

~~~text
application reads = 2
DB reads          = 1
~~~

Cache-Aside 的关键是 Application 自己负责 miss 后回源和回填。

写路径常见做法：

~~~text
update DB
   |
delete cache
~~~

而不是同时更新 DB 与 Cache。后者在并发写下更容易产生顺序覆盖问题。不过 update DB then delete cache 仍然只是常见的 eventual-consistency 策略，不是严格分布式原子提交。

---

## 场景 2：缓存穿透 / Cache Penetration

请求一个不存在的数据：

~~~text
Redis miss
   |
DB miss
   |
next request
   |
Redis miss again
   |
DB miss again
~~~

对 product:missing 连续请求 5 次，不做保护：

~~~text
DB reads = 5
~~~

加入短 TTL 的 negative marker：

~~~text
__NULL__
~~~

流程：

~~~text
first miss
   |
DB returns null
   |
SET __NULL__ with short TTL
   |
next requests
   |
negative-cache hit
   |
no DB access
~~~

预期：

~~~text
without negative cache -> DB reads = 5
with negative cache    -> DB reads = 1
~~~

代价是：如果对象刚创建，而 __NULL__ 尚未过期，会出现短暂假阴性，所以 negative TTL 通常比正常数据 TTL 更短。

对于大量随机不存在 Key 的恶意流量，还可以结合 Bloom Filter、request validation、authentication、rate limiting 和 ID format validation。

---

## 场景 3：热点 Key 击穿 / Cache Breakdown

热门 Key 过期时：

~~~text
many concurrent readers
        |
all see cache miss
        |
all query DB
~~~

本 Lab 同时启动 20 个 reader。朴素回源会让多个线程在第一个回填完成前一起进入 DB，预期 DB reads 大于 1。

### Single-flight Rebuild

第二轮使用：

~~~text
SET lockKey token NX PX 2000
~~~

Winner：

~~~text
acquire rebuild lock
        |
double-check cache
        |
DB read
        |
SET cache
        |
safe unlock
~~~

Losers：

~~~text
cannot acquire lock
        |
poll cache briefly
        |
winner fills cache
        |
read cached value
~~~

预期 20 个并发 reader 只产生 1 次 DB read。拿锁后必须 Double Check，因为另一个请求可能已完成回填。

NX 表示只在 Key 不存在时写入，PX 同时设置毫秒 TTL。比 SETNX 后再单独 EXPIRE 更适合短期 rebuild lock。

释放时需要 owner token，避免 A 锁过期、B 重新获锁后，A 再误删 B 的锁。本 Lab 用 Lua 校验 token 后再 DEL。

Cache rebuild lock 主要保护性能，支付、余额、库存等业务锁保护 correctness，两者风险级别不同。

---

## 场景 4：缓存雪崩与 TTL Jitter

大量 Key 同时写入相同 TTL：

~~~text
many keys
same TTL
   |
expire together
   |
many cache misses
   |
DB spike
~~~

本 Lab 对比 fixed TTL 2000ms 与 base TTL 2000ms + 正负 700ms jitter。为了可重复，实验使用 key hash 生成确定性 jitter；生产通常可使用随机 jitter。

程序通过 PTTL 比较 fixed TTL spread 和 jittered TTL spread，目的是确认过期边界被打散。

TTL jitter 只解决同步过期这一类雪崩。Redis 节点故障、网络分区、mass eviction、大规模主动失效也可能造成 avalanche，通常要结合 local L1 cache、pre-warming、rate limiting、circuit breaker、load shedding 和 degraded response。

---

## 场景 5：逻辑过期 / Logical Expiration

普通 TTL 过期后，请求会 hard miss 并等待重建。逻辑过期把 freshness deadline 存入 value：

~~~text
logicalExpireAt | value
~~~

Redis Key 本身保留更长的物理 TTL。

~~~text
not stale
-> return fresh value

stale
-> return stale value immediately
-> one worker acquires refresh lock
-> refresh from DB asynchronously
~~~

实验先缓存 version-1，让逻辑 freshness 过期，然后把 DB 更新到 version-2。第一次读取仍立即返回 version-1，后台只启动一次 refresh；稍后再读得到 version-2。

这是 stale-while-revalidate：热点失效时 latency 更稳定，代价是主动允许短时间 stale data。因此更适合 catalog、profile、recommendation、ranking 等数据，不一定适合 available balance、最终支付状态、严格库存或强授权判断。

---

## 场景 6：DB / Cache 一致性

初始 DB 和 Cache 都是 version-1。如果只更新 DB 为 version-2，Cache-Aside 再读仍然会命中 stale version-1。

实验随后执行：

~~~text
update DB to version-3
        |
delete cache
        |
next read misses Redis
        |
read version-3 from DB
        |
cache refill
~~~

但 update DB then delete cache 仍存在并发 race，更严格的一致性可以考虑 delayed double delete、version check、CDC / binlog invalidation、outbox event、cache generation/version、short TTL，或对强一致读直接 bypass cache。

---

## 穿透、击穿、雪崩怎么区分

Penetration：请求的数据本来不存在，导致每次 Redis miss 都穿到 DB。

Breakdown / Stampede：一个热点 Key 存在，但在高并发下过期，大量 reader 同时回源。

Avalanche：大量 Key 或整个 Cache Layer 同时失效，导致大范围流量落到 DB。

---

## 生产设计 Checklist

一个 Cache-Aside 至少需要回答：Key format、Value schema version、TTL、TTL jitter、negative cache、hot-key single-flight、rebuild lock TTL、stale tolerance、logical expiration、写后 delete 还是 update cache、Redis 故障时 fail-open / fail-closed、DB fallback capacity、L1 cache、hit/miss/eviction metrics、hot-key detection 以及 cache warm-up。

## Redis 故障真正危险在哪里

如果平时 100k QPS 走 Redis、1k QPS 走 DB，Redis 故障后简单 bypass 可能把 100k QPS 全部导向 DB。因此缓存 resilience 需要和前面学过的 Rate Limiting、Circuit Breaker、Bulkhead、Deadline、Load Shedding 组合设计。

## 建议监控指标

至少观察 cache hit rate、miss rate、negative-cache hit rate、rebuild count / latency、lock contention、Redis command latency / errors、expired / evicted keys、memory usage、hot-key QPS、DB fallback QPS 和 stale response count。

## 当前验证边界

当前执行环境没有 Docker、Redis Server 或 Maven，因此无法在这里真实启动 Redis 8.10 执行完整 Lab。

已按当前 Redis 文档核对 Cache-Aside 基本模型、SET NX PX 原子获取短期 rebuild lock、owner-token Lua 安全释放、TTL / PTTL 语义与 stampede mitigation 思路。

在支持 Docker + Maven 的机器上运行 bash scripts/run.sh 即可执行全部场景。

## 生产与面试问题

1. Cache-Aside 的读写流程是什么？
2. 为什么写操作常见 update DB then delete cache？
3. 什么是 cache penetration？
4. Negative Cache 的副作用是什么？
5. Bloom Filter 和 Negative Cache 如何选择？
6. 什么是 hot-key stampede？
7. 为什么需要 single-flight？
8. SET NX PX 比 SETNX + EXPIRE 好在哪里？
9. rebuild lock 为什么需要 owner token？
10. TTL jitter 为什么能降低 avalanche 风险？
11. logical expiration 的收益和一致性代价是什么？
12. Redis 故障为什么可能拖垮 DB？
13. update DB then delete cache 是否严格一致？
14. delayed double delete 解决什么 race？
15. 哪些数据不适合 stale cache？

## 下一步

Phase 4 下一步进入 Kafka Consumer Lag & Retry，重点观察 producer rate、consumer rate、lag、slow processing、retry、backoff、retry topic、dead-letter topic、poison message 和 ordering trade-off。

---

## English

### Goal

This lab treats Redis as a cache resilience component rather than only a GET / SET store. It demonstrates cache-aside reads, negative caching, hot-key stampede, Redis-backed single-flight rebuild, TTL jitter, logical expiration, and database/cache consistency trade-offs.

The FakeDatabase adds deterministic latency and a read counter so each strategy can be compared by how many database fallbacks it causes.

### Cache-Aside

The application checks Redis first. On a miss it loads from the database, writes the value with a TTL, and returns it. Two application reads should produce one database read when the second request hits the cache.

### Penetration

Non-existent data repeatedly misses both Redis and the database. A short-lived negative marker changes repeated misses from five database reads to one, at the cost of briefly hiding newly created data until the negative entry expires.

### Hot-key Stampede

Twenty concurrent readers hit one expired hot key. The naive implementation can create duplicate database reads. The single-flight implementation uses SET NX PX so only one caller rebuilds while others wait briefly for the cache fill. The owner-token Lua release prevents a previous lock owner from deleting a newer lock.

### TTL Jitter

Identical TTLs can synchronize expiration and backend load. The lab compares fixed TTLs with spread TTLs and observes PTTL distribution. TTL jitter mitigates synchronized expiration but does not solve Redis outages, mass eviction, or network failures.

### Logical Expiration

Logical expiration implements stale-while-revalidate: fresh values are returned normally; stale values are returned immediately while one worker refreshes asynchronously. It improves latency stability for hot data at the cost of bounded staleness.

### Consistency

Updating the database without invalidating Redis demonstrates a stale-cache read. The lab then applies update database followed by delete cache so the next read reloads the latest value. This remains eventual consistency; stronger requirements may need version checks, CDC invalidation, outbox events, short TTLs, or cache bypass.

### Engineering Takeaway

The architecture question is not only: How fast is Redis? It is: What happens to the database and the rest of the system when Redis misses, expires, or fails?