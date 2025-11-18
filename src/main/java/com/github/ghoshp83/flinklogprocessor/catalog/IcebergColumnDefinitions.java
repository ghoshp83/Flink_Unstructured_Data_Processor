/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.catalog;

import org.apache.iceberg.types.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.*;

public class IcebergColumnDefinitions {
  public static List<Types.NestedField> getIcebergColumns(String logType) {
    return getGenericColumns();
  }
  
  public static List<Types.NestedField> createDynamicColumns(Map<String, Object> sampleFields) {
    List<Types.NestedField> columns = new ArrayList<>();
    int index = 0;
    for (Map.Entry<String, Object> entry : sampleFields.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Integer) {
        columns.add(Types.NestedField.required(index++, entry.getKey(), Types.IntegerType.get()));
      } else {
        columns.add(Types.NestedField.required(index++, entry.getKey(), Types.StringType.get()));
      }
    }
    columns.add(Types.NestedField.required(index, PARTITION_DATE, Types.StringType.get()));
    return columns;
  }
  
  public static List<Types.NestedField> getGenericColumns() {
    List<Types.NestedField> columns = new ArrayList<>();
    columns.add(Types.NestedField.required(0, "timestamp", Types.StringType.get()));
    columns.add(Types.NestedField.required(1, "level", Types.StringType.get()));
    columns.add(Types.NestedField.required(2, "message", Types.StringType.get()));
    columns.add(Types.NestedField.required(3, PARTITION_DATE, Types.StringType.get()));
    return columns;
  }


}
