package com.github.ghoshp83.flinklogprocessor.integration;

import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.parser.GrokPatternParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenericLogProcessorIntegrationTest {

    private GrokPatternParser parser;

    @BeforeEach
    void setUp() {
        parser = new GrokPatternParser();
    }

    @Test
    void testGenericLogParsing() {
        String logLine = "2024-01-15T10:30:00.123Z INFO Application started successfully";
        String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}";
        GenericLogRecord record = parser.parseGenericRecord(logLine, "application", pattern);
        
        assertNotNull(record);
        assertEquals("application", record.getLogType());
        assertEquals("2024-01-15T10:30:00.123Z", record.getField("timestamp"));
        assertEquals("INFO", record.getField("level"));
        assertEquals("Application started successfully", record.getField("message"));
    }

    @Test
    void testInvalidLogParsing() {
        String logLine = "Invalid log format";
        String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}";
        GenericLogRecord record = parser.parseGenericRecord(logLine, "application", pattern);
        
        assertNull(record);
    }
}
