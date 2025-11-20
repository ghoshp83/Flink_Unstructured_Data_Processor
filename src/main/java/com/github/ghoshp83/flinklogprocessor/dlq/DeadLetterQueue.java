/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.dlq;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.serialization.SimpleStringEncoder;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy;
import java.io.Serializable;
import java.time.Duration;

@Slf4j
public class DeadLetterQueue implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String dlqPath;
    
    public DeadLetterQueue(String dlqPath) {
        this.dlqPath = dlqPath;
    }
    
    public StreamingFileSink<String> createSink() {
        return StreamingFileSink
            .forRowFormat(new Path(dlqPath), new SimpleStringEncoder<String>("UTF-8"))
            .withRollingPolicy(
                DefaultRollingPolicy.builder()
                    .withRolloverInterval(Duration.ofMinutes(15))
                    .withInactivityInterval(Duration.ofMinutes(5))
                    .withMaxPartSize(128 * 1024 * 1024)
                    .build()
            )
            .build();
    }
    
    public static String formatFailedRecord(String record, String errorType, String errorMessage) {
        return String.format("{\"timestamp\":\"%d\",\"errorType\":\"%s\",\"errorMessage\":\"%s\",\"record\":\"%s\"}",
            System.currentTimeMillis(),
            escapeJson(errorType),
            escapeJson(errorMessage),
            escapeJson(record));
    }
    
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
