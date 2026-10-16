# Spring

## 中文

Spring Core 与 Spring Boot 实验目录，重点把 IoC、生命周期、代理、事务和自动配置做成可运行实验。

### Labs

#### Bean 生命周期 / Bean Lifecycle

路径：`bean-lifecycle/`

重点：

- BeanDefinition → Bean instance
- Instantiation 与 Dependency Injection
- BeanNameAware / BeanFactoryAware
- BeanPostProcessor
- @PostConstruct
- InitializingBean.afterPropertiesSet
- custom initMethod
- @PreDestroy / DisposableBean / destroyMethod
- BeanFactory vs ApplicationContext
- 生命周期顺序自动校验

这个 Lab 从旧 `studyDemo` 的 ApplicationContext、BeanFactory 和 InitializingBean 实验升级而来，但重新拆分了“被管理的 Bean”和“容器扩展点”的角色。

#### AOP

路径：`aop/`

重点：

- Spring AOP runtime proxy
- JDK Dynamic Proxy
- class-based proxy
- Aspect / Pointcut / Advice / Target / Proxy
- Around Advice
- self-invocation
- AopUtils proxy detection
- 与 Transactional 的关系

#### 事务传播 / Transaction Propagation

路径：`transaction-propagation/`

重点：

- REQUIRED
- REQUIRES_NEW
- NESTED
- logical vs physical transaction
- UnexpectedRollbackException
- JDBC savepoint
- Connection identity
- self-invocation
- Proxy Boundary 与 Transaction Boundary

后续覆盖：

- Spring Boot Auto Configuration

---

## English

Focused Spring Core and Spring Boot experiments that make IoC, lifecycle, proxies, transactions, and auto-configuration observable.

### Labs

#### Bean Lifecycle / Bean 生命周期

Path: `bean-lifecycle/`

Focus:

- BeanDefinition to bean instance
- instantiation vs dependency injection
- BeanNameAware / BeanFactoryAware
- BeanPostProcessor
- @PostConstruct
- InitializingBean.afterPropertiesSet
- custom init method
- @PreDestroy / DisposableBean / destroy method
- BeanFactory vs ApplicationContext
- runtime lifecycle-order verification

This lab modernizes the legacy studyDemo ApplicationContext, BeanFactory, and InitializingBean experiments while separating managed-bean responsibilities from container extension points.

#### AOP

Path: `aop/`

Focus:

- Spring AOP runtime proxies
- JDK dynamic proxies
- class-based proxies
- aspect / pointcut / advice / target / proxy
- around advice
- self-invocation
- proxy detection with AopUtils
- relationship to transactional proxies

#### Transaction Propagation / 事务传播

Path: `transaction-propagation/`

Focus:

- REQUIRED
- REQUIRES_NEW
- NESTED
- logical vs physical transactions
- UnexpectedRollbackException
- JDBC savepoints
- connection identity
- self-invocation
- proxy boundaries vs transaction boundaries

Planned next topics:

- Spring Boot auto-configuration
