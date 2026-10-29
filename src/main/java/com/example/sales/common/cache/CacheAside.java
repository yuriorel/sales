package com.example.sales.common.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/// Cache-aside over Redis: read the cache, on a miss load from the source of truth
/// and store the JSON with a TTL. Writers update the database first, then [#evict].
///
/// Redis is an optimisation only: any Redis failure is logged and the loader result
/// is returned, so the API keeps working when Redis is down.
@Component
public class CacheAside {

    private static final Logger log = LoggerFactory.getLogger(CacheAside.class);

    private final StringRedisTemplate redis;
    private final JsonMapper json;

    public CacheAside(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public <T> T get(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, json.constructType(type), ttl, loader);
    }

    public <T> T get(String key, TypeReference<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, json.constructType(type), ttl, loader);
    }

    public void evict(String... keys) {
        try {
            var deleted = redis.delete(List.of(keys));
            log.debug("Cache evict keys={} deleted={}", keys, deleted);
        } catch (DataAccessException e) {
            log.warn("Cache evict failed for keys={}", keys, e);
        }
    }

    private <T> T get(String key, JavaType type, Duration ttl, Supplier<T> loader) {
        var cached = read(key);
        if (cached != null) {
            log.debug("Cache hit key={}", key);
            return json.readValue(cached, type);
        }
        log.debug("Cache miss key={}, loading", key);
        var value = loader.get();
        if (value != null) {
            write(key, json.writeValueAsString(value), ttl);
        }
        return value;
    }

    private String read(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("Cache read failed for key={}, falling back to loader", key, e);
            return null;
        }
    }

    private void write(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
            log.debug("Cache store key={} ttl={} value={}", key, ttl, value);
        } catch (DataAccessException e) {
            log.warn("Cache write failed for key={}", key, e);
        }
    }
}
