/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class CircuitBreaker {
    private static final Logger LOG = LoggerFactory.getLogger(CircuitBreaker.class);
    
    private enum State { CLOSED, OPEN, HALF_OPEN }
    
    private final int failureThreshold;
    private final long timeoutMs;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private volatile State state = State.CLOSED;
    
    public CircuitBreaker(int failureThreshold, long timeoutMs) {
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
    }
    
    public <T> T execute(Supplier<T> operation, String operationName) {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime.get() > timeoutMs) {
                LOG.info("Circuit breaker transitioning to HALF_OPEN for {}", operationName);
                state = State.HALF_OPEN;
            } else {
                throw new RuntimeException("Circuit breaker is OPEN for " + operationName);
            }
        }
        
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure(operationName);
            throw e;
        }
    }
    
    private void onSuccess() {
        failureCount.set(0);
        if (state == State.HALF_OPEN) {
            LOG.info("Circuit breaker transitioning to CLOSED");
            state = State.CLOSED;
        }
    }
    
    private void onFailure(String operationName) {
        int failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failures >= failureThreshold) {
            LOG.warn("Circuit breaker transitioning to OPEN for {} after {} failures", 
                    operationName, failures);
            state = State.OPEN;
        }
    }
    
    public State getState() {
        return state;
    }
}
