/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.exception;

public class RetryableException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    public RetryableException(String message) {
        super(message);
    }
    
    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
