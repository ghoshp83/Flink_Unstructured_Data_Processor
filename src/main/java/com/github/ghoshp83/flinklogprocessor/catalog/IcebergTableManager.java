/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.catalog;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.flink.CatalogLoader;
import com.github.ghoshp83.flinklogprocessor.util.RetryUtil;
import com.github.ghoshp83.flinklogprocessor.util.CircuitBreaker;


import java.util.*;

import static com.github.ghoshp83.flinklogprocessor.config.Utils.getTableProperties;
import static com.github.ghoshp83.flinklogprocessor.config.LogConf.*;

@Slf4j
public class IcebergTableManager {
  private static final CircuitBreaker catalogCircuitBreaker = new CircuitBreaker(3, 30000); // 3 failures, 30s timeout
  
  public static CatalogLoader getCatalogLoader(Map<String, Properties> propertiesMap) {
    try {
      if (propertiesMap == null || propertiesMap.isEmpty()) {
        throw new RuntimeException("Properties map cannot be null or empty");
      }
      
      Properties icebergProperties = propertiesMap.get("iceberg.config");
      if (icebergProperties == null) {
        throw new RuntimeException("iceberg.config properties not found");
      }
      
      String warehouse = icebergProperties.getProperty("iceberg.warehouse");
      if (warehouse == null || warehouse.trim().isEmpty()) {
        throw new RuntimeException("iceberg.warehouse property is required");
      }
    
    Configuration hadoopConf = new Configuration();
    
    // Performance: Configure connection pooling for S3
    hadoopConf.set("fs.s3a.connection.maximum", "50");
    hadoopConf.set("fs.s3a.threads.max", "20");
    hadoopConf.set("fs.s3a.connection.timeout", "200000");
    hadoopConf.set("fs.s3a.connection.establish.timeout", "5000");
    hadoopConf.set("fs.s3a.attempts.maximum", "3");
    hadoopConf.set("fs.s3a.retry.limit", "3");
    hadoopConf.set("fs.s3a.retry.interval", "500ms");
    
    // Configure for LocalStack if in local mode
    boolean isLocal = System.getenv("FLINK_LOCAL_MODE") != null;
    if (isLocal) {
      log.info("Configuring Iceberg for LocalStack");
      hadoopConf.set("fs.s3a.endpoint", "http://localstack:4566");
      hadoopConf.set("fs.s3a.path.style.access", "true");
      hadoopConf.set("fs.s3a.access.key", "test");
      hadoopConf.set("fs.s3a.secret.key", "test");
      hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
    }
    
    Map<String, String> catalogProperties = new HashMap<>();
    catalogProperties.put("type", "iceberg");
    catalogProperties.put("warehouse", warehouse);
    catalogProperties.put("io-impl", "org.apache.iceberg.aws.s3.S3FileIO");
    
    // Performance: Configure connection pooling for AWS clients
    catalogProperties.put("s3.connection-pool-size", "50");
    catalogProperties.put("s3.connection-timeout-ms", "30000");
    catalogProperties.put("s3.socket-timeout-ms", "30000");
    catalogProperties.put("glue.max-connections", "50");
    catalogProperties.put("glue.connection-timeout-ms", "30000");
    
    if (isLocal) {
      log.info("Using LocalStack endpoints with reduced connection pool");
      catalogProperties.put("s3.endpoint", "http://localstack:4566");
      catalogProperties.put("s3.path-style-access", "true");
      catalogProperties.put("glue.endpoint", "http://localstack:4566");
      catalogProperties.put("client.region", "us-east-1");
      // Reduce pool size for local testing
      catalogProperties.put("s3.connection-pool-size", "10");
      catalogProperties.put("glue.max-connections", "10");
    }
    
      return RetryUtil.executeWithRetry(
        () -> CatalogLoader.custom(
            "glue",
            catalogProperties,
            hadoopConf,
            "org.apache.iceberg.aws.glue.GlueCatalog"),
        "create-catalog-loader"
      );
    } catch (Exception e) {
      log.error("Failed to create catalog loader", e);
      throw new RuntimeException("Failed to create Iceberg catalog loader", e);
    }
  }

  public static TableIdentifier getTableIdentifier(Map<String, Properties> propertiesMap, String logType) {
    Properties icebergProperties = propertiesMap.get("iceberg.config");

    if (icebergProperties == null) {
      throw new IllegalArgumentException("iceberg.config is missing in propertiesMap");
    }

    String databaseName = icebergProperties.getProperty("iceberg.database.name");
    if (databaseName == null || databaseName.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid database name: null or empty");
    }

    String tableName = getIcebergTblPropTblName(logType, icebergProperties);

    if (tableName == null || tableName.trim().isEmpty()) {
      throw new IllegalArgumentException("Invalid table name: null or empty for signalType " + logType);
    }

    return TableIdentifier.of(databaseName, tableName);
  }

  private static String getIcebergTblPropTblName(String logType, Properties icebergProperties) {
    return icebergProperties.getProperty("iceberg.table.name.default", "parsed_logs");
  }

  public static void createIcebergTable(org.apache.iceberg.catalog.Catalog catalog,
                                        Map<String, Properties> propertiesMap,
                                        String logType) {
    try {
      if (catalog == null) {
        throw new RuntimeException("Catalog cannot be null");
      }
      if (propertiesMap == null || propertiesMap.isEmpty()) {
        throw new RuntimeException("Properties map cannot be null or empty");
      }
      if (logType == null || logType.trim().isEmpty()) {
        throw new RuntimeException("Log type cannot be null or empty");
      }
      
      TableIdentifier outputTable = getTableIdentifier(propertiesMap, logType);
      log.info("Creating/verifying Iceberg table: {}", outputTable);
      
      try {
        catalogCircuitBreaker.execute(() -> {
          try {
            List<org.apache.iceberg.types.Types.NestedField> columns = IcebergColumnDefinitions.getIcebergColumns(logType);
            if (columns == null || columns.isEmpty()) {
              throw new RuntimeException("No columns defined for log type: " + logType);
            }
            
            Schema schema = new Schema(columns);
            PartitionSpec partitionSpec = IcebergSchemaManager.getPartitionSpec(schema, logType);
            Map<String, String> tableProperties = getTableProperties(propertiesMap);
            
            if (!catalog.tableExists(outputTable)) {
              log.info("Table {} does not exist, creating...", outputTable);
              Table icebergTable = catalog.createTable(outputTable, schema, partitionSpec, tableProperties);
              log.info("Table {} created successfully", icebergTable.name());
            } else {
              log.info("Table {} already exists, verifying schema...", outputTable);
              Table existingTable = catalog.loadTable(outputTable);
              log.info("Table {} verified successfully", existingTable.name());
            }
            return null;
          } catch (Exception e) {
            log.error("Error in table creation/verification for {}", outputTable, e);
            throw new RuntimeException("Failed to create/verify table: " + outputTable, e);
          }
        }, "create-iceberg-table");
      } catch (RuntimeException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
      
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error creating Iceberg table for log type: {}", logType, e);
      throw new RuntimeException("Failed to create Iceberg table", e);
    }
  }
}
