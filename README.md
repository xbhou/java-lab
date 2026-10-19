# Java Lab

> 中文 | English

## 中文

这是一个用于 **Java 工程实践、并发编程与后端系统设计** 的个人实验仓库。

这里的目标不是收集零散代码片段，而是把技术问题变成**可运行、可观察、可解释**的实验。

### 仓库结构

```text
java-lab
├── src/                         # 历史 Java 学习代码
├── labs/                        # 新的独立实验
│   ├── java-core/
│   ├── concurrency/
│   ├── jvm/
│   ├── spring/
│   ├── mysql/
│   ├── redis/
│   ├── kafka/
│   ├── grpc/
│   └── distributed-system/
└── docs/
    └── ROADMAP.md
```

### 现代实验

#### CompletableFuture 并行报价聚合

```text
labs/concurrency/completable-future-quote
```

演示并发请求多个 Provider、单 Provider 超时隔离、结果归集以及最优报价选择。

#### CompletableFuture Deadline 与取消

```text
labs/concurrency/completable-future-deadline
```

演示整体请求 Deadline、异常隔离、部分结果返回，以及“取消 Future”和“真正停止底层任务”之间的区别。

#### ThreadPoolExecutor 行为

```text
labs/concurrency/thread-pool-behavior
```

演示 core thread、bounded queue、maximumPoolSize、线程池饱和与拒绝策略。

#### 线程池容量设计

```text
labs/concurrency/thread-pool-sizing
```

比较 CPU-bound 和 I/O-bound 任务在不同线程数下的表现，说明为什么线程池大小应该通过测量确定，而不是死背公式。

#### Retry + Backoff + Jitter + Deadline

```text
labs/distributed-system/retry-backoff-deadline
```

演示可重试异常分类、指数退避、Jitter、Deadline Budget，以及多层重试造成的流量放大风险。

#### Idempotency / 幂等

```text
labs/distributed-system/idempotency
```

演示 Idempotency Key、Request Fingerprint、处理中状态、成功结果复用、并发重复请求，以及同 Key 不同参数的冲突处理。

#### Distributed Lock / 分布式锁

```text
labs/distributed-system/distributed-lock
```

演示 Lease、Owner Token、安全释放、续租、锁过期以及 Fencing Token 如何防止 stale owner 写入下游资源。

#### Rate Limiting / 限流

```text
labs/distributed-system/rate-limiting
```

对比 Fixed Window、Sliding Window Log 和 Token Bucket，重点观察窗口边界突发、平滑限流和受控 Burst。

#### Circuit Breaker / 熔断

```text
labs/distributed-system/circuit-breaker
```

演示 CLOSED → OPEN → HALF_OPEN 状态机、Fail Fast、恢复探测，以及熔断器与 Retry、Rate Limiting 的职责边界。

#### Multi-provider Routing & Aggregation / 多 Provider 路由与聚合

```text
labs/distributed-system/multi-provider-routing
```

综合演示动态路由、并行 Provider 调用、单 Provider Timeout、Overall Deadline、Circuit Breaker、Partial Result 和 Best Quote Selection。

#### JVM 类加载生命周期 / JVM Class Loading Lifecycle

```text
labs/jvm/class-loading-lifecycle
```

通过编译期常量、普通 static 字段、`ClassLoader.loadClass`、`Class.forName` 和父子类初始化顺序，观察 Loading、Linking 与 Initialization 的区别。

#### JVM 内存区域 / JVM Memory Regions

```text
labs/jvm/memory-regions
```

通过受控 JVM 参数分别观察 Heap、Thread Stack 和 Metaspace，并区分 `StackOverflowError`、Heap OOM 与 Metaspace OOM。

#### GC 观察 / GC Observation

```text
labs/jvm/gc-observation
```

通过受控对象分配和 JDK Unified Logging 观察 Young GC、Survivor Age、Promotion、Old Generation 增长和 Full GC，并对比 SerialGC 与 G1 日志。

#### Thread Dump Analysis / 线程转储分析

```text
labs/jvm/thread-dump-analysis
```

通过实际制造 RUNNABLE、BLOCKED、WAITING、TIMED_WAITING、Deadlock 和线程池 Queue Backlog，练习使用 `jcmd Thread.print` / `jstack` 进行 JVM 线程故障排查。

