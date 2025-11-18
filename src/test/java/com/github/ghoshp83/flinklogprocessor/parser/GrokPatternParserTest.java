package com.github.ghoshp83.flinklogprocessor.parser;

import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrokPatternParserTest {

    private GrokPatternParser parser;

    @BeforeEach
    void setUp() {
        parser = new GrokPatternParser();
    }



    @Test
    void parseGenericRecord_ApplicationLog_ReturnsCorrectRecord() {
        String appLog = "2024-01-15T10:30:00.123Z INFO Application started successfully";
        String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}";
        
        GenericLogRecord result = parser.parseGenericRecord(appLog, "application", pattern);
        
        assertNotNull(result);
        assertEquals("application", result.getLogType());
        assertEquals("2024-01-15T10:30:00.123Z", result.getField("timestamp"));
        assertEquals("INFO", result.getField("level"));
        assertEquals("Application started successfully", result.getField("message"));
    }

    @Test
    void parseGenericRecord_InvalidInput_ReturnsNull() {
        String invalidLog = "This is not a valid log format";
        String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}";
        
        GenericLogRecord result = parser.parseGenericRecord(invalidLog, "application", pattern);
        
        assertNull(result);
    }

    @Test
    void parseGenericRecord_EmptyLine_ReturnsNull() {
        String emptyLog = "";
        String pattern = "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}";
        
        GenericLogRecord result = parser.parseGenericRecord(emptyLog, "application", pattern);
        
        assertNull(result);
    }
}
