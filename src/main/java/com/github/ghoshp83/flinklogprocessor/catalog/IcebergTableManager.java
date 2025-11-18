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

import java.util.*;

import static com.github.ghoshp83.flinklogprocessor.config.Utils.getTableProperties;
import static com.github.ghoshp83.flinklogprocessor.config.LogConf.*;

@Slf4j
public class IcebergTableManager {
  public static CatalogLoader getCatalogLoader(Map<String, Properties> propertiesMap) {
    Properties icebergProperties = propertiesMap.get("iceberg.config");
    String warehouse = icebergProperties.getProperty("iceberg.warehouse");
    
    Configuration hadoopConf = new Configuration();
    
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
    
    if (isLocal) {
      catalogProperties.put("s3.endpoint", "http://localstack:4566");
      catalogProperties.put("s3.path-style-access", "true");
      catalogProperties.put("glue.endpoint", "http://localstack:4566");
      catalogProperties.put("client.region", "us-east-1");
    }
    
    return CatalogLoader.custom(
        "glue",
        catalogProperties,
        hadoopConf,
        "org.apache.iceberg.aws.glue.GlueCatalog");
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
    TableIdentifier outputTable = getTableIdentifier(propertiesMap, logType);
    List<org.apache.iceberg.types.Types.NestedField> columns = IcebergColumnDefinitions.getIcebergColumns(logType);
    Schema schema = new Schema(columns);
    PartitionSpec partitionSpec = IcebergSchemaManager.getPartitionSpec(schema, logType);
    Map<String, String> tableProperties = getTableProperties(propertiesMap);

    if (!catalog.tableExists(outputTable)) {
      Table icebergTable = catalog.createTable(outputTable, schema, partitionSpec, tableProperties);
      log.info("Table {} created.", icebergTable.name());

    } else {
      log.info(String.valueOf(outputTable));
      Table icebergTableInit = catalog.loadTable(outputTable);
      log.info("Table {} already exists.", icebergTableInit.name());
    }
  }
}
