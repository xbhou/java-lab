package dev.xbhou.javalab.redis.cache;

import java.time.Duration;
import java.util.List;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

public class RedisCache {

    private static final String RELEASE_LOCK_SCRIPT =
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private final JedisPooled jedis;

    public RedisCache(JedisPooled jedis) {
        this.jedis = jedis;
    }

    public String get(String key) {
        return jedis.get(key);
    }

    public void set(
            String key,
            String value,
            Duration ttl
    ) {
        jedis.set(
                key,
                value,
                SetParams.setParams()
                        .px(ttl.toMillis())
        );
    }

    public void delete(String key) {
        jedis.del(key);
    }

    public long pttl(String key) {
        return jedis.pttl(key);
    }

    public boolean tryLock(
            String lockKey,
            String token,
            Duration ttl
    ) {
        String result = jedis.set(
                lockKey,
                token,
                SetParams.setParams()
                        .nx()
                        .px(ttl.toMillis())
        );

        return "OK".equals(result);
    }

    public void releaseLock(
            String lockKey,
            String token
    ) {
        jedis.eval(
                RELEASE_LOCK_SCRIPT,
                List.of(lockKey),
                List.of(token)
        );
    }

    public void flushAll() {
        jedis.flushDB();
    }
}
