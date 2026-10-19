package dev.xbhou.javalab.redis.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeDatabase {

    private final Map<String, String> data =
            new ConcurrentHashMap<>();

    private final AtomicInteger readCount =
            new AtomicInteger();

    private final long readDelayMillis;

    public FakeDatabase(long readDelayMillis) {
        this.readDelayMillis = readDelayMillis;
    }

    public String read(String key) {
        readCount.incrementAndGet();

        sleep(readDelayMillis);

        return data.get(key);
    }

    public void write(String key, String value) {
        data.put(key, value);
    }

    public int readCount() {
        return readCount.get();
    }

    public void resetReadCount() {
        readCount.set(0);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "database read interrupted",
                    error
            );
        }
    }
}
