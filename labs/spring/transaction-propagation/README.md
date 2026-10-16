# Spring 事务传播实验 / Spring Transaction Propagation Lab

## 目标 / Goal

中文：

这个 Lab 使用 Spring JDBC + H2，把事务传播从注解名词变成可以观察的数据库行为。

重点验证：

- REQUIRED：多个逻辑事务范围共享一个物理事务
- REQUIRES_NEW：挂起外层事务并创建独立物理事务
- NESTED：同一物理事务内使用 JDBC savepoint
- UnexpectedRollbackException 为什么出现
- inner exception 被 outer catch 后为什么仍然可能整体回滚
- self-invocation 为什么让 REQUIRES_NEW 失效
- Proxy Boundary 与 Transaction Boundary 的关系

English:

This lab uses Spring JDBC and H2 to make transaction propagation observable through committed rows, rollback behavior, and JDBC connection identity.

## 版本 / Versions

~~~text
Spring Framework 7.0.9
H2 2.5.252
Java 17 compiler baseline
~~~

## 核心模型 / Core Model

Spring 声明式事务本质上建立在 AOP Proxy 上：

~~~text
Caller
  |
  v
Transaction Proxy
  |
  +--> begin / join / suspend / savepoint
  |
  v
Target Method
  |
  v
commit / rollback
~~~

因此先记住：

> Transaction boundary is created when the call crosses a transactional proxy.

如果调用没有经过 Proxy，新的事务传播规则就没有机会被拦截器应用。

## Logical Transaction vs Physical Transaction

这是理解 REQUIRED 的关键。

~~~text
Outer @Transactional(REQUIRED)
        |
        +-- logical transaction scope A
        |
        +--> Inner @Transactional(REQUIRED)
                |
                +-- logical transaction scope B
~~~

A 和 B 是两个逻辑事务范围，但默认映射到：

~~~text
one physical JDBC transaction
one transaction-bound connection
~~~

因此 inner 把共享事务标记为 rollback-only，会影响 outer 最终提交。

English:

Each REQUIRED method has a logical transaction scope, but nested REQUIRED calls in the same thread normally participate in the same physical transaction.

## 数据表 / Database Table

实验只使用一张简单表：

~~~sql
tx_event(
  id,
  label
)
~~~

每个场景执行后，程序都会查询真正提交到 H2 的 label，并自动断言结果。

这样可以区分：

~~~text
method executed
!=
data committed
~~~

## Connection Identity / JDBC Connection Identity

每个事务方法都会打印：

~~~text
transactionActive=true
connectionId=12345678
~~~

这让我们可以直接观察：

### REQUIRED

~~~text
outer connectionId = A
inner connectionId = A
~~~

### REQUIRES_NEW

~~~text
outer connectionId = A
inner connectionId = B
~~~

### NESTED

~~~text
outer connectionId = A
inner connectionId = A
~~~

NESTED 不是再开一个物理事务，而是在同一 JDBC transaction 上创建 savepoint。

## 场景 1：REQUIRED + Inner Failure + Outer Catch

调用：

~~~text
outer REQUIRED
  |
  +-- insert outer-required-before
  |
  +-- inner REQUIRED
  |     |
  |     +-- insert inner-required
  |     +-- throw RuntimeException
  |
  +-- catch inner exception
  |
  +-- insert outer-required-after
  |
  +-- outer returns normally
~~~

很多人会认为 outer 已经 catch 了异常，所以应该 commit。

但 inner REQUIRED 的事务拦截器已经把共享物理事务标记为 rollback-only。

因此 outer 最后尝试 commit 时：

~~~text
commit requested
      |
transaction is rollback-only
      |
      v
rollback
      |
      v
UnexpectedRollbackException
~~~

最终数据库：

~~~text
[]
~~~

也就是三条 insert 全部回滚。

关键结论：

> catch exception does not automatically clear rollback-only.

## 为什么需要 UnexpectedRollbackException

如果 Spring 静默回滚，然后 outer caller 还以为 commit 成功，会非常危险。

所以 Spring 明确抛出 UnexpectedRollbackException 告诉调用者：你尝试提交，但实际没有提交。

English:

UnexpectedRollbackException prevents the caller from incorrectly believing that a commit succeeded when a participating inner transaction had already marked the shared transaction rollback-only.

## 场景 2：REQUIRES_NEW Commit + Outer Rollback

调用：

~~~text
outer REQUIRED transaction A
  |
  +-- insert outer-new-before
  |
  +-- inner REQUIRES_NEW
  |       |
  |       +-- suspend transaction A
  |       +-- begin transaction B
  |       +-- insert inner-requires-new
  |       +-- commit B
  |       +-- resume A
  |
  +-- insert outer-new-after
  +-- throw RuntimeException
  |
  v
rollback A
~~~

最终数据库：

~~~text
inner-requires-new
~~~

outer 的两条数据回滚，但 inner 已经在独立事务 B 中提交。

关键结论：

> inner commit is independent from outer rollback.

## REQUIRES_NEW 的连接池风险 / Connection Pool Risk

当 outer 事务已经占用一个 Connection 时，REQUIRES_NEW 需要另一个 Connection。

~~~text
outer thread
  |
connection A held
  |
REQUIRES_NEW
  |
needs connection B
~~~

如果很多线程同时这样做，连接池容量不足，可能导致线程互相等待 Connection。

所以生产环境不能只记 REQUIRES_NEW = 独立事务，还要考虑外层连接 + 内层新连接的容量。

## 场景 3：NESTED Rollback to Savepoint

调用：

~~~text
outer REQUIRED
  |
  +-- insert outer-nested-before
  |
  +-- create savepoint
  |
  +-- inner NESTED
  |      |
  |      +-- insert inner-nested
  |      +-- throw
  |
  +-- rollback to savepoint
  +-- outer catches exception
  +-- insert outer-nested-after
  |
  v
