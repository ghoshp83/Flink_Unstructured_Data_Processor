/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.config;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Security configuration and validation utilities
 */
public class SecurityConfig {
    
    // Security constants
    public static final int MAX_LOG_LINE_LENGTH = 10000;
    public static final int MAX_LOG_TYPE_LENGTH = 50;
    public static final int MAX_PATTERN_LENGTH = 1000;
    public static final int MAX_S3_PATH_LENGTH = 500;
    
    // Validation patterns
    private static final Pattern LOG_TYPE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern GROK_PATTERN_VALIDATOR = Pattern.compile("^[%{}:A-Za-z0-9_\\s-]+$");
    private static final Pattern S3_PATH_PATTERN = Pattern.compile("^s3a?://[a-zA-Z0-9._${}-]+/.*$");
    
    /**
     * Validates S3 path for security
     */
    public static String validateS3Path(String s3Path) {
        if (s3Path == null || s3Path.length() > MAX_S3_PATH_LENGTH) {
            throw new IllegalArgumentException("Invalid S3 path length");
        }
        
        if (!S3_PATH_PATTERN.matcher(s3Path).matches()) {
            throw new IllegalArgumentException("Invalid S3 path format: " + s3Path);
        }
        
        return s3Path;
    }
    
    /**
     * Validates log type for security
     */
    public static String validateLogType(String logType) {
        if (logType == null || logType.length() > MAX_LOG_TYPE_LENGTH) {
            throw new IllegalArgumentException("Invalid log type length");
        }
        
        if (!LOG_TYPE_PATTERN.matcher(logType).matches()) {
            throw new IllegalArgumentException("Invalid log type format: " + logType);
        }
        
        return logType;
    }
    
    /**
     * Validates Grok pattern for security
     */
    public static String validateLogPattern(String pattern) {
        if (pattern == null || pattern.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Invalid log pattern length");
        }
        
        if (!GROK_PATTERN_VALIDATOR.matcher(pattern).matches()) {
            throw new IllegalArgumentException("Invalid characters in log pattern");
        }
        
        return pattern;
    }
    
    /**
     * Validates log line length
     */
    public static boolean isValidLogLine(String logLine) {
        return logLine != null && logLine.length() <= MAX_LOG_LINE_LENGTH;
    }
    
    /**
     * Sanitizes configuration properties by resolving environment variables
     */
    public static Properties sanitizeProperties(Properties properties) {
        Properties sanitized = new Properties();
        
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            if (value != null) {
                // Resolve environment variables
                value = resolveEnvironmentVariables(value);
                sanitized.setProperty(key, value);
            }
        }
        
        return sanitized;
    }
    
    /**
     * Resolves environment variables in configuration values
     */
    private static String resolveEnvironmentVariables(String value) {
        if (value.contains("${")) {
            // Replace ${VAR_NAME} with environment variable values
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\$\\{([^}]+)\\}");
            java.util.regex.Matcher matcher = pattern.matcher(value);
            StringBuffer result = new StringBuffer();
            
            while (matcher.find()) {
                String varName = matcher.group(1);
                String envValue = System.getenv(varName);
                String replacement = envValue != null ? envValue : getDefaultValue(varName);
                matcher.appendReplacement(result, replacement);
            }
            matcher.appendTail(result);
            return result.toString();
        }
        return value;
    }
    
    /**
     * Provides default values for environment variables
     */
    private static String getDefaultValue(String varName) {
        switch (varName) {
            case "ORGANIZATION_PREFIX":
                return "pralaydata";
            case "ENVIRONMENT":
                return "dev";
            default:
                return varName.toLowerCase();
        }
    }
}