/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor;

import com.github.ghoshp83.flinklogprocessor.functions.GenericRowDataMapFunction;
import com.github.ghoshp83.flinklogprocessor.model.GenericLogRecord;
import com.github.ghoshp83.flinklogprocessor.parser.GrokPatternParser;
import lombok.SneakyThrows;
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

    @SneakyThrows
    public static void main(String[] args) {
        log.info("Starting Unstructured Data Processor Flink Streaming job");
        StreamExecutionEnvironment sEnv = configureEnvironment();
        
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
        
        try{
            if (s3path == null || !s3path.startsWith("s3://")) {
                throw new IllegalArgumentException("Invalid S3 path: " + s3path);
            }
            logSource = FileSource.forRecordStreamFormat(new TextLineInputFormat(), new Path(s3path))
                    .monitorContinuously(Duration.ofMinutes(window)).build();
        }catch(Exception e){
            log.error("{} error_message=Issue loading log file -- {}", ERROR_MESSAGE_TYPE, e.getMessage());
            throw new RuntimeException("Failed to initialize log source", e);
        }
        defineWorkFlow(sEnv, tableLoader, logSource, logType, logPattern);
        sEnv.execute("Unstructured Data Processor");
    }

public static void defineWorkFlow(StreamExecutionEnvironment sEnv,
                                  TableLoader tableLoader,
                                  FileSource<String> logSource,
                                  String logType,
                                  String logPattern) throws IOException {

        // Read log files from S3
        SingleOutputStreamOperator<String> logStream = sEnv.
                fromSource(logSource, WatermarkStrategy.noWatermarks(), "log-source").
                name("genericLogSource").
                uid("genericLogSource");

        SingleOutputStreamOperator<GenericLogRecord> parsedStream = logStream.process(
                new ProcessFunction<String, GenericLogRecord>() {
                    @Override
                    public void processElement(String logLine, ProcessFunction<String, GenericLogRecord>.Context context,
                                               Collector<GenericLogRecord> collector) throws Exception {
                        GenericLogRecord record = parser.parseGenericRecord(logLine, logType, logPattern);
                        if(record != null){
                            collector.collect(record);
                        }
                    }
                }
        );
        
        DataStream<RowData> rowData = parsedStream.map(new GenericRowDataMapFunction());

        // Write to Iceberg
        FlinkSink.forRowData(rowData)
                .tableLoader(tableLoader)
                .upsert(false)
                .distributionMode(DistributionMode.HASH)
                .append()
                .uid("IcebergGenericLog")
                .name("IcebergGenericLog");
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
