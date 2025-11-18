/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GenericLogRecord implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String rawLine;
    private String logType;
    private Map<String, Object> fields;
    
    public GenericLogRecord(String rawLine, String logType) {
        this.rawLine = rawLine;
        this.logType = logType;
        this.fields = new HashMap<>();
    }
    
    public void setField(String key, Object value) {
        fields.put(key, value);
    }
    
    public Object getField(String key) {
        return fields.get(key);
    }
    
    public String getStringField(String key) {
        Object value = fields.get(key);
        return value != null ? value.toString() : "";
    }
    
    public int getIntField(String key) {
        Object value = fields.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    public boolean isValid() {
        return !fields.isEmpty();
    }
    
    public String getRawLine() {
        return rawLine;
    }
    
    public String getLogType() {
        return logType;
    }
    
    public Map<String, Object> getFields() {
        return fields;
    }
    
    @Override
    public String toString() {
        return "GenericLogRecord{" +
                "logType='" + logType + '\'' +
                ", fields=" + fields +
                '}';
    }
}
