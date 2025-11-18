package com.github.ghoshp83.flinklogprocessor.catalog;

import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.LOG_GENERIC;
import static org.junit.jupiter.api.Assertions.*;

class IcebergColumnDefinitionsTest {

    @Test
    void testGetIcebergColumns_Generic() {
        List<Types.NestedField> columns = IcebergColumnDefinitions.getIcebergColumns(LOG_GENERIC);
        
        assertEquals(4, columns.size());
        assertEquals("timestamp", columns.get(0).name());
        assertEquals(Types.StringType.get(), columns.get(0).type());
        assertEquals("level", columns.get(1).name());
        assertEquals(Types.StringType.get(), columns.get(1).type());
        assertEquals("message", columns.get(2).name());
        assertEquals(Types.StringType.get(), columns.get(2).type());
        assertEquals("event_date", columns.get(3).name());
        assertEquals(Types.StringType.get(), columns.get(3).type());
    }

    @Test
    void testGetGenericColumns() {
        List<Types.NestedField> columns = IcebergColumnDefinitions.getGenericColumns();
        
        assertEquals(4, columns.size());
        for (int i = 0; i < columns.size(); i++) {
            assertEquals(i, columns.get(i).fieldId());
        }
    }

    @Test
    void testCreateDynamicColumns() {
        Map<String, Object> sampleFields = new HashMap<>();
        sampleFields.put("user_id", "12345");
        sampleFields.put("count", 42);
        sampleFields.put("status", "active");
        
        List<Types.NestedField> columns = IcebergColumnDefinitions.createDynamicColumns(sampleFields);
        
        assertEquals(4, columns.size());
        assertTrue(columns.stream().anyMatch(c -> c.name().equals("event_date")));
    }
}
