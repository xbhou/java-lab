# Redis

## 中文

Redis 实验目录，重点通过可运行场景理解缓存、TTL、一致性和失效模式。

### Labs

#### 缓存模式 / Cache Patterns

路径：`cache-patterns/`

重点：

- Cache-Aside
- Negative Cache / Cache Penetration
- Hot-key Breakdown / Stampede
- Redis-backed Single-flight Rebuild
- SET NX PX + owner-token release
- TTL Jitter / Avalanche mitigation
- Logical Expiration / stale-while-revalidate
- DB / Cache Consistency
- Redis failure amplification

实验使用 Redis Open Source 8.10、Jedis 7.5.3 和 Java 17 baseline。

后续可继续扩展：

- Redis memory / eviction policies
- Hot Key detection
- Local L1 + Redis L2 cache
- CDC-driven cache invalidation
- Cluster / Sentinel failure behavior

---

## English

Redis experiments focused on observable caching, TTL, consistency, and failure behavior.

### Labs

#### Cache Patterns / 缓存模式

Path: `cache-patterns/`

Focus:

- cache-aside
- negative caching and penetration
- hot-key breakdown / stampede
- Redis-backed single-flight rebuild
- SET NX PX with owner-token release
- TTL jitter and avalanche mitigation
- logical expiration / stale-while-revalidate
- database/cache consistency
- Redis failure amplification

The lab uses Redis Open Source 8.10, Jedis 7.5.3, and a Java 17 baseline.

Possible future extensions:

- Redis memory / eviction policies
- hot-key detection
- local L1 + Redis L2 caching
- CDC-driven invalidation
- Cluster / Sentinel failure behavior
