# Java Lab 学习路线 / Java Lab Roadmap

> 开源源码学习计划 / Open-source source-reading plan: [OPEN_SOURCE_LEARNING_PATH.md](./OPEN_SOURCE_LEARNING_PATH.md)
>
> 当前推荐起点 / Current recommended starting point: **Caffeine → Resilience4j → Nacos → gRPC Java**

## 阶段 1 — Java 与并发 / Phase 1 — Java & Concurrency

- [x] 保留并清理旧的 Java 实验 / Preserve and clean legacy Java experiments
- [x] 建立新的 `labs/` 结构 / Establish the new `labs/` structure
- [x] CompletableFuture 并行报价聚合 / CompletableFuture parallel quote aggregation
- [x] ThreadPoolExecutor 线程池大小与拒绝策略 / ThreadPoolExecutor sizing and rejection behavior
- [x] CPU 密集型 vs I/O 密集型线程池实验 / CPU-bound vs I/O-bound thread-pool sizing experiment
- [x] CompletableFuture 异常、截止时间与取消行为 / CompletableFuture exception, deadline, and cancellation behavior
- [ ] 虚拟线程对比实验 / Virtual threads comparison

## 阶段 2 — JVM / Phase 2 — JVM

- [x] 类加载生命周期 / Class loading lifecycle
- [x] 堆、栈、元空间实验 / Heap / stack / metaspace experiments
- [x] 使用 JDK 工具观察 GC / GC observation with JDK tools
- [x] 线程转储分析 / Thread dump analysis

## 阶段 3 — Spring / Phase 3 — Spring

- [x] Bean 生命周期 / Bean lifecycle
- [x] AOP
- [x] 事务传播机制 / Transaction propagation
- [x] Spring Boot 自动配置 / Spring Boot auto-configuration

## 阶段 4 — 后端基础设施 / Phase 4 — Backend Infrastructure

- [x] MySQL 索引实验 / MySQL index experiments
- [x] Redis 缓存模式 / Redis cache patterns
- [ ] Kafka Consumer Lag 与重试 / Kafka consumer lag and retry
- [ ] gRPC 超时与重试 / gRPC timeout and retry

## 阶段 5 — 分布式系统 / Phase 5 — Distributed Systems

- [x] 幂等 / Idempotency
- [x] 重试、指数退避、抖动与 Deadline Budget / Retry, exponential backoff, jitter, and deadline budget
- [x] 分布式锁 / Distributed lock
- [x] 限流 / Rate limiting
- [x] 熔断 / Circuit breaker
- [x] 多提供方路由与聚合 / Multi-provider routing and aggregation

## 学习方法 / Working Method

每个主题都遵循以下步骤 / For every topic:

1. 明确问题 / Define the question.
2. 构建最小可运行实验 / Build the smallest runnable experiment.
3. 观察行为 / Observe behavior.
4. 解释为什么会这样 / Explain why it happens.
5. 记录权衡、边界和失败场景 / Record trade-offs and failure cases.

> 默认文档规范：中文 + English 双语。详细规则见根目录 `AGENTS.md`。
>
> Default documentation convention: bilingual Chinese + English. See root `AGENTS.md` for details.
