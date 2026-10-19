# Kafka Consumer Lag 与重试 / Kafka Consumer Lag & Retry

## 中文

### 目标

这个 Lab 不只演示 Producer / Consumer API，而是回答几个更接近生产的问题：

- Consumer Lag 到底是什么？
- Producer 比 Consumer 快时，Lag 如何增长？
- Consumer 追上后 Lag 如何回到 0？
- 为什么慢处理还可能影响 poll / rebalance？
- 失败时应阻塞当前 Partition 重试，还是移到 Retry Topic？
- Poison Message 为什么不能无限重试？
- Retry Topic 和 DLQ 如何设计？
- 重试为什么可能破坏同一个业务 Key 的处理顺序？
- offset 在什么时候 commit，会带来怎样的重复或丢失风险？

English:

This lab makes consumer lag, manual offset commits, retry topics, poison messages, DLQs, and ordering trade-offs observable with the native Kafka clients.

## 版本 / Version

~~~text
Apache Kafka 4.3.1
kafka-clients 4.3.1
Java 17 compiler baseline
~~~

## 运行结构 / Runtime Structure

~~~text
Kafka Broker
  |
  +-- lag topic
  +-- orders main topic
  +-- orders retry topic
  +-- orders DLQ topic
~~~

每次运行使用唯一 Topic suffix，避免旧消息和旧 offset 干扰。所有 Topic 都只有 1 个 Partition、replication factor 1，这是教学配置，不是生产高可用配置。

## 运行 / Run

需要 JDK 17+、Maven、Docker 和 Docker Compose。

~~~bash
cd labs/kafka/consumer-lag-retry
bash scripts/run.sh
~~~

Kafka 使用官方镜像 apache/kafka:4.3.1，暴露 localhost:9092。

手动运行：

~~~bash
docker compose up -d
mvn clean compile
mvn exec:java -Dexec.mainClass=dev.xbhou.javalab.kafka.lagretry.App
~~~

结束：

~~~bash
docker compose down
~~~

---

## 场景 1：Consumer Lag

一个 Partition 可以简化为：

~~~text
offset 0  event-0
offset 1  event-1
offset 2  event-2
...
offset 29 event-29
~~~

Consumer 处理后提交 committed offset，用来表达该 Consumer Group 恢复时应该从哪里继续。

如果成功处理 offset 4，通常提交的是：

~~~text
5
~~~

因为 committed offset 表示 next offset to consume。

### Lag 的教学定义

在本 Lab 的单分区普通消费场景中：

~~~text
consumer lag
=
log end offset
-
committed offset
~~~

例如：

~~~text
Log End Offset = 30
Committed       = 5
Lag             = 25
~~~

程序使用 Kafka Admin API 查询 latest partition offset 和 Consumer Group committed offset，而不是维护一个模拟计数器。

### Step 1：Producer 先生产 30 条

~~~text
Log End Offset = 30
Committed       = 0
Lag             = 30
~~~

### Step 2：Slow Consumer 处理 5 条

Consumer 每条模拟约 60ms 的业务处理，然后手动 commit。

~~~text
Log End Offset = 30
Committed       = 5
Lag             = 25
~~~

### Step 3：Producer 再追加 20 条

~~~text
Log End Offset = 50
Committed       = 5
Lag             = 45
~~~

这说明：

~~~text
producer progress > consumer progress
              |
              v
lag grows
~~~

Consumer 可能没有任何异常，但只要 Lag 持续增长，就说明消费能力无法跟上输入。

### Step 4：Consumer 追平

继续处理剩余 45 条：

~~~text
Log End Offset = 50
Committed       = 50
Lag             = 0
~~~

如果长期 producer = 5000 msg/s、consumer = 3000 msg/s，Lag 大致会以 2000 msg/s 持续增加。

真正重要的不只是某一个 Lag 数字，还包括：

> Lag trend。

---

## Lag 与 Message Age

