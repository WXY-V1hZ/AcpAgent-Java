package com.v1hz.acpagent.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class CancellationService {

    private final ConcurrentHashMap<String, Boolean> cancelled = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Thread> runningThreads = new ConcurrentHashMap<>();

    public void cancel(String sessionId) {
        cancelled.put(sessionId, true);
        Thread thread = runningThreads.remove(sessionId);
        if (thread != null) {
            thread.interrupt();
        }
    }

    public boolean isCancelled(String sessionId) {
        return cancelled.getOrDefault(sessionId, false);
    }

    public void registerThread(String sessionId) {
        runningThreads.put(sessionId, Thread.currentThread());
    }

    public void unregisterThread(String sessionId) {
        runningThreads.remove(sessionId);
    }

    public void clear(String sessionId) {
        cancelled.remove(sessionId);
        runningThreads.remove(sessionId);
    }
}
