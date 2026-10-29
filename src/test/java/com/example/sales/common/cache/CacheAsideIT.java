package com.example.sales.common.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/// Cache-aside against the local Redis (test database index 1).
@SpringBootTest
@ActiveProfiles("test")
class CacheAsideIT {

    record Item(long id, String name) {
    }

    @Autowired
    CacheAside cache;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void loadsOnceThenServesFromRedisUntilEvicted() {
        var key = "it:item:" + System.nanoTime();
        var loads = new AtomicInteger();

        var first = cache.get(key, Item.class, Duration.ofMinutes(1), () -> new Item(loads.incrementAndGet(), "db"));
        var second = cache.get(key, Item.class, Duration.ofMinutes(1), () -> new Item(loads.incrementAndGet(), "db"));

        assertThat(first).isEqualTo(second).isEqualTo(new Item(1, "db"));
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);

        cache.evict(key);
        var third = cache.get(key, Item.class, Duration.ofMinutes(1), () -> new Item(loads.incrementAndGet(), "db"));

        assertThat(third).isEqualTo(new Item(2, "db"));
        cache.evict(key);
    }
}
