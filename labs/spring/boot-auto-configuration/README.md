# Spring Boot 自动配置实验 / Spring Boot Auto-configuration Lab

## 目标 / Goal

中文：

这个 Lab 回答 Spring Boot 最核心的问题之一：

> 一个我没有显式注册的 Bean，为什么会自动出现在 ApplicationContext 里？

重点观察：

- SpringBootConfiguration + EnableAutoConfiguration
- AutoConfigurationImportSelector
- AutoConfiguration.imports 候选发现
- AutoConfiguration
- ConditionalOnProperty
- ConditionalOnMissingBean
- auto-configuration back-off
- ConditionEvaluationReport

English:

This lab makes Spring Boot auto-configuration observable. It shows how candidates are discovered, how conditions are evaluated, why a default bean appears, and why auto-configuration backs off when the user supplies a bean.

## 版本 / Versions

~~~text
Spring Boot 4.1.1
Java 17 baseline
~~~

## 简化流程 / Simplified Pipeline

~~~text
DemoApplication
  |
  +-- SpringBootConfiguration
  |
  +-- EnableAutoConfiguration
          |
          v
AutoConfigurationImportSelector
          |
          v
Auto-configuration candidates
          |
          +-- META-INF/spring/
          |   org.springframework.boot.autoconfigure.AutoConfiguration.imports
          |
          v
DemoGreetingAutoConfiguration
          |
          +-- ConditionalOnProperty
          |
          +-- ConditionalOnMissingBean
          |
          v
match / no match
          |
          v
BeanDefinition
          |
          v
GreetingService Bean
~~~

核心：

> Spring Boot 不是扫描到一个配置类就无条件创建 Bean，而是先发现 AutoConfiguration 候选，再根据 Condition 决定是否应用。

## 为什么故意不使用 Component Scan / Why Component Scan Is Avoided

应用类放在：

~~~text
dev.xbhou.javalab.boot.app
~~~

自动配置放在 sibling package：

~~~text
dev.xbhou.javalab.boot.autoconfigure
~~~

因此普通应用包扫描不会把 DemoGreetingAutoConfiguration 扫进来。

真正让 Boot 发现它的是：

~~~text
src/main/resources/
META-INF/spring/
org.springframework.boot.autoconfigure.AutoConfiguration.imports
~~~

文件内容：

~~~text
dev.xbhou.javalab.boot.autoconfigure.DemoGreetingAutoConfiguration
~~~

这样可以把：

~~~text
ordinary component scanning
~~~

和：

~~~text
Boot auto-configuration discovery
~~~

清楚拆开。

## 自定义 AutoConfiguration / Custom AutoConfiguration

核心配置：

~~~java
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "demo.greeting",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DemoGreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GreetingService.class)
    public GreetingService greetingService(
            Environment environment
    ) {
        ...
    }
}
~~~

这里有两个关键 Condition。

### Property Condition

以下情况匹配：

~~~text
demo.greeting.enabled=true
~~~

或者属性完全不存在，因为：

~~~text
matchIfMissing=true
~~~

### Missing Bean Condition

只有 ApplicationContext 中还没有 GreetingService 时，Boot 才提供默认 Bean。

这就是常说的：

> Auto-configuration backs off when the application provides its own bean.

## 场景 1：默认自动配置生效 / Default Auto-configuration Matches

不配置属性，也不提供用户 GreetingService。

条件：

~~~text
demo.greeting.enabled missing
+
matchIfMissing=true
+
GreetingService missing
~~~

结果：

~~~text
GreetingService count=1
bean class=DefaultGreetingService
greeting=Hello, Vincent
~~~

ConditionEvaluationReport 中相关条件应显示 MATCH。

## 场景 2：Property 关闭自动配置 / Property Disables Auto-configuration

设置：

~~~text
demo.greeting.enabled=false
~~~

结果：

~~~text
ConditionalOnProperty
      |
      v
NO MATCH
      |
      v
GreetingService count=0
~~~

这说明排查 Boot 配置问题时，不应该只问：

> 为什么 Bean 没有创建？

