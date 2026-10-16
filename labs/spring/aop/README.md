# Spring AOP 实验 / Spring AOP Lab

## 目标 / Goal

中文：

这个 Lab 重点验证 Spring AOP 的几个关键事实：

- Spring AOP 是基于 Proxy 的
- 有接口时默认可以使用 JDK Dynamic Proxy
- 没有接口时使用 class-based proxy
- Advice 只会拦截经过 Proxy 的方法调用
- self-invocation 会绕过 Proxy
- getBean() 拿到的对象可能不是原始 Target

English:

This lab demonstrates the proxy-based nature of Spring AOP, the difference between JDK and class-based proxies, advice execution, and why self-invocation bypasses advice.

## 核心结构 / Core Structure

~~~text
Caller
  |
  v
Proxy
  |
  +--> Advice / Interceptor
  |
  v
Target
  |
  v
Business Method
~~~

最重要的一点：

> 调用必须先经过 Proxy，Advice 才有机会执行。

Advice runs only when the invocation reaches the proxy first.

## AOP 概念 / Core Concepts

- Aspect：横切关注点，例如 logging、metrics、transaction。
- Join Point：Spring AOP 主要支持 method execution join points。
- Pointcut：决定哪些方法需要拦截。
- Advice：在 join point 前后执行的逻辑。
- Target：真正的业务对象。
- Proxy：Spring 在 Target 外面创建的代理对象。

本 Lab 使用自定义注解 Traceable 作为 Pointcut，并使用 Around Advice。

## 场景 1：JDK Dynamic Proxy

业务接口：

~~~java
public interface PaymentService {
    void outer();
    void inner();
}
~~~

实现：

~~~java
public class PaymentServiceImpl
        implements PaymentService {
}
~~~

配置：

~~~java
@EnableAspectJAutoProxy(proxyTargetClass = false)
~~~

因为 Bean 实现了接口，所以 Spring 可以生成 JDK Dynamic Proxy。

程序会验证：

~~~java
AopUtils.isJdkDynamicProxy(paymentService)
~~~

简化理解：

~~~text
PaymentService interface
        |
        v
JDK Proxy
        |
        v
PaymentServiceImpl
~~~

## 场景 2：Class-based Proxy

另一个 Bean 没有业务接口：

~~~java
public class ClassOnlyService {
    @Traceable
    public void run() {
    }
}
~~~

Spring 会使用 class-based proxy。

程序验证：

~~~java
AopUtils.isCglibProxy(classOnlyService)
~~~

运行时类型通常会包含 SpringCGLIB。

~~~text
ClassOnlyService
      |
      v
generated subclass proxy
      |
      v
target behavior
~~~

## JDK Proxy vs Class-based Proxy

| 维度 / Dimension | JDK Dynamic Proxy | Class-based Proxy |
| --- | --- | --- |
| 基础 / Based on | Interface | Class subclassing |
| 是否需要接口 | Yes | No |
| 常见运行时类型 | jdk.proxy... | SpringCGLIB |
| final class | 不影响接口代理 | 无法 subclass |
| final method | 接口调用仍由代理入口接住 | 无法 override，因此无法正常增强 |

## 场景 3：Around Advice 调用链

直接调用：

~~~java
paymentService.inner();
~~~

预期事件：

~~~text
advice before inner
target inner
advice after inner
~~~

说明：

~~~text
Caller
  |
Proxy
  |
Advice before
  |
Target.inner
  |
Advice after
~~~

## 场景 4：Self-invocation

这是本 Lab 最重要的场景。

~~~java
@Traceable
public void outer() {
    recorder.record("target outer start");

    this.inner();

    recorder.record("target outer end");
}
~~~

inner() 本身也标记了 Traceable。

但是调用：

~~~java
paymentService.outer();
~~~

预期只有：

~~~text
advice before outer
target outer start
target inner
target outer end
advice after outer
~~~

不会出现：

~~~text
advice before inner
advice after inner
~~~

原因：

~~~text
Caller
  |
Proxy.outer()
  |
Target.outer()
  |
this.inner()
  |
Target.inner()
~~~

this.inner() 已经发生在 Target 内部，不会重新经过外面的 Proxy。

所以：

> self-invocation bypasses Spring AOP proxy advice.

## 为什么这和 Transactional 有关系 / Why This Matters for Transactions

