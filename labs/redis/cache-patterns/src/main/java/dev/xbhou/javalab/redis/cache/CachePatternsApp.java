package dev.xbhou.javalab.redis.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import redis.clients.jedis.JedisPooled;

public class CachePatternsApp {

    private static final int CONCURRENT_READERS = 20;

    public static void main(String[] args) throws Exception {
        JedisPooled jedis =
                new JedisPooled(
                        "localhost",
                        6380
                );

        RedisCache cache =
                new RedisCache(jedis);

        FakeDatabase database =
                new FakeDatabase(120);

        ExecutorService refreshExecutor =
                Executors.newFixedThreadPool(2);

        CacheAsideService service =
                new CacheAsideService(
                        cache,
                        database,
                        refreshExecutor
                );

        try {
            cache.flushAll();

            cacheAsideScenario(
                    service,
                    database
            );

            penetrationScenario(
                    service,
                    database
            );

            hotKeyBreakdownScenario(
                    service,
                    database
            );

            avalancheAndJitterScenario(
                    service
            );

            logicalExpirationScenario(
                    service,
                    database
            );

            consistencyScenario(
                    service,
                    database
            );

            System.out.println();
            System.out.println(
                    "All Redis cache pattern scenarios verified."
            );
        } finally {
            refreshExecutor.shutdownNow();
            jedis.close();
        }
    }

    private static void cacheAsideScenario(
            CacheAsideService service,
            FakeDatabase database
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 1: cache-aside hit / miss ==="
        );

        database.write("product:1", "version-1");
        database.resetReadCount();

        String first =
                service.getWithoutNegativeCache(
                        "product:1"
                ).orElseThrow();

        String second =
                service.getWithoutNegativeCache(
                        "product:1"
                ).orElseThrow();

        verifyEquals(
                "cache-aside first value",
                "version-1",
                first
        );

        verifyEquals(
                "cache-aside second value",
                "version-1",
                second
        );

        verifyEquals(
                "cache-aside DB reads",
                1,
                database.readCount()
        );

