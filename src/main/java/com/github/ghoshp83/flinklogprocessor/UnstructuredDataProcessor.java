/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor;

import com.github.ghoshp83.flinklogprocessor.functions.GenericRowDataMapFunction;
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.parser.GrokPatternParser;
import com.github.ghoshp83.flinklogprocessor.util.ErrorHandler;
import com.github.ghoshp83.flinklogprocessor.util.RetryUtil;

import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.file.src.FileSource;
import org.apache.flink.connector.file.src.reader.TextLineInputFormat;
import org.apache.flink.core.fs.Path;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static com.github.ghoshp83.flinklogprocessor.config.LogConf.LOG_GENERIC;
import static com.github.ghoshp83.flinklogprocessor.config.Utils.*;
import com.github.ghoshp83.flinklogprocessor.config.LocalConfigLoader;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Collector;
import org.apache.iceberg.DistributionMode;
import org.apache.iceberg.flink.CatalogLoader;
import org.apache.iceberg.flink.TableLoader;
import org.apache.iceberg.flink.sink.FlinkSink;

import static com.github.ghoshp83.flinklogprocessor.catalog.IcebergTableManager.*;


@Slf4j
public class UnstructuredDataProcessor {
    public static final String PROPERTIES_FILE = "application-properties-local.json";
    private static FileSource<String> logSource = null;
    private static final String ERROR_MESSAGE_TYPE = "Log-Data-Processor, error_type=file-error";
    static GrokPatternParser parser = new GrokPatternParser();