Lag 是消息数量距离，但 lag = 100 无法告诉你最旧未处理消息是 100ms 还是 30min 前产生的。

生产监控最好同时关注：

- offset lag
- oldest unprocessed message age
- producer rate
- consumer rate
- processing latency
- business freshness SLA

离线任务和实时资金状态流对 Lag 的容忍度完全不同。

---

## 慢 Consumer 与 max.poll.interval.ms

如果处理逻辑就在 poll 线程：

~~~text
poll()
  |
process for a very long time
  |
next poll() arrives too late
~~~

超过 max.poll.interval.ms 后，使用 Consumer Group 管理时 Consumer 可能被认为失效并触发 Partition reassignment。

因此慢消费排查还要看：

- processing time per record
- max.poll.records
- time between poll
- max.poll.interval.ms
- rebalance count
- DB / RPC downstream latency

不能只把 max.poll.interval.ms 无限调大。

---

## 场景 2：Retry Topic + DLQ

主 Topic 按同一个 key order-1 写入：

~~~text
offset 0  OK:event-1
offset 1  TRANSIENT:event-2
offset 2  OK:event-3
offset 3  POISON:event-4
~~~

Message Processor 规则：

~~~text
OK
-> success

TRANSIENT
-> attempt 0 fails
-> retry attempt 1 succeeds

POISON
-> every attempt fails
~~~

这样可以同时观察 transient failure 和 poison message。

---

## 主 Topic 失败后的处理

event-2 失败时：

~~~text
consume event-2
     |
processing failure
     |
publish to retry topic
     |
commit original offset
     |
continue main partition
~~~

event-3 可以继续处理。

主流程先完成：

~~~text
event-1
event-3
~~~

Retry Consumer 稍后成功处理 event-2：

~~~text
event-1
event-3
event-2
~~~

程序会自动断言这个 completion order。

关键结论：

> Retry Topic 可以释放主 Partition，但可能牺牲严格业务顺序。

---

## Blocking Retry vs Retry Topic

Blocking Retry：

~~~text
event-1 success
event-2 fail
   |
retry same record
retry
retry
   |
event-3 cannot pass
~~~

优点是同一 Partition 顺序更容易保持；缺点是一个 poison / slow message 可以阻塞整个 Partition，形成 Head-of-line Blocking，Lag 持续增长。

Retry Topic：

~~~text
event-2 fails
   |
move to retry topic
   |
main partition continues event-3
~~~

优点是主消费流不会被一个失败消息长期卡住；缺点是原始顺序可能改变，还需要额外 Topic、Lag 监控和可靠性设计。

Retry Topic 不是无条件更好，而是 throughput / isolation 与 ordering 之间的权衡。

---

## Retry Backoff

Retry Consumer 根据 retry-attempt header 使用简单指数退避：

~~~text
attempt 1 -> 100ms
attempt 2 -> 200ms
attempt 3 -> 400ms
~~~

目的不是提供生产最佳参数，而是说明 tight-loop retry 往往只会加剧下游故障。

生产中还需要考虑：

- exponential backoff
- jitter
- maximum retry age
- downstream recovery time
- overall business deadline

这与前面的 Retry + Backoff + Jitter Lab 是同一个思想。

---

## Poison Message 与 DLQ

event-4 永远失败。如果无限重试：

~~~text
poison message
     |
retry
     |
retry
     |
retry forever
~~~

会导致无意义资源消耗、Retry Topic 永久积压和监控噪声。

本 Lab 最大尝试次数是 3。第 3 次仍失败后发送 DLQ。

DLQ Record 保留：

- original-topic
- original-offset
- final-attempt
- error
- original key / value

进入 DLQ 不等于任务完成。必须回答：

1. 谁监控 DLQ？
2. 多久处理？
3. 哪些异常允许修复后 replay？
4. Replay 是否幂等？
5. 原始消息是否已经产生部分 side effect？
6. Schema 已变化怎么办？
7. DLQ 保留多久？

