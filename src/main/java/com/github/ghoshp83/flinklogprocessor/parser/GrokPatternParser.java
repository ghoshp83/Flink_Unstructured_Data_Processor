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
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;

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

    public GrokPatternParser() {
        this(new java.util.HashMap<>());
    }
    
    public GrokPatternParser(Map<String, String> genericPatterns) {
        this.genericPatterns = genericPatterns;
        this.genericGrokMap = new java.util.HashMap<>();
    }

    private void ensureGrokInitialized() {
        if (genericGrokMap == null) {
            genericGrokMap = new java.util.HashMap<>();
        }
    }
    
    public GenericLogRecord parseGenericRecord(String line, String logType, String pattern) {
        try {
            // Security: Validate inputs
            if (!isValidInput(line, logType, pattern)) {
                LOG.warn("Invalid input detected for security reasons");
                return null;
            }
            
            ensureGrokInitialized();
            
            Grok grok = genericGrokMap.get(logType);
            if (grok == null) {
                GrokCompiler compiler = GrokCompiler.newInstance();
                compiler.registerDefaultPatterns();
                grok = compiler.compile(pattern);
                genericGrokMap.put(logType, grok);
            }
            
            Map<String, Object> match = grok.match(line).capture();
            if (match.isEmpty()) {
                LOG.warn("Failed to parse generic record for type {}: {}", logType, line);
                return null;
            }
            
            GenericLogRecord record = new GenericLogRecord(line, logType);
            for (Map.Entry<String, Object> entry : match.entrySet()) {
                record.setField(entry.getKey(), entry.getValue());
            }
            
            return record;
        } catch (Exception e) {
            LOG.error("Error parsing generic record for type {}: {}", logType, line, e);
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