    public static void main(String[] args) {
        try {
            log.info("Starting Unstructured Data Processor Flink Streaming job");
            StreamExecutionEnvironment sEnv = ErrorHandler.executeWithErrorHandling(
                () -> configureEnvironment(),
                "configure-environment"
            );
        
        // Check if running locally (detect by environment variable or config file)
        boolean isLocal = System.getenv("FLINK_LOCAL_MODE") != null || 
                         LocalConfigLoader.class.getClassLoader().getResource("application-properties-docker.json") != null;
        
        Map<String, Properties> propertiesMap;
        if (isLocal) {
            log.info("Running in LOCAL mode - loading config from file");
            propertiesMap = com.github.ghoshp83.flinklogprocessor.config.LocalConfigLoader.loadLocalConfig("application-properties-docker.json");
        } else {
            log.info("Running in AWS KDA mode - loading config from runtime");
            propertiesMap = initPropertiesMap(sEnv, PROPERTIES_FILE);
        }
        CatalogLoader catalogLoader = getCatalogLoader(propertiesMap);
        createIcebergTable(catalogLoader.loadCatalog(), propertiesMap, LOG_GENERIC);
        TableLoader tableLoader = TableLoader.fromCatalog(catalogLoader, getTableIdentifier(propertiesMap, LOG_GENERIC));
        Properties jobProperties = propertiesMap.get("job.config");
        String s3path = jobProperties.getProperty("source.s3path");
        long window = Long.parseLong(jobProperties.getProperty("source.window"));
        String logType = validateLogType(jobProperties.getProperty("log.type", "generic"));
        String logPattern = validateLogPattern(jobProperties.getProperty("log.pattern", "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"));
        
            logSource = ErrorHandler.executeWithErrorHandling(
                () -> {
                    if (s3path == null || !s3path.startsWith("s3://")) {
                        throw new RuntimeException("Invalid S3 path: " + s3path);
                    }
                    return FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path(s3path))
                            .monitorContinuously(Duration.ofMinutes(window)).build();
                },
                "create-file-source"
            );
            ErrorHandler.executeWithErrorHandling(
                () -> defineWorkFlow(sEnv, tableLoader, logSource, logType, logPattern),
                "define-workflow"
            );
            
            log.info("Starting Flink job execution...");
            ErrorHandler.executeWithErrorHandling(
                () -> {
                    try {
                        sEnv.execute("Unstructured Data Processor");
                    } catch (Exception e) {
                        throw new RuntimeException("Flink job execution failed", e);
                    }
                },
                "execute-flink-job"
            );
            
        } catch (RuntimeException e) {
            log.error("Application failed with processing error: {}", e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            log.error("Application failed with unexpected error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

public static void defineWorkFlow(StreamExecutionEnvironment sEnv,
                                  TableLoader tableLoader,
                                  FileSource<String> logSource,
                                  String logType,
                                  String logPattern) {
        try {
            if (sEnv == null) {
                throw new RuntimeException("StreamExecutionEnvironment cannot be null");
            }
            if (tableLoader == null) {
                throw new RuntimeException("TableLoader cannot be null");
            }
            if (logSource == null) {
                throw new RuntimeException("LogSource cannot be null");
            }
            
            log.info("Defining workflow for logType: {} with pattern: {}", logType, 
                    logPattern.length() > 50 ? logPattern.substring(0, 50) + "..." : logPattern);

            // Read log files from S3 with error handling
            SingleOutputStreamOperator<String> logStream = ErrorHandler.executeWithErrorHandling(
                () -> sEnv.fromSource(logSource, WatermarkStrategy.noWatermarks(), "log-source")
                         .name("genericLogSource")
                         .uid("genericLogSource"),
                "create-log-stream"
            );

            // Parse logs with comprehensive error handling
            SingleOutputStreamOperator<GenericLogRecord> parsedStream = logStream.process(
                new ProcessFunction<String, GenericLogRecord>() {
                    private transient long processedCount = 0;
                    private transient long errorCount = 0;
                    private transient long lastLogTime = 0;
                    
                    @Override
                    public void processElement(String logLine, ProcessFunction<String, GenericLogRecord>.Context context,
                                               Collector<GenericLogRecord> collector) throws Exception {
                        processedCount++;
                        
                        // Log progress every 10000 records
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastLogTime > 30000) { // Every 30 seconds
                            log.info("Processed {} records, {} errors for logType: {}", 
                                    processedCount, errorCount, logType);
                            lastLogTime = currentTime;
                        }
                        
                        try {
                            if (logLine == null || logLine.trim().isEmpty()) {
                                return; // Skip empty lines
                            }
                            
                            GenericLogRecord record = ErrorHandler.executeWithErrorHandling(
                                () -> parser.parseGenericRecord(logLine, logType, logPattern),
                                "parse-log-record",
                                () -> null, // fallback to null
                                false // don't log every parsing error
                            );
                            
                            if (record != null && record.isValid()) {
                                collector.collect(record);
                            } else {
                                errorCount++;
                            }
                        } catch (Exception e) {
                            errorCount++;
                            if (errorCount % 100 == 0) { // Log every 100th error
                                log.warn("Parsing error #{} for logType {}: {}", errorCount, logType, e.getMessage());
                            }
                        }
                    }
                }
            ).name("parseLogRecords").uid("parseLogRecords");
            
            // Transform to RowData with error handling
            DataStream<RowData> rowData = ErrorHandler.executeWithErrorHandling(
                () -> parsedStream.map(new GenericRowDataMapFunction())
                                 .name("transformToRowData")
                                 .uid("transformToRowData"),
                "create-row-data-stream"
            );

            // Write to Iceberg with error handling
            ErrorHandler.executeWithErrorHandling(
                () -> {
                    FlinkSink.forRowData(rowData)
                            .tableLoader(tableLoader)
                            .upsert(false)
                            .distributionMode(DistributionMode.HASH)
                            .append()
                            .uid("IcebergGenericLog")
                            .name("IcebergGenericLog");
                    return null;
                },
                "create-iceberg-sink"
            );
            
            log.info("Workflow defined successfully for logType: {}", logType);
            
        } catch (Exception e) {
            log.error("Failed to define workflow for logType: {}", logType, e);
            throw new RuntimeException("Failed to define Flink workflow", e);
        }
    }
        
    /**
     * Validates log type to prevent injection attacks
     */
    private static String validateLogType(String logType) {
        if (logType == null || !logType.matches("^[a-zA-Z0-9_-]+$") || logType.length() > 50) {
            throw new IllegalArgumentException("Invalid log type: " + logType);
        }
        return logType;
    }
    
    /**
     * Validates log pattern to prevent injection attacks
     */
    private static String validateLogPattern(String pattern) {
        if (pattern == null || pattern.length() > 1000) {
            throw new IllegalArgumentException("Invalid log pattern length");
        }
        
        // Basic validation for Grok pattern format
        if (!pattern.matches("^[%{}:A-Za-z0-9_\\\\s-]+$")) {
            throw new IllegalArgumentException("Invalid characters in log pattern");
        }
        
        return pattern;
    }
}
