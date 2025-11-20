/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.health;

import lombok.extern.slf4j.Slf4j;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class HealthCheckService implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final AtomicLong recordsProcessed = new AtomicLong(0);
    private final AtomicLong recordsFailed = new AtomicLong(0);
    private final AtomicLong lastProcessedTimestamp = new AtomicLong(System.currentTimeMillis());
    private volatile boolean isHealthy = true;
    private volatile String lastError = null;
    
    public void recordSuccess() {
        recordsProcessed.incrementAndGet();
        lastProcessedTimestamp.set(System.currentTimeMillis());
        isHealthy = true;
        lastError = null;
    }
    
    public void recordFailure(String error) {
        recordsFailed.incrementAndGet();
        lastError = error;
        
        long total = recordsProcessed.get() + recordsFailed.get();
        if (total > 100 && recordsFailed.get() > total / 2) {
            isHealthy = false;
        }
    }
    
    public Map<String, Object> getHealthMetrics() {
        long timeSinceLastProcess = System.currentTimeMillis() - lastProcessedTimestamp.get();
        boolean isStale = timeSinceLastProcess > 300000;
        
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("status", (isHealthy && !isStale) ? "UP" : "DOWN");
        metrics.put("recordsProcessed", recordsProcessed.get());
        metrics.put("recordsFailed", recordsFailed.get());
        metrics.put("successRate", calculateSuccessRate());
        metrics.put("timeSinceLastProcess", timeSinceLastProcess);
        metrics.put("lastError", lastError);
        return metrics;
    }
    
    private double calculateSuccessRate() {
        long total = recordsProcessed.get() + recordsFailed.get();
        if (total == 0) return 100.0;
        return (recordsProcessed.get() * 100.0) / total;
    }
}
