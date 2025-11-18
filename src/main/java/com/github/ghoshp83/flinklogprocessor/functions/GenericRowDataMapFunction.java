/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.functions;

import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.data.StringData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class GenericRowDataMapFunction implements MapFunction<GenericLogRecord, RowData> {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public RowData map(GenericLogRecord record) {
        Map<String, Object> fields = record.getFields();
        int fieldCount = fields.size() + 1; // +1 for partition date
        GenericRowData rowData = new GenericRowData(fieldCount);
        
        int index = 0;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                rowData.setField(index++, StringData.fromString((String) value));
            } else if (value instanceof Integer) {
                rowData.setField(index++, value);
            } else {
                rowData.setField(index++, StringData.fromString(value != null ? value.toString() : ""));
            }
        }
        
        // Add partition date
        rowData.setField(index, StringData.fromString(LocalDate.now().format(DATE_FORMATTER)));
        
        return rowData;
    }
}