Spring 声明式事务通常也是基于 AOP Proxy。

因此：

~~~java
@Transactional
public void outer() {
    inner();
}

@Transactional
public void inner() {
}
~~~

如果 outer 内部直接执行 this.inner()，inner 上独立配置的事务拦截逻辑不会再次经过 Transaction Proxy。

真正的问题不是“注解神秘失效”，而是：

> 这次调用没有经过 Proxy boundary。

## Self-invocation 怎么处理 / Handling Self-invocation

优先考虑：

1. 把需要独立 Advice 的逻辑重构到另一个 Spring Bean。
2. 必要时注入自己的 Proxy，但要谨慎处理自引用。
3. AopContext.currentProxy() 可以做到，但会让业务代码强耦合 Spring AOP，通常不作为首选。

## BeanPostProcessor 与 AOP / BeanPostProcessor and AOP

上一个 Bean Lifecycle Lab 已经看到：

~~~text
Bean initialization
      |
BeanPostProcessor
      |
possibly wrapped / proxied
      |
getBean()
~~~

AOP 自动代理基础设施会识别需要增强的 Bean，并返回 Proxy。

因此：

> getBean() 拿到的不一定是原始 Target。

## Spring AOP vs AspectJ

Spring AOP：

~~~text
runtime proxy
method execution
Spring Bean
~~~

AspectJ 可以通过 compile-time 或 load-time weaving 提供更广泛的 join point 模型。

本 Lab 使用 AspectJ annotation style 和 pointcut expression，但执行机制仍然是：

> Spring AOP runtime proxy

而不是 AspectJ bytecode weaving。

## 运行 / Run

需要 JDK 17+ 和 Maven。

~~~bash
mvn clean compile
mvn exec:java   -Dexec.mainClass=dev.xbhou.javalab.spring.aop.App
~~~

核心输出应类似：

~~~text
=== Proxy types ===
paymentService jdkProxy=true
classOnlyService classProxy=true

=== self invocation ===
advice before outer
target outer start
target inner
target outer end
advice after outer

=== direct inner call ===
advice before inner
target inner
advice after inner
~~~

程序会自动校验这些关键行为。

## 与旧 studyDemo 的关系 / Relationship to Legacy studyDemo

旧项目曾包含 AopHelloWorld、AopService、HelloAspectJTest 和 aspectj-autoproxy 配置。

新 Lab 的升级点：

~~~text
旧：看到 Aspect 能执行
        |
        v
新：理解 Proxy 类型
        |
        +--> JDK Proxy
        +--> Class Proxy
        |
        v
理解 self-invocation
        |
        v
为 Transactional 建立基础
~~~

## 常见误区 / Common Misconceptions

1. 加了 Aspect 就修改了 Target Class。错误，Spring AOP 通常是在 Target 外创建 Proxy。
2. 方法上有 Advice 就一定执行。错误，调用必须经过 Proxy。
3. this.method() 会重新经过 Proxy。错误，这就是 self-invocation 问题。
4. Spring AOP 就是 AspectJ weaving。错误，Spring AOP 主要是 runtime proxy。
5. getBean() 一定拿到原始对象。错误，可能拿到 Proxy。

## 生产与面试问题 / Production & Interview Questions

1. Spring AOP 为什么需要 Proxy？
2. JDK Dynamic Proxy 和 class-based proxy 的区别是什么？
3. Spring 什么时候选择 JDK Proxy？
4. final class / final method 对 class proxy 有什么影响？
5. Pointcut、Advice、Aspect、Target、Proxy 分别是什么？
6. Around Advice 为什么能控制方法调用前后逻辑？
7. self-invocation 为什么绕过 AOP？
8. 为什么 Transactional 会遇到 self-invocation 问题？
9. getBean() 为什么可能拿到 Proxy？
10. Spring AOP 和 AspectJ weaving 有什么区别？
11. AOP Proxy 和 BeanPostProcessor 有什么关系？
12. 为什么不推荐业务代码大量依赖 AopContext.currentProxy()？

## 下一步 / Next Step

Phase 3 下一步进入：

> Transaction Propagation / 事务传播

重点验证：

~~~text
REQUIRED
REQUIRES_NEW
NESTED
~~~

以及 outer rollback、inner rollback、UnexpectedRollbackException、self-invocation 与 transaction boundary。
