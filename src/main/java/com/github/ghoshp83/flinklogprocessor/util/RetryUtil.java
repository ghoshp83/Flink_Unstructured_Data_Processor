/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class RetryUtil {
    private static final Logger LOG = LoggerFactory.getLogger(RetryUtil.class);
    
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_INITIAL_DELAY_MS = 1000;
    private static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    
    public static <T> T executeWithRetry(Supplier<T> operation, String operationName) {
        return executeWithRetry(operation, operationName, DEFAULT_MAX_RETRIES, 
                               DEFAULT_INITIAL_DELAY_MS, DEFAULT_BACKOFF_MULTIPLIER);
    }
    
    public static <T> T executeWithRetry(Supplier<T> operation, String operationName, 
                                         int maxRetries, long initialDelayMs, double backoffMultiplier) {
        int attempt = 0;
        long delay = initialDelayMs;
        
        while (attempt < maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    LOG.error("Operation {} failed after {} attempts", operationName, maxRetries, e);
                    throw new RuntimeException("Max retries exceeded for " + operationName, e);
                }
                
                LOG.warn("Operation {} failed (attempt {}/{}), retrying in {}ms", 
                        operationName, attempt, maxRetries, delay, e);
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
                
                delay = (long) (delay * backoffMultiplier);
            }
        }
        
        throw new RuntimeException("Unexpected retry loop exit");
    }
}
