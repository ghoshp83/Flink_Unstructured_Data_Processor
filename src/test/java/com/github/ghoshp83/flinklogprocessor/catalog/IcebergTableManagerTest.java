package com.github.ghoshp83.flinklogprocessor.catalog;

import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.CatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.LOG_GENERIC;
import static org.junit.jupiter.api.Assertions.*;

class IcebergTableManagerTest {

    @Test
    void testGetCatalogLoader() {
        Map<String, Properties> propertiesMap = new HashMap<>();
        Properties icebergProperties = new Properties();
        icebergProperties.put("iceberg.warehouse", "s3://test-bucket");
        propertiesMap.put("iceberg.config", icebergProperties);
        
        CatalogLoader catalogLoader = IcebergTableManager.getCatalogLoader(propertiesMap);
        
        assertNotNull(catalogLoader);
    }

    @Test
    void testGetTableIdentifier_Generic() {
        Map<String, Properties> propertiesMap = new HashMap<>();
        Properties icebergProperties = new Properties();
        icebergProperties.put("iceberg.database.name", "test-db");
        icebergProperties.put("iceberg.table.name.default", "test-generic-table");
        propertiesMap.put("iceberg.config", icebergProperties);
        
        TableIdentifier tableIdentifier = IcebergTableManager.getTableIdentifier(propertiesMap, LOG_GENERIC);
        
        assertEquals("test-db", tableIdentifier.namespace().level(0));
        assertEquals("test-generic-table", tableIdentifier.name());
    }

    @Test
    void testGetTableIdentifier_MissingIcebergConfig() {
        Map<String, Properties> propertiesMap = new HashMap<>();
        
        assertThrows(IllegalArgumentException.class, () -> {
            IcebergTableManager.getTableIdentifier(propertiesMap, LOG_GENERIC);
        });
    }

    @Test
    void testGetTableIdentifier_EmptyDatabaseName() {
        Map<String, Properties> propertiesMap = new HashMap<>();
        Properties icebergProperties = new Properties();
        icebergProperties.put("iceberg.database.name", "");
        propertiesMap.put("iceberg.config", icebergProperties);
        
        assertThrows(IllegalArgumentException.class, () -> {
            IcebergTableManager.getTableIdentifier(propertiesMap, LOG_GENERIC);
        });
    }

    @Test
    void testGetTableIdentifier_DefaultTableName() {
        Map<String, Properties> propertiesMap = new HashMap<>();
        Properties icebergProperties = new Properties();
        icebergProperties.put("iceberg.database.name", "test-db");
        propertiesMap.put("iceberg.config", icebergProperties);
        
        TableIdentifier tableIdentifier = IcebergTableManager.getTableIdentifier(propertiesMap, LOG_GENERIC);
        
        assertEquals("test-db", tableIdentifier.namespace().level(0));
        assertEquals("parsed_logs", tableIdentifier.name());
    }
}
