package dev.xbhou.javalab.redis.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CacheAsideService {

    private static final String NULL_MARKER = "__NULL__";

    private final RedisCache cache;
    private final FakeDatabase database;
    private final ExecutorService refreshExecutor;

    public CacheAsideService(
            RedisCache cache,
            FakeDatabase database,
            ExecutorService refreshExecutor
    ) {
        this.cache = cache;
        this.database = database;
        this.refreshExecutor = refreshExecutor;
    }

    public Optional<String> getWithoutNegativeCache(
            String key
    ) {
        String cached = cache.get(dataKey(key));

        if (cached != null) {
            return Optional.of(cached);
        }

        String value = database.read(key);

        if (value != null) {
            cache.set(
                    dataKey(key),
                    value,
                    Duration.ofSeconds(5)
            );
        }

        return Optional.ofNullable(value);
    }

    public Optional<String> getWithNegativeCache(
            String key
    ) {
        String cached = cache.get(dataKey(key));

        if (NULL_MARKER.equals(cached)) {
            return Optional.empty();
        }

        if (cached != null) {
            return Optional.of(cached);
        }

        String value = database.read(key);

        if (value == null) {
            cache.set(
                    dataKey(key),
                    NULL_MARKER,
                    Duration.ofSeconds(2)
            );

            return Optional.empty();
        }

        cache.set(
                dataKey(key),
                value,
                Duration.ofSeconds(5)
        );

        return Optional.of(value);
    }

    public Optional<String> getHotKeyNaive(
            String key
    ) {
        String cached = cache.get(dataKey(key));

        if (cached != null) {
            return Optional.of(cached);
        }

        String value = database.read(key);

        if (value != null) {
            cache.set(
                    dataKey(key),
                    value,
                    Duration.ofSeconds(5)
            );
        }

        return Optional.ofNullable(value);
    }

    public Optional<String> getHotKeySingleFlight(
            String key
    ) {
        String cacheKey = dataKey(key);
        String cached = cache.get(cacheKey);

        if (cached != null) {
            return Optional.of(cached);
        }

        String lockKey = "lock:rebuild:" + key;
        String token = UUID.randomUUID().toString();

        boolean acquired = cache.tryLock(
                lockKey,
                token,
                Duration.ofSeconds(2)
        );

        if (acquired) {
            try {
                String secondRead = cache.get(cacheKey);

                if (secondRead != null) {
                    return Optional.of(secondRead);
                }

                String value = database.read(key);

                if (value != null) {
                    cache.set(
                            cacheKey,
                            value,
                            Duration.ofSeconds(5)
                    );
                }

                return Optional.ofNullable(value);
            } finally {
                cache.releaseLock(lockKey, token);
            }
        }

        for (int i = 0; i < 50; i++) {
            sleep(20);

            String value = cache.get(cacheKey);

            if (value != null) {
                return Optional.of(value);
            }
        }

        return Optional.ofNullable(
                database.read(key)
        );
    }

    public long putWithDeterministicJitter(
            String key,
            String value,
            long baseTtlMillis,
            long jitterMillis
    ) {
        long offsetRange =
                jitterMillis * 2 + 1;

        long offset =
                Math.floorMod(
                        key.hashCode(),
                        offsetRange
                ) - jitterMillis;

        long ttlMillis =
                Math.max(
                        1,
                        baseTtlMillis + offset
                );

        cache.set(
                dataKey(key),
                value,
                Duration.ofMillis(ttlMillis)
        );

        return ttlMillis;
    }

    public void putLogical(
            String key,
            String value,
            Duration freshFor
    ) {
        long expireAt =
                System.currentTimeMillis()
                        + freshFor.toMillis();

        String payload =
                expireAt + "|" + value;

        cache.set(
                logicalKey(key),
                payload,
                Duration.ofSeconds(30)
        );
    }

    public Optional<String> getLogical(
            String key,
            Duration freshFor
    ) {
        String payload =
                cache.get(logicalKey(key));

        if (payload == null) {
            String value = database.read(key);

            if (value == null) {
                return Optional.empty();
            }

            putLogical(
                    key,
                    value,
                    freshFor
            );

            return Optional.of(value);
        }

        LogicalValue logical =
                LogicalValue.parse(payload);

        if (!logical.expired()) {
            return Optional.of(logical.value());
        }

        tryStartAsyncRefresh(
                key,
                freshFor
        );

        return Optional.of(logical.value());
    }

    public void updateDbThenDeleteCache(
            String key,
            String value
    ) {
        database.write(key, value);
        cache.delete(dataKey(key));
    }

    public void deleteDataCache(String key) {
        cache.delete(dataKey(key));
    }

    public long pttl(String key) {
        return cache.pttl(dataKey(key));
    }

    private void tryStartAsyncRefresh(
            String key,
            Duration freshFor
    ) {
        String lockKey =
                "lock:logical-refresh:" + key;

        String token =
                UUID.randomUUID().toString();

        if (!cache.tryLock(
                lockKey,
                token,
                Duration.ofSeconds(2)
        )) {
            return;
        }

        refreshExecutor.submit(
                () -> {
                    try {
                        String latest =
                                database.read(key);

                        if (latest != null) {
                            putLogical(
                                    key,
                                    latest,
                                    freshFor
                            );
                        }
                    } finally {
                        cache.releaseLock(
                                lockKey,
                                token
                        );
                    }
                }
        );
    }

    private static String dataKey(String key) {
        return "cache:data:" + key;
    }

    private static String logicalKey(String key) {
        return "cache:logical:" + key;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "wait interrupted",
                    error
            );
        }
    }

    private record LogicalValue(
            long expireAtMillis,
            String value
    ) {
        private static LogicalValue parse(
                String payload
        ) {
            int separator =
                    payload.indexOf('|');

            if (separator < 0) {
                throw new IllegalStateException(
                        "invalid logical cache payload"
                );
            }

            long expireAt =
                    Long.parseLong(
                            payload.substring(
                                    0,
                                    separator
                            )
                    );

            return new LogicalValue(
                    expireAt,
                    payload.substring(
                            separator + 1
                    )
            );
        }

        private boolean expired() {
            return System.currentTimeMillis()
                    >= expireAtMillis;
        }
    }
}
