package com.example.sales.common.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheAsideTest {

    static final Duration TTL = Duration.ofMinutes(5);

    record Item(long id, String name) {
    }

    final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    final ValueOperations<String, String> values = mock(ValueOperations.class);
    final CacheAside cache = new CacheAside(redis, JsonMapper.builder().build());
    final AtomicInteger loads = new AtomicInteger();

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void hitReturnsCachedValueWithoutLoading() {
        when(values.get("item:1")).thenReturn("""
                {"id":1,"name":"cached"}""");

        var item = cache.get("item:1", Item.class, TTL, () -> load(new Item(1, "db")));

        assertThat(item).isEqualTo(new Item(1, "cached"));
        assertThat(loads).hasValue(0);
    }

    @Test
    void missLoadsAndStoresWithTtl() {
        var item = cache.get("item:1", Item.class, TTL, () -> load(new Item(1, "db")));

        assertThat(item).isEqualTo(new Item(1, "db"));
        assertThat(loads).hasValue(1);
        verify(values).set("item:1", """
                {"id":1,"name":"db"}""", TTL);
    }

    @Test
    void genericTypesRoundTrip() {
        when(values.get("items")).thenReturn("""
                [{"id":1,"name":"a"},{"id":2,"name":"b"}]""");

        var items = cache.get("items", new TypeReference<List<Item>>() { }, TTL, List::of);

        assertThat(items).containsExactly(new Item(1, "a"), new Item(2, "b"));
    }

    @Test
    void evictDeletesKeys() {
        cache.evict("item:1", "items");

        verify(redis).delete(List.of("item:1", "items"));
    }

    @Test
    void redisDownFallsThroughToLoader() {
        var down = new RedisConnectionFailureException("down");
        when(values.get(anyString())).thenThrow(down);
        doThrow(down).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(ArgumentMatchers.<List<String>>any())).thenThrow(down);

        var item = cache.get("item:1", Item.class, TTL, () -> load(new Item(1, "db")));
        cache.evict("item:1");

        assertThat(item).isEqualTo(new Item(1, "db"));
        assertThat(loads).hasValue(1);
    }

    @Test
    void nullLoaderResultIsNotCached() {
        var item = cache.get("item:9", Item.class, TTL, () -> null);

        assertThat(item).isNull();
        verify(values, never())
                .set(anyString(), anyString(), any(Duration.class));
    }

    private Item load(Item item) {
        loads.incrementAndGet();
        return item;
    }
}
