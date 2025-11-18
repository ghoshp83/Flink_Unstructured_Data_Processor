package com.github.ghoshp83.flinklogprocessor;

import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.parser.GrokPatternParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;

import java.time.Duration;

@Slf4j
public class SimpleLocalProcessor {

    public static void main(String[] args) throws Exception {
        log.info("Starting Simple Local Processor");
        
        // Configure Hadoop for S3A
        org.apache.hadoop.conf.Configuration hadoopConf = new org.apache.hadoop.conf.Configuration();
        hadoopConf.set("fs.s3a.endpoint", "http://localstack:4566");
        hadoopConf.set("fs.s3a.access.key", "test");
        hadoopConf.set("fs.s3a.secret.key", "test");
        hadoopConf.set("fs.s3a.path.style.access", "true");
        hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem");
        hadoopConf.set("fs.s3a.connection.ssl.enabled", "false");
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        
        // Register Hadoop configuration
        env.getConfig().setGlobalJobParameters(
            org.apache.flink.api.java.utils.ParameterTool.fromMap(
                new java.util.HashMap<String, String>() {{
                    put("fs.s3a.endpoint", "http://localstack:4566");
                    put("fs.s3a.access.key", "test");
                    put("fs.s3a.secret.key", "test");
                    put("fs.s3a.path.style.access", "true");
                }}
            )
        );
        
        String s3Path = validateS3Path("s3a://flink-logs/");
        String logType = validateLogType("application");
        String logPattern = validateLogPattern("%{TIMESTAMP_ISO8601:timestamp} %{WORD:level} %{GREEDYDATA:message}");
        
        log.info("Reading from: {}", s3Path);
        
        FileSource<String> fileSource = FileSource
            .forRecordStreamFormat(new TextLineInputFormat(), new Path(s3Path))
            .monitorContinuously(Duration.ofMinutes(1))
            .build();
        
        SingleOutputStreamOperator<String> logStream = env
            .fromSource(fileSource, WatermarkStrategy.noWatermarks(), "log-source");
        
        GrokPatternParser parser = new GrokPatternParser();
        SingleOutputStreamOperator<GenericLogRecord> parsedStream = logStream.process(
            new ProcessFunction<String, GenericLogRecord>() {
                @Override
                public void processElement(String logLine, Context context, Collector<GenericLogRecord> collector) {
                    try {
                        if (logLine != null && logLine.length() <= 10000) {
                            GenericLogRecord record = parser.parseGenericRecord(logLine, logType, logPattern);
                            if (record != null && record.isValid()) {
                                collector.collect(record);
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing log line: {}", logLine, e);
                    }
                }
            }
        );
        
        parsedStream.print();
        
        env.execute("Simple Local Processor");
    }
    
    private static String validateS3Path(String s3Path) {
        if (s3Path == null || !s3Path.matches("^s3a?://[a-zA-Z0-9._-]+/.*$")) {
            throw new IllegalArgumentException("Invalid S3 path: " + s3Path);
        }
        return s3Path;
    }
    
    private static String validateLogType(String logType) {
        if (logType == null || !logType.matches("^[a-zA-Z0-9_-]+$") || logType.length() > 50) {
            throw new IllegalArgumentException("Invalid log type: " + logType);
        }
        return logType;
    }
    
    private static String validateLogPattern(String pattern) {
        if (pattern == null || pattern.length() > 1000) {
            throw new IllegalArgumentException("Invalid log pattern length");
        }
        if (!pattern.matches("^[%{}:A-Za-z0-9_\\\\s-]+$")) {
            throw new IllegalArgumentException("Invalid characters in log pattern");
        }
        return pattern;
    }
}
