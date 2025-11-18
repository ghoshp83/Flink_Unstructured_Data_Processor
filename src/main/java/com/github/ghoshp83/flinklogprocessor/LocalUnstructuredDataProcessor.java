/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor;

import com.github.ghoshp83.flinklogprocessor.functions.GenericRowDataMapFunction;
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.parser.GrokPatternParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Collector;

import java.time.Duration;

@Slf4j
public class LocalUnstructuredDataProcessor {

    public static void main(String[] args) throws Exception {
        log.info("Starting Local Unstructured Data Processor");
        
        // Configuration for local testing
        String s3Endpoint = "http://localstack:4566";
        String s3Path = "s3a://flink-logs/";
        String logType = "application";
        String logPattern = "%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}";
        
        // Create Flink environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        // Configure S3 for LocalStack
        env.getConfig().setGlobalJobParameters(
            org.apache.flink.api.java.utils.ParameterTool.fromArgs(new String[]{
                "--s3.endpoint", s3Endpoint,
                "--s3.path-style-access", "true"
            })
        );
        
        log.info("Configured S3 endpoint: {}", s3Endpoint);
        log.info("Reading from: {}", s3Path);
        log.info("Log type: {}, Pattern: {}", logType, logPattern);
        
        // Create file source
        FileSource<String> fileSource = FileSource
            .forRecordStreamFormat(new TextLineInputFormat(), new Path(s3Path))
            .monitorContinuously(Duration.ofMinutes(1))
            .build();
        
        // Read log files
        SingleOutputStreamOperator<String> logStream = env
            .fromSource(fileSource, WatermarkStrategy.noWatermarks(), "log-source")
            .name("LocalLogSource")
            .uid("LocalLogSource");
        
        // Parse logs
        GrokPatternParser parser = new GrokPatternParser();
        SingleOutputStreamOperator<GenericLogRecord> parsedStream = logStream.process(
            new ProcessFunction<String, GenericLogRecord>() {
                @Override
                public void processElement(String logLine, Context context, Collector<GenericLogRecord> collector) {
                    try {
                        GenericLogRecord record = parser.parseGenericRecord(logLine, logType, logPattern);
                        if (record != null && record.isValid()) {
                            collector.collect(record);
                            log.debug("Parsed record: {}", record);
                        } else {
                            log.warn("Failed to parse line: {}", logLine);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing line: {}", logLine, e);
                    }
                }
            }
        ).name("ParseLogs").uid("ParseLogs");
        
        // Transform to RowData
        DataStream<RowData> rowData = parsedStream
            .map(new GenericRowDataMapFunction())
            .name("TransformToRowData")
            .uid("TransformToRowData");
        
        // Print results (for local testing)
        rowData.print().name("PrintResults");
        
        // Execute
        log.info("Starting Flink job execution...");
        env.execute("Local Unstructured Data Processor");
    }
}