commit outer transaction
~~~

最终数据库：

~~~text
outer-nested-before
outer-nested-after
~~~

inner-nested 被回滚到 savepoint。

关键区别：

~~~text
REQUIRES_NEW
-> two physical transactions

NESTED
-> one physical transaction
-> savepoint based partial rollback
~~~

这个 Lab 使用 DataSourceTransactionManager + H2 JDBC，所以可以展示 JDBC savepoint 行为。

## 场景 4：Self-invocation 让 REQUIRES_NEW 失效

代码结构：

~~~java
@Transactional
public void outerCallsRequiresNewOnThisThenFails() {
    repository.insert("self-outer");

    this.innerRequiresNew();

    throw new IllegalStateException("outer failure");
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void innerRequiresNew() {
    repository.insert("self-inner");
}
~~~

调用路径：

~~~text
Caller
  |
Proxy.outer()
  |
Target.outer()
  |
this.innerRequiresNew()
  |
Target.innerRequiresNew()
~~~

inner 调用没有再次经过 Proxy，因此 REQUIRES_NEW interceptor 没有执行。

结果：

~~~text
self-outer
self-inner
~~~

都在 outer 的同一个事务里。

outer 最后 rollback，因此数据库：

~~~text
[]
~~~

这正是上一个 AOP Lab 的 self-invocation 问题在事务上的实际后果。

## 场景 5：同一个 Inner 方法从 Proxy 外部调用

清空表后，直接从外部调用：

~~~java
selfInvocationService.innerRequiresNew();
~~~

这次调用经过 Proxy：

~~~text
Caller
  |
Proxy
  |
REQUIRES_NEW transaction
  |
Target.innerRequiresNew()
~~~

最终：

~~~text
self-inner
~~~

成功提交。

这说明：

> 注解没有变化，差异只在调用是否经过 Proxy。

## REQUIRED / REQUIRES_NEW / NESTED 对比

| Propagation | Physical Transaction | Connection | Inner Rollback |
| --- | --- | --- | --- |
| REQUIRED | join existing | usually same | marks shared transaction rollback-only |
| REQUIRES_NEW | new independent transaction | different while outer is suspended | independent from outer |
| NESTED | same physical transaction | same | rollback to savepoint |

## 运行 / Run

需要 JDK 17+ 和 Maven。

~~~bash
mvn clean compile
mvn exec:java   -Dexec.mainClass=dev.xbhou.javalab.spring.tx.App
~~~

程序会依次执行五个场景，并自动校验数据库最终状态。

## 预期输出摘要 / Expected Summary

~~~text
Scenario 1 REQUIRED
caught UnexpectedRollbackException
committed rows=[]

Scenario 2 REQUIRES_NEW
outer failed as expected
committed rows=[inner-requires-new]

Scenario 3 NESTED
inner nested failure caught
committed rows=[outer-nested-before, outer-nested-after]

Scenario 4 self-invocation
outer failed as expected
committed rows=[]

Scenario 5 external REQUIRES_NEW
committed rows=[self-inner]
~~~

## 默认 rollback 规则 / Default Rollback Rules

Spring 声明式事务默认情况下：

~~~text
RuntimeException / Error
-> rollback

checked Exception
-> normally no automatic rollback
~~~

如果业务异常是 checked exception，要明确设计 rollbackFor / noRollbackFor。

## 常见误区 / Common Misconceptions

1. catch 住异常就一定可以提交。错误，共享事务可能已经是 rollback-only。
2. REQUIRES_NEW 是当前事务里的子事务。错误，它是独立物理事务。
3. NESTED 和 REQUIRES_NEW 一样。错误，NESTED 通常是同一 JDBC transaction 上的 savepoint。
4. 方法标了 REQUIRES_NEW 就一定生效。错误，调用必须经过 Transaction Proxy。
5. Transactional 能跨 RPC 自动传播。错误，本地 Spring transaction context 不是分布式事务上下文。

## 生产设计问题 / Production Design Questions

1. 这个方法需要加入当前事务还是独立提交？
2. inner rollback 是否应该导致 outer rollback？
3. outer rollback 后 inner 结果是否必须保留？
4. REQUIRES_NEW 是否会增加 Connection Pool 压力？
5. NESTED 的 transaction manager 是否支持 savepoint？
6. checked exception 是否需要 rollbackFor？
7. 是否存在 self-invocation？
8. Transactional 方法是否通过 Spring Bean Proxy 调用？
9. 长事务里是否包含 RPC / HTTP 调用？
10. 是否把数据库事务错误地当成分布式事务？
11. UnexpectedRollbackException 是否被错误吞掉？
12. transaction timeout 和 lock timeout 如何设计？

## 与前两个 Spring Lab 的关系 / Relationship to Earlier Spring Labs

~~~text
Bean Lifecycle
      |
      v
BeanPostProcessor
      |
      v
AOP Proxy
      |
      v
Transaction Interceptor
      |
      v
Transaction Boundary
~~~

现在可以把 Spring 的几个核心概念串起来：

> Transactional 不是数据库魔法，而是 Spring AOP 在方法调用边界上组织 TransactionManager 的结果。

## 下一步 / Next Step

Phase 3 下一步进入：

> Spring Boot Auto-configuration

会重点观察：

~~~text
SpringApplication
      |
AutoConfigurationImportSelector
      |
AutoConfiguration candidates
      |
Conditional annotations
      |
matched / did not match
      |
BeanDefinition
      |
ApplicationContext
~~~

并用 ConditionEvaluationReport 看清楚为什么某个 Bean 被自动配置出来。
