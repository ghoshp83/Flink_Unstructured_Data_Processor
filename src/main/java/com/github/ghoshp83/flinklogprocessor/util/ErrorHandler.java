/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.util;

import com.github.ghoshp83.flinklogprocessor.exception.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Centralized error handling utility for the Flink log processor
 */
public class ErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);
    
    private static final ConcurrentHashMap<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> lastErrorTime = new ConcurrentHashMap<>();
    
    // Error rate limiting - max 10 errors per minute per operation
    private static final long ERROR_RATE_LIMIT_WINDOW_MS = 60000;
    private static final long MAX_ERRORS_PER_WINDOW = 10;
    
    /**
     * Execute operation with comprehensive error handling
     */
    public static <T> T executeWithErrorHandling(Supplier<T> operation, String operationName) {
        return executeWithErrorHandling(operation, operationName, null, true);
    }
    
    /**
     * Execute operation with error handling and optional fallback
     */
    public static <T> T executeWithErrorHandling(Supplier<T> operation, String operationName, 
                                                 Supplier<T> fallback, boolean logErrors) {
        try {
            return operation.get();
        } catch (Exception e) {
            if (logErrors && shouldLogError(operationName)) {
                LOG.error("Error in operation {}: {}", operationName, e.getMessage(), e);
            }
            if (fallback != null) {
                return fallback.get();
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Error in " + operationName, e);
        }
    }
    
    /**
     * Execute operation with error handling (void return)
     */
    public static void executeWithErrorHandling(Runnable operation, String operationName) {
        executeWithErrorHandling(() -> {
            operation.run();
            return null;
        }, operationName);
    }
    
    /**
     * Execute operation with error handling and custom error handler
     */
    public static <T> T executeWithErrorHandling(Supplier<T> operation, String operationName, 
                                                 Consumer<Exception> errorHandler) {
        try {
            return operation.get();
        } catch (Exception e) {
            if (shouldLogError(operationName)) {
                LOG.error("Error in operation {}: {}", operationName, e.getMessage(), e);
            }
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Error in " + operationName, e);
        }
    }
    
    /**
     * Check if error should be logged (rate limiting)
     */
    private static boolean shouldLogError(String operationName) {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old entries
        lastErrorTime.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > ERROR_RATE_LIMIT_WINDOW_MS);
        
        AtomicLong count = errorCounts.computeIfAbsent(operationName, k -> new AtomicLong(0));
        Long lastTime = lastErrorTime.get(operationName);
        
        if (lastTime == null || currentTime - lastTime > ERROR_RATE_LIMIT_WINDOW_MS) {
            // Reset counter for new window
            count.set(1);
            lastErrorTime.put(operationName, currentTime);
            return true;
        }
        
        long currentCount = count.incrementAndGet();
        if (currentCount <= MAX_ERRORS_PER_WINDOW) {
            return true;
        }
        
        // Rate limited
        if (currentCount == MAX_ERRORS_PER_WINDOW + 1) {
            LOG.warn("Error rate limit reached for operation: {}. Suppressing further error logs for this window.", 
                    operationName);
        }
        return false;
    }
    
    /**
     * Get error statistics for monitoring
     */
    public static long getErrorCount(String operationName) {
        AtomicLong count = errorCounts.get(operationName);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Reset error statistics
     */
    public static void resetErrorStats() {
        errorCounts.clear();
        lastErrorTime.clear();
    }
    
    /**
     * Check if operation is retryable based on exception type
     */
    public static boolean isRetryable(Exception e) {
        return e instanceof RetryableException ||
               e.getCause() instanceof java.net.SocketTimeoutException ||
               e.getCause() instanceof java.net.ConnectException ||
               e.getMessage() != null && (
                   e.getMessage().contains("timeout") ||
                   e.getMessage().contains("connection") ||
                   e.getMessage().contains("network")
               );
    }
}