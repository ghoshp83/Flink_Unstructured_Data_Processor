/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.catalog;

import lombok.extern.slf4j.Slf4j;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.types.Type;
import org.apache.iceberg.types.Types;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.PARTITION_DATE;

@Slf4j
public class IcebergSchemaManager {

  public static PartitionSpec getPartitionSpec(Schema schema, String signalType) {
    log.info("Setting partition for {}", signalType);
    return PartitionSpec.builderFor(schema)
        .identity(PARTITION_DATE)
        .build();
  }

  public static Type convertToIcebergType(String typeName) {
    switch (typeName.toLowerCase()) {
      case "string":
        return Types.StringType.get();
      case "int":
        return Types.IntegerType.get();
      case "long":
        return Types.LongType.get();
      case "boolean":
        return Types.BooleanType.get();
      case "double":
        return Types.DoubleType.get();
      default:
        throw new IllegalArgumentException("Unsupported type: " + typeName);
    }
  }
}
