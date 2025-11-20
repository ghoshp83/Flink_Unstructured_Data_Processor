/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.health;

import lombok.extern.slf4j.Slf4j;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class MetricsDashboard implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> gauges = new ConcurrentHashMap<>();
    private final AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
    
    public void incrementCounter(String name) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementCounter(String name, long value) {
        counters.computeIfAbsent(name, k -> new AtomicLong(0)).addAndGet(value);
    }
    
    public void setGauge(String name, long value) {
        gauges.computeIfAbsent(name, k -> new AtomicLong(0)).set(value);
    }
    
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Add counters
        Map<String, Long> counterValues = new HashMap<>();
        counters.forEach((k, v) -> counterValues.put(k, v.get()));
        metrics.put("counters", counterValues);
        
        // Add gauges
        Map<String, Long> gaugeValues = new HashMap<>();
        gauges.forEach((k, v) -> gaugeValues.put(k, v.get()));
        metrics.put("gauges", gaugeValues);
        
        // Add uptime
        long uptime = System.currentTimeMillis() - startTime.get();
        metrics.put("uptimeMs", uptime);
        metrics.put("uptimeSeconds", uptime / 1000);
        
        return metrics;
    }
    
    public void logMetrics() {
        Map<String, Object> metrics = getAllMetrics();
        log.info("=== Metrics Dashboard ===");
        log.info("Uptime: {} seconds", metrics.get("uptimeSeconds"));
        log.info("Counters: {}", metrics.get("counters"));
        log.info("Gauges: {}", metrics.get("gauges"));
    }
}
