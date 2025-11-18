/*
 * Licensed under the MIT License
 * https://opensource.org/licenses/MIT
 */

package com.github.ghoshp83.flinklogprocessor.metrics;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Meter;
import org.apache.flink.metrics.MeterView;

import java.io.Serializable;

public class LogProcessingMetrics implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private transient Counter recordsProcessed;
    private transient Counter recordsFailed;
    private transient Counter parseErrors;
    private transient Meter recordsPerSecond;
    
    public void incrementProcessed() {
        if (recordsProcessed != null) {
            recordsProcessed.inc();
        }
    }
    
    public void incrementFailed() {
        if (recordsFailed != null) {
            recordsFailed.inc();
        }
    }
    
    public void incrementParseErrors() {
        if (parseErrors != null) {
            parseErrors.inc();
        }
    }
    
    public void registerMetrics(org.apache.flink.metrics.MetricGroup metricGroup) {
        recordsProcessed = metricGroup.counter("records_processed");
        recordsFailed = metricGroup.counter("records_failed");
        parseErrors = metricGroup.counter("parse_errors");
        recordsPerSecond = metricGroup.meter("records_per_second", new MeterView(recordsProcessed, 60));
    }
}
