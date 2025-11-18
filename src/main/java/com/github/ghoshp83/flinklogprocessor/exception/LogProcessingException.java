/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.exception;

public class LogProcessingException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public LogProcessingException(String message) {
        super(message);
    }
    
    public LogProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