        System.out.println(
                "DB reads=" + database.readCount()
        );
    }

    private static void penetrationScenario(
            CacheAsideService service,
            FakeDatabase database
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 2: cache penetration ==="
        );

        String missingKey = "product:missing";

        service.deleteDataCache(missingKey);
        database.resetReadCount();

        for (int i = 0; i < 5; i++) {
            service.getWithoutNegativeCache(
                    missingKey
            );
        }

        int withoutNegativeCache =
                database.readCount();

        service.deleteDataCache(missingKey);
        database.resetReadCount();

        for (int i = 0; i < 5; i++) {
            service.getWithNegativeCache(
                    missingKey
            );
        }

        int withNegativeCache =
                database.readCount();

        verifyEquals(
                "penetration without negative cache",
                5,
                withoutNegativeCache
        );

        verifyEquals(
                "penetration with negative cache",
                1,
                withNegativeCache
        );

        System.out.println(
                "without negative cache DB reads="
                        + withoutNegativeCache
        );

        System.out.println(
                "with negative cache DB reads="
                        + withNegativeCache
        );
    }

    private static void hotKeyBreakdownScenario(
            CacheAsideService service,
            FakeDatabase database
    ) throws Exception {
        System.out.println();
        System.out.println(
                "=== Scenario 3: hot-key breakdown ==="
        );

        String key = "product:hot";
        database.write(key, "hot-value");

        service.deleteDataCache(key);
        database.resetReadCount();

        runConcurrentReads(
                () -> service.getHotKeyNaive(key)
        );

        int naiveReads =
                database.readCount();

        service.deleteDataCache(key);
        database.resetReadCount();

        runConcurrentReads(
                () -> service.getHotKeySingleFlight(
                        key
                )
        );

        int singleFlightReads =
                database.readCount();

        if (naiveReads <= 1) {
            throw new IllegalStateException(
                    "naive hot-key scenario should cause duplicate DB reads"
            );
        }

        verifyEquals(
                "single-flight DB reads",
                1,
                singleFlightReads
        );

        System.out.println(
                "naive DB reads=" + naiveReads
        );

        System.out.println(
                "single-flight DB reads="
                        + singleFlightReads
        );
    }

    private static void avalancheAndJitterScenario(
            CacheAsideService service
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 4: TTL jitter ==="
        );

        List<Long> fixedTtls =
                new ArrayList<>();

        List<Long> jitteredTtls =
                new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            String fixedKey =
                    "fixed:" + i;

            service.putWithDeterministicJitter(
                    fixedKey,
                    "value",
                    2000,
                    0
            );

            fixedTtls.add(
                    service.pttl(fixedKey)
            );

            String jitterKey =
                    "jitter:" + i;

            service.putWithDeterministicJitter(
                    jitterKey,
                    "value",
                    2000,
                    700
            );

            jitteredTtls.add(
                    service.pttl(jitterKey)
            );
        }

        long fixedSpread =
                spread(fixedTtls);

        long jitterSpread =
                spread(jitteredTtls);

        if (jitterSpread < 500) {
            throw new IllegalStateException(
                    "TTL jitter should spread expirations"
            );
        }

        System.out.println(
                "fixed TTL spread="
                        + fixedSpread
                        + " ms"
        );

        System.out.println(
                "jittered TTL spread="
                        + jitterSpread
                        + " ms"
        );
    }

    private static void logicalExpirationScenario(
            CacheAsideService service,
            FakeDatabase database
    ) throws Exception {
        System.out.println();
        System.out.println(
                "=== Scenario 5: logical expiration ==="
        );

        String key = "product:logical";

        database.write(key, "version-1");

        service.putLogical(
                key,
                "version-1",
                Duration.ofMillis(100)
        );

        Thread.sleep(150);

        database.write(key, "version-2");
        database.resetReadCount();

        long start = System.nanoTime();

        String stale =
                service.getLogical(
                        key,
                        Duration.ofSeconds(2)
                ).orElseThrow();

        long elapsedMillis =
                (System.nanoTime() - start)
                        / 1_000_000;

        verifyEquals(
                "logical expiration returns stale immediately",
                "version-1",
                stale
        );

        if (elapsedMillis >= 100) {
            throw new IllegalStateException(
                    "logical expiration should not block on DB refresh"
            );
        }

        Thread.sleep(250);

        String refreshed =
                service.getLogical(
                        key,
                        Duration.ofSeconds(2)
                ).orElseThrow();

        verifyEquals(
                "logical expiration async refresh",
                "version-2",
                refreshed
        );

        verifyEquals(
                "logical expiration DB reads",
                1,
                database.readCount()
        );

        System.out.println(
                "stale response latency="
                        + elapsedMillis
                        + " ms"
        );
    }

    private static void consistencyScenario(
            CacheAsideService service,
            FakeDatabase database
    ) {
        System.out.println();
        System.out.println(
                "=== Scenario 6: DB / cache consistency ==="
        );

        String key = "product:consistency";

        database.write(key, "version-1");
        service.deleteDataCache(key);

        String first =
                service.getWithoutNegativeCache(
                        key
                ).orElseThrow();

        verifyEquals(
                "initial cached value",
                "version-1",
                first
        );

        database.write(key, "version-2");

        String stale =
                service.getWithoutNegativeCache(
                        key
                ).orElseThrow();

        verifyEquals(
                "stale cache when DB changes without invalidation",
                "version-1",
                stale
        );

        service.updateDbThenDeleteCache(
                key,
                "version-3"
        );

        String latest =
                service.getWithoutNegativeCache(
                        key
                ).orElseThrow();

        verifyEquals(
                "DB update then cache delete",
                "version-3",
                latest
        );

        System.out.println(
                "stale without invalidation="
                        + stale
        );

        System.out.println(
                "after update + delete="
                        + latest
        );
    }

    private static void runConcurrentReads(
            CacheRead action
    ) throws Exception {
        ExecutorService executor =
                Executors.newFixedThreadPool(
                        CONCURRENT_READERS
                );

        CountDownLatch ready =
                new CountDownLatch(
                        CONCURRENT_READERS
                );

        CountDownLatch start =
                new CountDownLatch(1);

        List<Future<Optional<String>>> futures =
                new ArrayList<>();

        try {
            for (int i = 0;
                 i < CONCURRENT_READERS;
                 i++) {

                futures.add(
                        executor.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return action.read();
                                }
                        )
                );
            }

            ready.await();
            start.countDown();

            for (Future<Optional<String>> future
                    : futures) {
                future.get().orElseThrow();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static long spread(
            List<Long> values
    ) {
        long min =
                values.stream()
                        .mapToLong(Long::longValue)
                        .min()
                        .orElseThrow();

        long max =
                values.stream()
                        .mapToLong(Long::longValue)
                        .max()
                        .orElseThrow();

        return max - min;
    }

    private static void verifyEquals(
            String scenario,
            Object expected,
            Object actual
    ) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    scenario
                            + " expected="
                            + expected
                            + " actual="
                            + actual
            );
        }
    }

    @FunctionalInterface
    private interface CacheRead {
        Optional<String> read() throws Exception;
    }
}
