/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GenericLogRecordTest {

    @Test
    void testGenericLogRecordCreation() {
        GenericLogRecord record = new GenericLogRecord("test log line", "apache");
        
        assertNotNull(record);
        assertEquals("test log line", record.getRawLine());
        assertEquals("apache", record.getLogType());
        assertFalse(record.isValid());
    }

    @Test
    void testSetAndGetFields() {
        GenericLogRecord record = new GenericLogRecord("test", "syslog");
        
        record.setField("timestamp", "2024-01-01T10:00:00Z");
        record.setField("level", "INFO");
        record.setField("message", "Test message");
        record.setField("count", 42);
        
        assertEquals("2024-01-01T10:00:00Z", record.getStringField("timestamp"));
        assertEquals("INFO", record.getStringField("level"));
        assertEquals("Test message", record.getStringField("message"));
        assertEquals(42, record.getIntField("count"));
        assertTrue(record.isValid());
    }

    @Test
    void testGetIntFieldWithString() {
        GenericLogRecord record = new GenericLogRecord("test", "generic");
        
        record.setField("port", "8080");
        assertEquals(8080, record.getIntField("port"));
    }

    @Test
    void testGetIntFieldWithInvalidString() {
        GenericLogRecord record = new GenericLogRecord("test", "generic");
        
        record.setField("invalid", "not-a-number");
        assertEquals(0, record.getIntField("invalid"));
    }

    @Test
    void testGetStringFieldWithNull() {
        GenericLogRecord record = new GenericLogRecord("test", "generic");
        
        assertEquals("", record.getStringField("nonexistent"));
    }

    @Test
    void testGetIntFieldWithNull() {
        GenericLogRecord record = new GenericLogRecord("test", "generic");
        
        assertEquals(0, record.getIntField("nonexistent"));
    }
}