没有运营流程的 DLQ 只是一个更隐蔽的消息墓地。

---

## Offset Commit 的可靠性窗口

本 Lab 为了机制清晰，采用：

~~~text
processing fails
      |
publish retry record
      |
commit source offset
~~~

如果 retry publish 成功，但 source offset commit 前崩溃：

~~~text
source record replayed
+
retry topic already contains a copy
~~~

可能出现重复，因此 downstream 通常仍要具备幂等能力。

如果反过来先 commit source offset，再 publish retry，然后中间崩溃：

~~~text
source committed
+
retry not published
~~~

可能丢失失败消息。

所以只是交换两步顺序并不能消灭问题。

更严格的 Kafka-to-Kafka 流程可以考虑 Kafka Transactions，把 output records 和 consumed offsets 放在同一个 Kafka transaction。

但如果 side effect 在 MySQL、HTTP API 或 Payment Provider，仍然需要重新分析端到端一致性，不能把 Kafka Exactly-Once 简化成所有外部系统 exactly once。

---

## 为什么手动 Commit

本 Lab 设置：

~~~text
enable.auto.commit=false
~~~

成功处理或成功移交 Retry Topic 后显式提交：

~~~text
record.offset + 1
~~~

这样可以明确观察：

~~~text
processing boundary
vs
offset boundary
~~~

而不是让 Auto Commit 掩盖确认时机。

---

## Retry Headers

Retry Topic 增加：

~~~text
retry-attempt
original-topic
original-offset
~~~

DLQ 再增加：

~~~text
final-attempt
error
~~~

生产还可以保存 original partition、first failure timestamp、correlation ID、trace ID、exception class 和 schema version，但不要把敏感 credential 塞进 Header。

---

## Ordering 的真正边界

Kafka 保证的是 Partition 内 Record 顺序。

应用层是否仍保持业务处理顺序，还受到：

- retry strategy
- async processing
- multiple partitions
- key selection
- concurrency model

影响。

如果一个业务实体必须严格有序，首先应确保 same business key -> same Kafka key -> same Partition，然后还要确保应用层没有通过 Retry Topic 或异步执行主动打乱顺序。

本 Lab 故意用同一个 order-1 key，但仍得到：

~~~text
original order:
event-1, event-2, event-3

completion order:
event-1, event-3, event-2
~~~

这就是 Retry Topic 的核心 ordering trade-off。

---

## Partition 数量与消费能力

Consumer Group 中，一个 Partition 同一时间只会由一个 group member 消费。

因此：

~~~text
1 partition
+
20 consumer instances
~~~

不等于 20 倍并行度。

扩容 Consumer 前要先确认 Partition 数量是否足够。

但 Partition 也不是越多越好，它还会增加 broker metadata、open files、replication work 和 rebalance complexity。

---

## Lag 排查 Checklist

看到 Lag 增长，可以依次检查：

1. Producer rate 是否突然增长？
2. Consumer rate 是否下降？
3. 哪个 Partition lag 最大？
4. 是否出现 hot partition？
5. processing latency 是否上升？
6. DB / RPC 下游是否变慢？
7. retry rate 是否突然增多？
8. poison message 是否阻塞？
9. 是否频繁 rebalance？
10. max.poll.interval.ms 是否超时？
11. GC / CPU 是否异常？
12. Consumer 数量与 Partition 数是否匹配？
13. 是否有异常大消息？
14. offset commit 是否正常？
15. oldest message age 是否违反 SLA？

---

## 生产监控

至少建议观察：

~~~text
records produced / sec
records consumed / sec
consumer lag by partition
max consumer lag
oldest unprocessed message age
processing latency
time between poll
rebalance count
commit failures
retry rate
retry-topic lag
DLQ rate
poison-message count
downstream error rate
~~~

Kafka Consumer 也暴露 time-between-poll、last-poll-seconds-ago 等指标帮助观察 poll 健康度。

