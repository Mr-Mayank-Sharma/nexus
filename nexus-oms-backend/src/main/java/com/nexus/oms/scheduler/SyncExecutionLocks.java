package com.nexus.oms.scheduler;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-JVM mutex that serializes sync executions per store+syncType.
 *
 * Prevents the scheduler and manually-triggered sync endpoints (or overlapping
 * scheduled runs) from importing/pushing for the same store concurrently,
 * which previously caused interleaved API calls and watermark clobbering.
 */
@Component
public class SyncExecutionLocks {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public static String key(java.util.UUID storeId, String syncType) {
        return storeId + ":" + syncType;
    }

    /** Try to acquire the execution slot; false if a sync is already running for this key. */
    public boolean tryAcquire(String key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock()).tryLock();
    }

    /** Release the execution slot acquired via tryAcquire. Safe to call once after acquire. */
    public void release(String key) {
        ReentrantLock lock = locks.get(key);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
