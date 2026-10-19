# Kafka

## 中文

Kafka 实验目录，重点通过可运行场景理解 Producer / Consumer、Offset、Lag、Retry、Ordering 和失败恢复。

### Labs

#### Consumer Lag 与重试 / Consumer Lag & Retry

路径：`consumer-lag-retry/`

重点：

- Producer Rate vs Consumer Rate
- Log End Offset / Committed Offset
- Consumer Lag
- Manual Offset Commit
- Slow Consumer / max.poll.interval.ms
- Retry Topic
- Exponential Backoff
- Poison Message
- Dead-letter Topic
- Retry Headers
- Ordering Trade-off
- Kafka-to-Kafka 原子性边界

实验使用 Apache Kafka 4.3.1、原生 kafka-clients 和 Java 17 baseline。

后续可继续扩展：

- Consumer Group Rebalance
- Partition Assignment
- Exactly-once / Transactions
- Cooperative Rebalance
- Hot Partition
- Schema Evolution

---

## English

Kafka experiments focused on observable producer/consumer behavior, offsets, lag, retries, ordering, and failure recovery.

### Labs

#### Consumer Lag & Retry / Consumer Lag 与重试

Path: `consumer-lag-retry/`

Focus:

- producer rate vs consumer rate
- log end offset / committed offset
- consumer lag
- manual offset commits
- slow consumers / max.poll.interval.ms
- retry topics
- exponential backoff
- poison messages
- dead-letter topics
- retry metadata
- ordering trade-offs
- Kafka-to-Kafka atomicity boundaries

The lab uses Apache Kafka 4.3.1, native kafka-clients, and a Java 17 baseline.

Possible future extensions:

- consumer-group rebalancing
- partition assignment
- exactly-once / transactions
- cooperative rebalancing
- hot partitions
- schema evolution
