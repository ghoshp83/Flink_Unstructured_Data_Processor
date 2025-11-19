/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.parser;

import io.krakens.grok.api.Grok;
import io.krakens.grok.api.GrokCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.util.CircuitBreaker;

public class GrokPatternParser implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(GrokPatternParser.class);
    
    // Security: Whitelist of allowed Grok pattern elements
    private static final Set<String> ALLOWED_PATTERN_ELEMENTS = new HashSet<>();
    static {
        ALLOWED_PATTERN_ELEMENTS.add("TIMESTAMP_ISO8601");
        ALLOWED_PATTERN_ELEMENTS.add("COMBINEDAPACHELOG");
        ALLOWED_PATTERN_ELEMENTS.add("SYSLOGBASE");
        ALLOWED_PATTERN_ELEMENTS.add("LOGLEVEL");
        ALLOWED_PATTERN_ELEMENTS.add("WORD");
        ALLOWED_PATTERN_ELEMENTS.add("GREEDYDATA");
        ALLOWED_PATTERN_ELEMENTS.add("INT");
        ALLOWED_PATTERN_ELEMENTS.add("IP");
        ALLOWED_PATTERN_ELEMENTS.add("HTTPDATE");
        ALLOWED_PATTERN_ELEMENTS.add("QS");
        ALLOWED_PATTERN_ELEMENTS.add("URI");
        ALLOWED_PATTERN_ELEMENTS.add("URIPATH");
        ALLOWED_PATTERN_ELEMENTS.add("URIPARAM");
    }
    
    // Security: Pattern to validate Grok patterns
    private static final Pattern GROK_PATTERN_VALIDATOR = Pattern.compile("^[%{}:A-Za-z0-9_\\s-]+$");

    private transient Map<String, Grok> genericGrokMap;
    private Map<String, String> genericPatterns;
    private transient CircuitBreaker parsingCircuitBreaker;
    private transient Map<String, Integer> patternFailureCount;
    
    // Performance: Cache for pattern keys to avoid string concatenation
    private transient Map<String, String> patternKeyCache;
    
    // Performance: Reusable StringBuilder for string operations
    private static final ThreadLocal<StringBuilder> STRING_BUILDER_CACHE = 
        ThreadLocal.withInitial(() -> new StringBuilder(256));

    public GrokPatternParser() {
        this(new java.util.HashMap<>());
    }
    
    public GrokPatternParser(Map<String, String> genericPatterns) {
        this.genericPatterns = genericPatterns;
        this.genericGrokMap = new ConcurrentHashMap<>();
        this.parsingCircuitBreaker = new CircuitBreaker(5, 60000); // 5 failures, 60s timeout
        this.patternFailureCount = new ConcurrentHashMap<>();
        this.patternKeyCache = new ConcurrentHashMap<>();
    }

    private void ensureGrokInitialized() {
        if (genericGrokMap == null) {
            genericGrokMap = new ConcurrentHashMap<>();
        }
        if (parsingCircuitBreaker == null) {
            parsingCircuitBreaker = new CircuitBreaker(5, 60000);
        }
        if (patternFailureCount == null) {
            patternFailureCount = new ConcurrentHashMap<>();
        }
        if (patternKeyCache == null) {
            patternKeyCache = new ConcurrentHashMap<>();
        }
    }
    
    public GenericLogRecord parseGenericRecord(String line, String logType, String pattern) {
        try {
            // Security: Validate inputs
            if (!isValidInput(line, logType, pattern)) {
                LOG.warn("Invalid input detected for security reasons - logType: {}", logType);
                return null;
            }
            
            ensureGrokInitialized();
            
            // Performance: Use cached pattern key to avoid string concatenation
            String patternKey = getPatternKey(logType, pattern);
            Integer failureCount = patternFailureCount.getOrDefault(patternKey, 0);
            if (failureCount > 10) {
                LOG.warn("Pattern {} has failed {} times, skipping", patternKey, failureCount);
                return null;
            }
            
            return parsingCircuitBreaker.execute(() -> {
                try {
                    Grok grok = genericGrokMap.get(logType);
                    if (grok == null) {
                        LOG.debug("Compiling new Grok pattern for logType: {}", logType);
                        GrokCompiler compiler = GrokCompiler.newInstance();
                        compiler.registerDefaultPatterns();
                        grok = compiler.compile(pattern);
                        genericGrokMap.put(logType, grok);
                        LOG.debug("Successfully compiled Grok pattern for logType: {}", logType);
                    }
                    
                    Map<String, Object> match = grok.match(line).capture();
                    if (match.isEmpty()) {
                        // Increment failure count for this pattern
                        patternFailureCount.put(patternKey, failureCount + 1);
                        // Performance: Optimize log message string operations
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Failed to parse line with pattern {} (failure count: {}): {}", 
                                     logType, failureCount + 1, truncateString(line, 100));
                        }
                        return null;
                    }
                    
                    // Reset failure count on success
                    patternFailureCount.remove(patternKey);
                    
                    GenericLogRecord record = new GenericLogRecord(line, logType);
                    for (Map.Entry<String, Object> entry : match.entrySet()) {
                        if (entry.getValue() != null) {
                            record.setField(entry.getKey(), entry.getValue());
                        }
                    }
                    
                    LOG.debug("Successfully parsed record for logType: {} with {} fields", logType, match.size());
                    return record;
                    
                } catch (Exception e) {
                    // Increment failure count for this pattern
                    patternFailureCount.put(patternKey, failureCount + 1);
                    LOG.error("Error in Grok parsing for logType {}: {}", logType, e.getMessage(), e);
                    throw new RuntimeException("Grok parsing failed for logType: " + logType, e);
                }
            }, "grok-parsing-" + logType);
            
        } catch (RuntimeException e) {
            LOG.error("Runtime error parsing generic record for logType {}: {}", logType, e.getMessage(), e);
            return null;
        } catch (Exception e) {
            LOG.error("Unexpected error parsing generic record for logType {}: {}", logType, e.getMessage(), e);
            return null;
        }
    }

    private String getString(Map<String, Object> match, String key) {
        Object value = match.get(key);
        return value != null ? value.toString() : "";
    }

    private int getInt(Map<String, Object> match, String key) {
        Object value = match.get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Performance: Get or create cached pattern key to avoid repeated string concatenation
     */
    private String getPatternKey(String logType, String pattern) {
        // Use computeIfAbsent for thread-safe caching
        return patternKeyCache.computeIfAbsent(logType + pattern.hashCode(), 
            k -> logType + ":" + pattern);
    }
    
    /**
     * Performance: Efficiently truncate strings using cached StringBuilder
     */
    private String truncateString(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        StringBuilder sb = STRING_BUILDER_CACHE.get();
        sb.setLength(0); // Clear previous content
        sb.append(str, 0, maxLength).append("...");
        return sb.toString();
    }
    
    /**
     * Security validation for inputs to prevent code injection
     */
    private boolean isValidInput(String line, String logType, String pattern) {
        // Validate line length to prevent DoS
        if (line == null || line.length() > 10000) {
            return false;
        }
        
        // Validate log type
        if (logType == null || !logType.matches("^[a-zA-Z0-9_-]+$") || logType.length() > 50) {
            return false;
        }
        
        // Validate Grok pattern
        if (pattern == null || pattern.length() > 1000) {
            return false;
        }
        
        // Check pattern format
        if (!GROK_PATTERN_VALIDATOR.matcher(pattern).matches()) {
            LOG.warn("Pattern contains invalid characters: {}", pattern);
            return false;
        }
        
        // Validate that pattern only uses allowed elements
        return validatePatternElements(pattern);
    }
    
    private boolean validatePatternElements(String pattern) {
        // Extract pattern elements like %{TIMESTAMP_ISO8601:timestamp}
        Pattern elementPattern = Pattern.compile("%\\{([A-Z0-9_]+)(?::[a-zA-Z0-9_]+)?\\}");
        java.util.regex.Matcher matcher = elementPattern.matcher(pattern);
        
        while (matcher.find()) {
            String element = matcher.group(1);
            if (!ALLOWED_PATTERN_ELEMENTS.contains(element)) {
                LOG.warn("Disallowed pattern element: {}", element);
                return false;
            }
        }
        return true;
    }
}