#### Spring Bean 生命周期 / Spring Bean Lifecycle

```text
labs/spring/bean-lifecycle
```

观察 Bean 实例化、依赖注入、Aware 回调、BeanPostProcessor、`@PostConstruct`、`InitializingBean`、自定义 init/destroy 与 ApplicationContext 关闭时的完整生命周期。

#### Spring AOP

```text
labs/spring/aop
```

验证 Spring AOP 的 Proxy 模型，对比 JDK Dynamic Proxy 与 class-based proxy，并通过 self-invocation 场景解释为什么内部调用会绕过 Advice。

#### Spring Transaction Propagation / 事务传播

```text
labs/spring/transaction-propagation
```

使用 Spring JDBC + H2 验证 REQUIRED、REQUIRES_NEW、NESTED、UnexpectedRollbackException、JDBC savepoint 和 self-invocation，并通过 Connection identity 观察物理事务边界。

#### Spring Boot 自动配置 / Spring Boot Auto-configuration

```text
labs/spring/boot-auto-configuration
```

通过自定义 AutoConfiguration、AutoConfiguration.imports、ConditionalOnProperty、ConditionalOnMissingBean 和 ConditionEvaluationReport，观察 Spring Boot 为什么创建或跳过一个默认 Bean。

#### MySQL 索引实验 / MySQL Index Experiments

```text
labs/mysql/index-experiments
```

基于 MySQL 8.4 LTS 和 20 万行数据，对比无二级索引、单列索引、联合索引、最左前缀、覆盖索引、ICP、Sargable Predicate 和低选择性索引，并使用 EXPLAIN ANALYZE 观察实际执行工作量。

### 历史代码

根目录的 `src/` 保留了早期学习实验，例如：

- 算法
- 反射
- ClassLoader
- 单例模式
- synchronized / volatile
- 线程池
- 日期时间 API
- JavaScript Engine

这些代码不会一次性机械迁移。后续复习某个主题时，再把值得保留的内容重写成新的独立 Lab。

### Lab 规范

1. 一个 Lab 只回答一个明确问题。
2. 优先写可运行实验，而不是复制笔记。
3. 每个 Lab 应能独立理解和运行。
4. README 至少包含：问题、设计、运行方式、观察结果和工程意义。
5. 关键行为尽量通过测试或可重复实验验证。
6. 禁止提交公司代码、内部地址、凭证、生产数据或敏感配置。
7. README 默认使用中英双语。

### 学习路线

参见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## English

This is a personal lab for **Java engineering, concurrency, and backend system design**.

The goal is not to collect snippets. The repository turns technical questions into **runnable, observable, and explainable experiments**.

### Repository Structure

```text
java-lab
├── src/                         # Legacy Java study experiments
├── labs/                        # New standalone experiments
│   ├── java-core/
│   ├── concurrency/
│   ├── jvm/
│   ├── spring/
│   ├── mysql/
│   ├── redis/
│   ├── kafka/
│   ├── grpc/
│   └── distributed-system/
└── docs/
    └── ROADMAP.md
```

### Modern Labs

#### CompletableFuture Quote Aggregator

```text
labs/concurrency/completable-future-quote
```

Demonstrates concurrent provider requests, timeout isolation, result aggregation, and best-quote selection.

#### CompletableFuture Deadline & Cancellation

```text
labs/concurrency/completable-future-deadline
```

Demonstrates end-to-end deadlines, failure isolation, partial results, and the difference between cancelling a future and actually stopping the underlying work.

#### ThreadPoolExecutor Behavior

```text
labs/concurrency/thread-pool-behavior
```

Demonstrates core threads, bounded queues, maximum pool growth, saturation, and rejection behavior.

#### Thread Pool Sizing

```text
labs/concurrency/thread-pool-sizing
```

Compares CPU-bound and I/O-bound workloads across several pool sizes and shows why thread counts should be measured rather than chosen from a single formula.

#### Retry + Backoff + Jitter + Deadline

```text
labs/distributed-system/retry-backoff-deadline
```

Demonstrates retry classification, exponential backoff, jitter, deadline budgeting, and retry amplification risks.