---

## 用 CLI 查看 Group Lag

程序结束时会打印 lag topic 和 lag group。

可以执行：

~~~bash
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh   --bootstrap-server localhost:9092   --describe   --group <printed-lag-group>
~~~

常见字段：

~~~text
CURRENT-OFFSET
LOG-END-OFFSET
LAG
~~~

与程序通过 Admin API 观察的是同一个核心模型。

---

## 当前验证边界

当前执行环境有 JDK 21，但没有 Maven 或 Docker，因此无法在这里启动 Kafka 4.3.1 做完整 runtime 验证。

已按 Kafka 4.3.1 官方 API / 文档核对：

- Producer / Consumer / Admin API
- Consumer Group offset API
- latest partition offset API
- manual commit 模型
- max.poll.interval.ms 语义
- Consumer monitoring metrics

代码使用 Java 17 compiler baseline。

在有 Docker + Maven 的机器上执行：

~~~bash
bash scripts/run.sh
~~~

即可运行实际 Broker 场景。

---

## 生产与面试问题

1. Consumer Lag 怎么计算？
2. Consumer Position 和 Committed Offset 有什么区别？
3. 为什么 Lag trend 比一次瞬时 Lag 更重要？
4. 为什么还要监控 message age？
5. max.poll.interval.ms 解决什么问题？
6. max.poll.records 为什么会影响慢处理？
7. Auto Commit 和 Manual Commit 各有什么风险？
8. 为什么通常 commit offset + 1？
9. Blocking Retry 的优缺点是什么？
10. Retry Topic 的优缺点是什么？
11. Retry Topic 为什么可能破坏顺序？
12. Poison Message 为什么不能无限重试？
13. DLQ 应保存哪些诊断信息？
14. retry publish 成功、offset commit 失败会怎样？
15. offset commit 成功、retry publish 失败会怎样？
16. Kafka Transaction 能解决哪些 Kafka-to-Kafka 原子性问题？
17. 为什么 Kafka Exactly-Once 不自动等于外部 DB / HTTP exactly-once？
18. Consumer 数量超过 Partition 数会怎样？
19. 如何判断 producer spike 还是 consumer slowdown 导致 Lag？
20. Retry Topic Lag 本身为什么也必须监控？

## 下一步

Phase 4 下一步进入 gRPC Timeout & Retry，重点观察 client deadline、server processing time、DEADLINE_EXCEEDED、cancellation propagation、retryable status、per-attempt timeout、deadline budget、idempotency 与 retry amplification。

---

## English

### Goal

This lab uses the native Kafka Producer, Consumer, and Admin APIs to make consumer lag, manual commits, retry topics, poison messages, DLQs, and ordering trade-offs observable.

### Lag

For the single-partition teaching scenario:

~~~text
lag = log end offset - committed offset
~~~

The deterministic sequence is:

~~~text
produce 30       -> lag 30
consume 5        -> lag 25
produce 20 more  -> lag 45
catch up         -> lag 0
~~~

In production, lag trend and oldest-message age are often more meaningful than one absolute lag value.

### Retry

A transiently failing record is published to a retry topic and the source offset is committed. This frees the main Partition but permits later records to complete first.

The Lab intentionally proves:

~~~text
original order:
event-1, event-2, event-3

completion order:
event-1, event-3, event-2
~~~

### DLQ

A poison record fails every attempt. After three attempts it moves to a DLQ with origin and failure metadata.

A DLQ requires alerting, ownership, diagnosis, and replay rules; it is not a substitute for failure handling.

### Reliability

Retry publish and source offset commit are separate operations in this teaching implementation. A crash between them can create duplicates or loss depending on ordering. Idempotency remains important, while Kafka transactions can help when the workflow stays inside Kafka.

### Engineering Takeaway

The design question is not simply whether to retry. It is where retry happens, what it blocks, what ordering is required, when offsets commit, and what happens after retries are exhausted.
