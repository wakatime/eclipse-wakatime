package com.wakatime.eclipse.plugin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Debouncer<T> {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, Future<Object>> delayedMap = new ConcurrentHashMap<String, Future<Object>>();

    public void debounce(final String key, final Runnable runnable, long delay, TimeUnit unit) {
        final Future<Object> prev = delayedMap.put(key, (Future<Object>) scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } finally {
                    delayedMap.remove(key);
                }
            }
        }, delay, unit));
        if (prev != null) {
            prev.cancel(true);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}