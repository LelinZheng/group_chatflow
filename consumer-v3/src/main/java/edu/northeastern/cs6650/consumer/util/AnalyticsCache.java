package edu.northeastern.cs6650.consumer.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class AnalyticsCache {
  private final long ttlMs;
  private final Map<String, Entry> cache = new ConcurrentHashMap<>();

  public AnalyticsCache(long ttlMs) {
    this.ttlMs = ttlMs;
  }

  public <T> T get(String key, Supplier<T> loader) {
    long now = System.currentTimeMillis();
    Entry entry = cache.get(key);
    if (entry != null && now - entry.createdAtMs <= ttlMs) {
      @SuppressWarnings("unchecked")
      T value = (T) entry.value;
      return value;
    }
    T value = loader.get();
    cache.put(key, new Entry(value, now));
    return value;
  }

  private static class Entry {
    private final Object value;
    private final long createdAtMs;

    private Entry(Object value, long createdAtMs) {
      this.value = value;
      this.createdAtMs = createdAtMs;
    }
  }
}