更应该问：

> 哪个 Condition 没匹配？

## 场景 3：用户 Bean 让 AutoConfiguration Back Off

用户显式提供：

~~~java
@Bean
public GreetingService userGreetingService() {
    return new UserGreetingService();
}
~~~

此时：

~~~text
user GreetingService already exists
      |
ConditionalOnMissingBean
      |
      v
NO MATCH
      |
default GreetingService not created
~~~

最终：

~~~text
GreetingService count=1
bean class=UserGreetingService
greeting=Custom, Vincent
~~~

这体现 Spring Boot 的设计：

> provide opinionated defaults, but get out of the way when the application makes an explicit choice.

## ConditionEvaluationReport

程序直接读取：

~~~java
ConditionEvaluationReport.get(
    context.getBeanFactory()
)
~~~

然后只筛选 DemoGreetingAutoConfiguration 相关 source。

典型结构：

~~~text
source=...DemoGreetingAutoConfiguration
  MATCH     OnPropertyCondition -> ...

source=...DemoGreetingAutoConfiguration#greetingService
  MATCH     OnBeanCondition -> ...
~~~

用户覆盖场景则可能看到：

~~~text
NO MATCH  OnBeanCondition -> ...
~~~

ConditionEvaluationReport 的真正价值不是告诉你最终有没有 Bean，而是回答：

> 为什么 match 或 no match？

## Boot debug 模式 / Debug Mode

真实 Spring Boot 应用可以直接：

~~~bash
java -jar app.jar --debug
~~~

Boot 会输出条件评估报告。

因此实际排查流程可以是：

~~~text
unexpected / missing bean
        |
        v
identify AutoConfiguration
        |
        v
Condition Evaluation Report
        |
        +-- classpath condition?
        +-- property condition?
        +-- bean condition?
        +-- web application condition?
        |
        v
find exact reason
~~~

## 常见 Condition / Common Conditions

常见条件包括：

~~~text
ConditionalOnClass
ConditionalOnMissingClass
ConditionalOnBean
ConditionalOnMissingBean
ConditionalOnProperty
ConditionalOnResource
ConditionalOnWebApplication
ConditionalOnNotWebApplication
~~~

它们分别从这些维度影响 BeanDefinition：

~~~text
classpath
bean graph
environment
resource
application type
~~~

## 为什么 Classpath 会改变 ApplicationContext

Spring Boot AutoConfiguration 的重要输入之一就是：

> classpath 上有什么依赖？

典型思路：

~~~text
library exists
+
required property matches
+
user did not provide bean
        |
        v
AutoConfiguration matches
        |
        v
infrastructure bean appears
~~~

因此 Maven / Gradle dependency 本身就可能改变 Spring Boot 容器结构。

## AutoConfiguration.imports 是什么

自定义自动配置必须让 Boot 能发现。

本 Lab 使用：

~~~text
META-INF/spring/
org.springframework.boot.autoconfigure.AutoConfiguration.imports
~~~

每一行写一个 AutoConfiguration class name。

可以理解成：

~~~text
library jar
  |
  +-- Java classes
  |
  +-- AutoConfiguration.imports
          |
          v
Boot application starts
          |
          v
candidate discovered
          |
          v
conditions evaluated
~~~

这也是 Starter / library 为使用方提供自动装配能力的关键机制之一。

## 为什么 AutoConfiguration 不应该被普通扫描发现

如果它同时被普通 ComponentScan 发现：

~~~text
ordinary scanning
+
auto-configuration import
~~~

就会模糊：

- 配置来自哪里
- Condition 是否真的控制了加载
- 用户配置为什么能否覆盖

因此自定义 AutoConfiguration 应放在独立 package，并避免成为应用普通 component scan 的目标。

## SpringBootApplication 做了什么

日常应用通常写：

~~~java
@SpringBootApplication
public class Application {
}
~~~

它组合了多个重要能力，包括：

~~~text
SpringBootConfiguration
EnableAutoConfiguration
ComponentScan
~~~

本 Lab 故意不用完整 SpringBootApplication，而只写：