#### Idempotency

```text
labs/distributed-system/idempotency
```

Demonstrates idempotency keys, request fingerprints, processing state, cached-success replay, concurrent duplicate requests, and same-key / different-payload conflict handling.

#### Distributed Lock

```text
labs/distributed-system/distributed-lock
```

Demonstrates lease-based locking, owner-checked release, renewal, lock expiry, stale-owner risks, and fencing tokens that protect downstream resources.

#### Rate Limiting

```text
labs/distributed-system/rate-limiting
```

Compares Fixed Window, Sliding Window Log, and Token Bucket behavior, including boundary bursts and controlled burst capacity.

#### Circuit Breaker

```text
labs/distributed-system/circuit-breaker
```

Demonstrates CLOSED → OPEN → HALF_OPEN transitions, fail-fast behavior, recovery probes, and how circuit breaking differs from retry and rate limiting.

#### Multi-provider Routing & Aggregation

```text
labs/distributed-system/multi-provider-routing
```

Combines dynamic routing, parallel provider calls, provider timeouts, overall deadlines, circuit breaking, partial results, and best-quote selection.

#### JVM Class Loading Lifecycle

```text
labs/jvm/class-loading-lifecycle
```

Explores loading, linking, and initialization through compile-time constants, static fields, `ClassLoader.loadClass`, `Class.forName`, and parent/child initialization order.

#### JVM Memory Regions

```text
labs/jvm/memory-regions
```

Uses controlled JVM limits to observe Heap, Thread Stack, and Metaspace, and to distinguish stack overflow, heap OOM, and metaspace OOM.

#### GC Observation

```text
labs/jvm/gc-observation
```

Uses controlled allocation pressure and JDK unified GC logging to observe young collections, survivor aging, promotion, old-generation growth, full collections, and SerialGC vs G1 behavior.

#### Thread Dump Analysis

```text
labs/jvm/thread-dump-analysis
```

Creates RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, deadlock, and thread-pool backlog scenarios, then diagnoses them with `jcmd Thread.print` and `jstack`.

#### Spring Bean Lifecycle

```text
labs/spring/bean-lifecycle
```

Observes bean instantiation, dependency injection, aware callbacks, BeanPostProcessor hooks, `@PostConstruct`, `InitializingBean`, custom init/destroy methods, and ApplicationContext shutdown.

#### Spring AOP

```text
labs/spring/aop
```

Demonstrates Spring AOP's proxy model, compares JDK and class-based proxies, and shows why self-invocation bypasses advice.

#### Spring Transaction Propagation

```text
labs/spring/transaction-propagation
```

Uses Spring JDBC and H2 to demonstrate REQUIRED, REQUIRES_NEW, NESTED, UnexpectedRollbackException, JDBC savepoints, self-invocation, and physical transaction boundaries.

#### Spring Boot Auto-configuration

```text
labs/spring/boot-auto-configuration
```

Demonstrates custom auto-configuration discovery, conditional matching, back-off behavior, and ConditionEvaluationReport diagnostics.

#### MySQL Index Experiments

```text
labs/mysql/index-experiments
```

Uses MySQL 8.4 LTS and a 200,000-row dataset to compare no secondary index, single and composite indexes, leftmost-prefix behavior, covering indexes, ICP, sargable predicates, and cost-based optimizer decisions with EXPLAIN ANALYZE.

### Legacy Topics

The legacy `src/` tree contains experiments covering:

- algorithms
- reflection
- class loading
- singleton patterns
- synchronized / volatile
- thread pools
- date and time APIs
- JavaScript engine experiments

These examples are preserved as historical learning assets. They will be rewritten into modern labs only when a topic is revisited.

### Lab Rules

1. One lab, one concrete technical question.
2. Prefer runnable experiments over copied notes.
3. Keep each lab small enough to understand independently.
4. Each README should explain the problem, design, run steps, observations, and engineering implications.
5. Validate important behavior with tests or reproducible experiments.
6. Never commit company code, credentials, internal endpoints, production data, or sensitive configuration.
7. README files are bilingual by default.

### Roadmap

See [docs/ROADMAP.md](docs/ROADMAP.md).
