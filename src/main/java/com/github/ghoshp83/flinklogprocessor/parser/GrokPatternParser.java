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
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;

public class GrokPatternParser implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(GrokPatternParser.class);

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
}