~~~text
SpringBootConfiguration
+
EnableAutoConfiguration
~~~

目的是把：

~~~text
component scanning
~~~

和：

~~~text
auto-configuration
~~~

拆开观察。

## 用户配置与自动配置的顺序 / User Configuration and Back-off

可以先建立这个心智模型：

~~~text
user configuration
       |
       v
user BeanDefinitions
       |
       v
auto-configuration evaluates conditions
       |
       +-- user already configured?
       |       |
       |       v
       |     back off
       |
       +-- user did not configure?
               |
               v
           provide default
~~~

所以 Boot 并不是夺走配置权，而是在没有特殊要求时提供默认值。

## 运行 / Run

需要：

~~~text
JDK 17+
Maven 3.6.3+
~~~

运行：

~~~bash
mvn clean compile

mvn exec:java   -Dexec.mainClass=dev.xbhou.javalab.boot.app.App
~~~

程序会创建三个独立 ApplicationContext：

~~~text
1. default auto-configuration
2. property disabled
3. user bean override
~~~

每个场景都会打印：

- GreetingService bean count
- 实际 Bean 类型
- greeting 结果
- DemoGreetingAutoConfiguration 的 ConditionEvaluationReport

## 预期结果 / Expected Results

~~~text
=== Scenario 1: default auto-configuration ===
GreetingService count=1
bean class=DefaultGreetingService
greeting=Hello, Vincent
condition: MATCH

=== Scenario 2: property disables auto-configuration ===
GreetingService count=0
condition: NO MATCH

=== Scenario 3: user bean makes auto-configuration back off ===
GreetingService count=1
bean class=UserGreetingService
greeting=Custom, Vincent
missing-bean condition: NO MATCH
~~~

## 与前面 Spring Labs 的关系 / Relationship to Earlier Spring Labs

现在 Phase 3 可以串起来：

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
      |
      v
Spring Boot Auto-configuration
      |
      v
Where infrastructure BeanDefinitions come from
~~~

前面的 Lab 更多在回答：

> Bean 创建以后发生什么？

这个 Lab 开始回答：

> BeanDefinition 为什么一开始会出现在容器里？

## 生产排查方法 / Production Troubleshooting

遇到 Boot 自动配置问题，按这个顺序排查：

1. Bean 是否真的存在？
2. Bean 的实际运行时类型是什么？
3. 哪个 Configuration / AutoConfiguration 创建它？
4. ConditionEvaluationReport 怎么说？
5. classpath 条件是否满足？
6. property 是否满足？
7. 是否因为用户 Bean 导致 back-off？
8. 是否 exclude 了某个 AutoConfiguration？
9. 是否存在多个同类型 Bean？
10. 是否被 package scan 意外扫描？

## 生产与面试问题 / Production & Interview Questions

1. Auto-configuration 和 ComponentScan 有什么区别？
2. EnableAutoConfiguration 做了什么？
3. AutoConfigurationImportSelector 的职责是什么？
4. AutoConfiguration.imports 的作用是什么？
5. ConditionalOnClass 为什么能根据依赖决定配置？
6. ConditionalOnMissingBean 为什么是 back-off 的关键？
7. 为什么用户 Bean 应优先于默认自动配置？
8. ConditionEvaluationReport 怎么排查问题？
9. debug 模式为什么能看到自动配置报告？
10. 如何 exclude 某个 AutoConfiguration？
11. Starter 和 AutoConfiguration 有什么关系？
12. 为什么自定义 AutoConfiguration 不应该被普通 ComponentScan 扫到？

## Phase 3 总结 / Phase 3 Summary

完成这个 Lab 后，Phase 3 基础路线完成：

~~~text
Bean Lifecycle
      |
AOP
      |
Transaction Propagation
      |
Spring Boot Auto-configuration
~~~

你已经从：

> Spring 如何管理一个对象？

推进到了：

> Spring Boot 为什么能根据 classpath、properties 和已有 Bean 自动装配整套基础设施？

下一步建议进入 Phase 4：Backend Infrastructure，优先从 MySQL Index Experiments 开始。
